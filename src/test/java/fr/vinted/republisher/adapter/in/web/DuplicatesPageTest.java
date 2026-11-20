package fr.vinted.republisher.adapter.in.web;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-47: the static page of the duplicates in the installed Chrome, against a simulated API answering as the contract
 * says: never the real application nor Vinted.
 */
@Tag("browser")
class DuplicatesPageTest {

    private static final String ONE_GROUP = """
            {"deletionDryRun": false, "groups": [{"title": "Robe <b>bleue</b>",
              "price": {"amount": 12, "currency": "EUR"},
              "listings": [
                {"listingId": 1, "url": "https://vinted.test/items/1", "firstSeenAt": "2026-11-18T10:05:00Z",
                 "backedUp": true, "deletable": false},
                {"listingId": 2, "url": "https://vinted.test/items/2", "firstSeenAt": "2026-11-18T10:06:00Z",
                 "backedUp": false, "deletable": true}]}]}""";
    private static final String NO_GROUP = """
            {"deletionDryRun": false, "groups": []}""";

    private static Playwright playwright;
    private static Browser chrome;

    private HttpServer fakeApi;
    private final AtomicReference<String> duplicates = new AtomicReference<>(ONE_GROUP);
    private final AtomicInteger deletionStatus = new AtomicInteger(200);
    private final AtomicReference<String> deletionAnswer =
            new AtomicReference<>("{\"outcome\": \"DELETED\", \"twinListingId\": 1}");
    private final List<String> deletions = new CopyOnWriteArrayList<>();
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
        fakeApi.createContext("/api/duplicates", exchange -> {
            String body = duplicates.get();
            if (body == null) {
                respond(exchange, 500, "text/plain", new byte[0]);
            } else {
                respond(exchange, 200, "application/json", body.getBytes(StandardCharsets.UTF_8));
            }
        });
        fakeApi.createContext("/api/listings/", exchange -> {
            deletions.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            if (deletionStatus.get() == 200) {
                duplicates.set(NO_GROUP);
            }
            respond(exchange, deletionStatus.get(), "application/json",
                    deletionAnswer.get().getBytes(StandardCharsets.UTF_8));
        });
        fakeApi.start();
        page = chrome.newPage(new Browser.NewPageOptions().setTimezoneId("Europe/Paris"));
    }

    @AfterEach
    void stopFakeApi() {
        page.close();
        fakeApi.stop(0);
    }

    // AT-47-01
    @Test
    void shows_the_groups_of_duplicates() {
        open();

        assertThat(page.locator("#duplicates-summary")).hasText("1 groupe de doublons.");
        assertThat(page.locator(".duplicate-group h2")).hasText("Robe <b>bleue</b> · 12,00 €");
        assertThat(page.locator(".duplicate-group h2 b")).hasCount(0);
        assertThat(rows()).hasCount(2);
        assertThat(rows().nth(0)).containsText("Sauvegardée");
        assertThat(rows().nth(0)).containsText("18/11/2026 11:05:00");
        assertThat(rows().nth(0).locator("a").first()).hasAttribute("href", "#/listings/1");
    }

    // AT-47-02
    @Test
    void listing_with_history_cannot_be_deleted() {
        open();

        assertThat(rows().nth(0)).containsText("Historique à garder");
        assertThat(rows().nth(0).locator("button")).hasCount(0);
        assertThat(rows().nth(1).locator("button")).hasText("Supprimer ce doublon");
    }

    // AT-47-14
    @Test
    void deletes_a_duplicate_after_confirmation() {
        open();

        rows().nth(1).locator("button").click();
        assertThat(page.locator("#duplicate-summary")).hasText("Supprimer de Vinted l'annonce 2 et l'effacer de "
                + "l'outil (sauvegarde, photos, relevés, tags). L'annonce 1 reste en vente. Irréversible.");
        page.locator("#confirm-duplicate-deletion").click();

        assertThat(page.locator("#duplicate-message"))
                .hasText("Doublon 2 supprimé de Vinted et effacé de l'outil ; l'annonce 1 reste en vente.");
        assertThat(page.locator("#duplicates-summary")).hasText("Aucun doublon.");
        assertThat(deletions).containsExactly("POST /api/listings/2/duplicate-deletion");
    }

    @Test
    void cancelling_deletes_nothing() {
        open();

        rows().nth(1).locator("button").click();
        page.locator("#cancel-duplicate-deletion").click();

        assertThat(page.locator("#duplicate-confirmation")).isHidden();
        assertThat(deletions).isEmpty();
    }

    @Test
    void a_refusal_says_why() {
        deletionStatus.set(409);
        deletionAnswer.set("{\"reason\": \"BROWSER_BUSY\"}");
        open();

        rows().nth(1).locator("button").click();
        page.locator("#confirm-duplicate-deletion").click();

        assertThat(page.locator("#duplicate-message"))
                .hasText("Doublon non supprimé : une autre action est en cours sur cette annonce.");
        assertThat(rows()).hasCount(2);
    }

    @Test
    void says_when_there_is_no_duplicate_or_the_page_is_unavailable() {
        duplicates.set(NO_GROUP);
        open();
        assertThat(page.locator("#duplicates-summary")).hasText("Aucun doublon.");

        duplicates.set(null);
        open();
        assertThat(page.locator("#duplicates-problem")).hasText("Doublons indisponibles.");
    }

    private void open() {
        BuiltFrontend.open(page, fakeApi, "duplicates",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
    }

    private Locator rows() {
        return page.locator(".duplicate-group tbody tr");
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
