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
            {"listingId": 3, "listingTitle": "Pull <b>bleu</b>", "stage": "TO_DELETE", "kind": "OTHER",
             "failure": {"entryId": 2, "occurredAt": "2026-11-18T10:00:00Z", "action": "DELETION_RECHECK",
                         "outcome": "NOT_DONE", "listingId": 3, "listingTitle": "Pull <b>bleu</b>"}}""";
    private static final String DRESS = """
            {"listingId": 4, "listingTitle": "Robe", "stage": "TO_BACK_UP", "kind": "REFUSED",
             "failure": {"entryId": 1, "occurredAt": "2026-11-17T10:00:00Z", "action": "BACKUP_VALIDATION",
                         "outcome": "REFUSED", "listingId": 4, "listingTitle": "Robe",
                         "reason": "BACKUP_REPLACED"}}""";
    private static final String SWEATER_IN_DOUBT = """
            {"listingId": 5, "listingTitle": "Pull bleu", "stage": "REPUBLICATION_TO_CHECK", "kind": "IN_DOUBT",
             "failure": {"entryId": 3, "occurredAt": "2026-11-18T11:00:00Z", "action": "REPUBLICATION",
                         "outcome": "IN_DOUBT", "listingId": 5, "listingTitle": "Pull bleu"}}""";
    private static final String SKIRT_REFUSED = """
            {"listingId": 6, "listingTitle": "Robe", "stage": "TO_REPUBLISH", "kind": "REFUSED",
             "failure": {"entryId": 4, "occurredAt": "2026-11-18T12:00:00Z", "action": "REPUBLICATION",
                         "outcome": "REFUSED", "listingId": 6, "listingTitle": "Robe",
                         "reason": "FIELD_NOT_FILLABLE"}}""";
    private static final String SKIRT_BACKUP = """
            {"vintedId": 6, "savedAt": "2026-11-10T10:00:00Z"}""";
    private static final String SKIRT_PREPARED = """
            {"listing": {"vintedId": 6, "savedAt": "2026-11-10T10:00:00Z"},
             "unfilledFields": [{"field": "SIZE", "optionId": 588}]}""";
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
    private final List<String> manualRepublications = new CopyOnWriteArrayList<>();
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
        fakeApi.createContext("/api/listings/6/backup", exchange ->
                respond(exchange, 200, "application/json", SKIRT_BACKUP.getBytes(StandardCharsets.UTF_8)));
        fakeApi.createContext("/api/listings/6/republication/manual", exchange -> {
            manualRepublications.add(exchange.getRequestMethod() + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            failedListings.set("{\"listings\": [" + SWEATER_IN_DOUBT + "]}");
            respond(exchange, 200, "application/json", SKIRT_PREPARED.getBytes(StandardCharsets.UTF_8));
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
        assertThat(pull().getByRole(AriaRole.LINK)).hasText("Pull <b>bleu</b>");
        assertThat(pull().getByRole(AriaRole.LINK)).hasAttribute("href", "#/listings/3");
        assertThat(pull()).containsText("Prête à supprimer");
        assertThat(pull()).containsText("Revérification");
        assertThat(pull().locator("strong")).hasText("non faite");
        assertThat(pull()).containsText("suppression non faite");
        assertThat(pull()).containsText("18/11/2026 11:00:00");
        assertThat(dress()).containsText("À sauvegarder");
        assertThat(dress()).containsText("sauvegarde remplacée");
        // Et « Pull bleu » a un bouton « Supprimer de Vinted » (action de la relance), « Robe » n'en a pas
        assertThat(pull().getByRole(AriaRole.BUTTON)).hasText("Supprimer de Vinted");
        assertThat(dress().getByRole(AriaRole.BUTTON)).hasCount(0);
        // Quand il relance « Pull bleu » et que la suppression réussit
        pull().getByRole(AriaRole.BUTTON).click();
        // Alors la page relit la liste, qui ne contient plus « Pull bleu »
        assertThat(page.locator("#relaunch-message")).hasText("Pull <b>bleu</b> : Suppression faite");
        assertThat(rows()).hasCount(1);
        assertThat(dress()).containsText("Robe");
        assertThat(relaunches).containsExactly("POST {\"stage\":\"TO_DELETE\"}");
    }

    @Test
    void refused_relaunch_keeps_the_row_and_says_why() {
        // Relance refusée depuis la page : message de refus comme dans la liste ; la ligne reste
        relaunchRefused.set(true);
        open();
        pull().getByRole(AriaRole.BUTTON).click();
        assertThat(page.locator("#relaunch-message"))
                .hasText("Pull <b>bleu</b> : Relance refusée : voir la fiche fiche de l'annonce");
        assertThat(page.locator("#relaunch-message").getByRole(AriaRole.LINK)).hasAttribute("href", "#/listings/3");
        assertThat(rows()).hasCount(2);
        assertThat(pull().getByRole(AriaRole.BUTTON)).isEnabled();
    }

    @Test
    void says_when_there_is_no_failed_listing() {
        // US-45 : sans annonce en échec, les trois sections restent affichées, vides
        failedListings.set("{\"listings\": []}");
        open();
        assertThat(page.locator(".failure-group h2")).hasText(new String[]{"Dans le doute (0)", "Refusées (0)",
                "Autres échecs (0)"});
        assertThat(page.locator(".failure-group .no-failure")).hasText(new String[]{"Aucune annonce.",
                "Aucune annonce.", "Aucune annonce."});
        assertThat(rows()).hasCount(0);
    }

    @Test
    void groups_failures_and_offers_manual_republication() {
        // AT-45-02. Étant donné une annonce « Pull bleu » dont la republication est en doute
        // Et une annonce « Robe » à republier, dont la republication a été refusée (taille impossible à remplir)
        failedListings.set("{\"listings\": [" + SKIRT_REFUSED + ", " + SWEATER_IN_DOUBT + "]}");
        // Quand le membre ouvre la page « Échecs »
        open();
        // Alors « Pull bleu » est dans « Dans le doute » avec « Relancer »
        assertThat(group("in-doubt").locator("h2")).hasText("Dans le doute (1)");
        assertThat(group("in-doubt").locator("tbody tr")).containsText("Pull bleu");
        assertThat(group("in-doubt").getByRole(AriaRole.BUTTON)).hasText("Vérifier la republication");
        // Et « Robe » est dans « Refusées » avec « Republier sur Vinted » et « Republier manuellement »
        assertThat(group("refused").locator("tbody tr")).containsText("Robe");
        assertThat(group("refused").getByRole(AriaRole.BUTTON)).hasText(new String[]{"Republier sur Vinted",
                "Republier manuellement"});
        // Et la section « Autres échecs » est affichée avec « Aucune annonce. »
        assertThat(group("other").locator(".no-failure")).hasText("Aucune annonce.");

        // Quand il la republie manuellement
        group("refused").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Republier manuellement"))
                .click();
        // Alors le formulaire est préparé pour la sauvegarde vue, et la page dit quoi compléter
        assertThat(page.locator("#relaunch-message")).hasText(
                "Robe : Formulaire rempli dans Chrome, à compléter : Taille (588). Publiez puis validez sur la fiche.");
        assertThat(manualRepublications).containsExactly("POST {\"savedAt\":\"2026-11-10T10:00:00Z\"}");
        assertThat(group("refused").locator("tbody tr")).hasCount(0);
    }

    @Test
    void offers_manual_republication_while_waiting() {
        // AT-45-04d. Étant donné une annonce supprimée en attente de sa date dont la republication a été refusée
        failedListings.set("{\"listings\": [" + SKIRT_REFUSED.replace("TO_REPUBLISH", "WAITING") + "]}");
        open();
        // Alors elle propose « Republier manuellement »
        assertThat(group("refused").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName("Republier manuellement"))).isVisible();
    }

    private Locator group(String kind) {
        return page.locator("#failures-" + kind);
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

    /** « Pull bleu », revérification non faite : dans « Autres échecs ». */
    private Locator pull() {
        return group("other").locator("tbody tr");
    }

    /** « Robe », validation refusée : dans « Refusées ». */
    private Locator dress() {
        return group("refused").locator("tbody tr");
    }

    private Locator rows() {
        return page.locator(".failure-group tbody tr");
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
