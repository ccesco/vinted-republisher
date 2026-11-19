package fr.vinted.republisher.adapter.in.web;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitUntilState;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-39: the failures page in the installed Chrome, against a simulated API answering as the contract says: never the
 * real application nor Vinted.
 */
@Tag("browser")
class FailedListingsPageTest {

    private static final String PULL = """
            {"listingId": 3, "listingTitle": "Pull <b>bleu</b>", "stage": "TO_DELETE",
             "failure": {"entryId": 2, "occurredAt": "2026-11-18T10:00:00Z", "action": "DELETION_RECHECK",
                         "outcome": "NOT_DONE", "listingId": 3, "listingTitle": "Pull <b>bleu</b>"}}""";
    private static final String DRESS = """
            {"listingId": 4, "listingTitle": "Robe", "stage": "TO_BACK_UP",
             "failure": {"entryId": 1, "occurredAt": "2026-11-17T10:00:00Z", "action": "BACKUP_VALIDATION",
                         "outcome": "REFUSED", "listingId": 4, "listingTitle": "Robe",
                         "reason": "BACKUP_REPLACED"}}""";
    private static final String DELETED = """
            {"action": "DELETION", "dryRun": false,
             "listing": {"vintedId": 3, "title": "Pull <b>bleu</b>", "price": {"amount": 10, "currency": "EUR"},
                         "url": "https://vinted.test/items/3", "present": false, "stage": "WAITING"}}""";

    private static Playwright playwright;
    private static Browser chrome;

    private HttpServer fakeApi;
    private final AtomicReference<String> failedListings =
            new AtomicReference<>("{\"listings\": [" + PULL + ", " + DRESS + "]}");
    private final AtomicBoolean applicationDown = new AtomicBoolean();
    private final AtomicBoolean relaunchRefused = new AtomicBoolean();
    private final List<String> relaunches = new CopyOnWriteArrayList<>();
    private Page page;

    @BeforeAll
    static void startChrome() {
        playwright = Playwright.create();
        chrome = playwright.chromium().launch(new BrowserType.LaunchOptions().setChannel("chrome"));
    }

    @AfterAll
    static void stopChrome() {
        chrome.close();
        playwright.close();
    }

    @BeforeEach
    void startFakeApi() throws IOException {
        fakeApi = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        BuiltFrontend.serveFrom(fakeApi);
        fakeApi.createContext("/api/failed-listings", exchange -> {
            if (applicationDown.get()) {
                respond(exchange, 500, "text/plain", new byte[0]);
            } else {
                respond(exchange, 200, "application/json", failedListings.get().getBytes(StandardCharsets.UTF_8));
            }
        });
        fakeApi.createContext("/api/listings/3/relaunch", exchange -> {
            relaunches.add(exchange.getRequestMethod() + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (relaunchRefused.get()) {
                respond(exchange, 409, "application/json",
                        "{\"reason\": \"ACTION_REFUSED\"}".getBytes(StandardCharsets.UTF_8));
            } else {
                failedListings.set("{\"listings\": [" + DRESS + "]}");
                respond(exchange, 200, "application/json", DELETED.getBytes(StandardCharsets.UTF_8));
            }
        });
        fakeApi.start();
        page = chrome.newPage(new Browser.NewPageOptions().setTimezoneId("Europe/Paris"));
    }

    @AfterEach
    void stopFakeApi() {
        page.close();
        fakeApi.stop(0);
    }

    @Test
    void shows_failures_with_cause_and_relaunches() {
        // AT-39-06. Étant donné une annonce « Pull bleu » prête à supprimer, dont la revérification a conclu
        //   « non faite » le 18/11/2026
        // Et une annonce « Robe » à sauvegarder, dont la validation a été refusée (sauvegarde remplacée)
        // Quand le membre ouvre la page « Échecs »
        open();
        // Alors il voit « Pull bleu », lien vers sa fiche, « Prête à supprimer », la cause « suppression non faite »
        //   et la date
        assertThat(rows()).hasCount(2);
        assertThat(rows().nth(0).getByRole(AriaRole.LINK)).hasText("Pull <b>bleu</b>");
        assertThat(rows().nth(0).getByRole(AriaRole.LINK)).hasAttribute("href", "#/listings/3");
        assertThat(rows().nth(0)).containsText("Prête à supprimer");
        assertThat(rows().nth(0)).containsText("Revérification");
        assertThat(rows().nth(0).locator("strong")).hasText("non faite");
        assertThat(rows().nth(0)).containsText("suppression non faite");
        assertThat(rows().nth(0)).containsText("18/11/2026 11:00:00");
        assertThat(rows().nth(1)).containsText("À sauvegarder");
        assertThat(rows().nth(1)).containsText("sauvegarde remplacée");
        // Et « Pull bleu » a un bouton « Supprimer de Vinted » (action de la relance), « Robe » n'en a pas
        assertThat(rows().nth(0).getByRole(AriaRole.BUTTON)).hasText("Supprimer de Vinted");
        assertThat(rows().nth(1).getByRole(AriaRole.BUTTON)).hasCount(0);
        // Quand il relance « Pull bleu » et que la suppression réussit
        rows().nth(0).getByRole(AriaRole.BUTTON).click();
        // Alors la page relit la liste, qui ne contient plus « Pull bleu »
        assertThat(page.locator("#relaunch-message")).hasText("Pull <b>bleu</b> : Suppression faite");
        assertThat(rows()).hasCount(1);
        assertThat(rows().nth(0)).containsText("Robe");
        assertThat(relaunches).containsExactly("POST {\"stage\":\"TO_DELETE\"}");
    }

    @Test
    void refused_relaunch_keeps_the_row_and_says_why() {
        // Relance refusée depuis la page : message de refus comme dans la liste ; la ligne reste
        relaunchRefused.set(true);
        open();
        rows().nth(0).getByRole(AriaRole.BUTTON).click();
        assertThat(page.locator("#relaunch-message"))
                .hasText("Pull <b>bleu</b> : Relance refusée : voir la fiche fiche de l'annonce");
        assertThat(page.locator("#relaunch-message").getByRole(AriaRole.LINK)).hasAttribute("href", "#/listings/3");
        assertThat(rows()).hasCount(2);
        assertThat(rows().nth(0).getByRole(AriaRole.BUTTON)).isEnabled();
    }

    @Test
    void says_when_there_is_no_failed_listing() {
        // Et sans annonce en échec, la page affiche « Aucune annonce en échec. »
        failedListings.set("{\"listings\": []}");
        open();
        assertThat(page.locator("#no-failure")).hasText("Aucune annonce en échec.");
        assertThat(rows()).hasCount(0);
    }

    @Test
    void says_when_failed_listings_are_unavailable() {
        applicationDown.set(true);
        open();
        assertThat(page.getByRole(AriaRole.ALERT)).hasText("Annonces en échec indisponibles.");
        assertThat(page.locator("#no-failure")).hasCount(0);
    }

    @Test
    void navigation_leads_to_failures_page() {
        BuiltFrontend.open(page, fakeApi, "journal",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Échecs")).click();
        assertThat(rows()).hasCount(2);
    }

    private void open() {
        BuiltFrontend.open(page, fakeApi, "failures",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
    }

    private Locator rows() {
        return page.locator("#failed-listings tbody tr");
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }
}
