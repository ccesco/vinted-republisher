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
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The static journal page in the installed Chrome, against a simulated API answering as the contract says: never the
 * real application nor Vinted.
 */
@Tag("browser")
class ActionJournalPageTest {

    private static final String FIRST_PAGE = """
            {"entries": [
               {"entryId": 2, "occurredAt": "2026-11-15T10:01:00Z", "action": "DELETION", "outcome": "REFUSED",
                "listingId": 3, "listingTitle": "Pull <b>rayé</b>", "reason": "NOT_VALIDATED"},
               {"entryId": 1, "occurredAt": "2026-11-15T10:00:00Z", "action": "SCAN", "outcome": "SUCCEEDED"}],
             "hasMore": true}""";
    private static final String OLDER_PAGE = """
            {"entries": [
               {"entryId": 0, "occurredAt": "2026-11-14T10:00:00Z", "action": "WARDROBE_BACKUP",
                "outcome": "FAILED", "reason": "SESSION_DISCONNECTED"}],
             "hasMore": false}""";

    private static Playwright playwright;
    private static Browser chrome;

    private HttpServer fakeApi;
    private final AtomicBoolean applicationDown = new AtomicBoolean();
    private final List<String> queries = new CopyOnWriteArrayList<>();
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
        for (String file : List.of("journal.html", "journal.js")) {
            fakeApi.createContext("/" + file, exchange -> {
                try (InputStream content = getClass().getResourceAsStream("/static/" + file)) {
                    respond(exchange, 200, file.endsWith(".js") ? "text/javascript; charset=utf-8"
                            : "text/html; charset=utf-8", content.readAllBytes());
                }
            });
        }
        fakeApi.createContext("/api/journal", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            queries.add(query == null ? "" : query);
            if (applicationDown.get()) {
                respond(exchange, 500, "text/plain", new byte[0]);
            } else {
                respond(exchange, 200, "application/json",
                        (query == null ? FIRST_PAGE : OLDER_PAGE).getBytes(StandardCharsets.UTF_8));
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
    void shows_entries_and_loads_older_ones() {
        // US-23 AT-23-13. Étant donné un scan terminé et une suppression refusée de « Pull rayé »
        // Quand le membre ouvre journal.html
        open();
        // Alors il voit la suppression refusée en premier, mise en évidence, avec la raison en clair
        //   et le lien vers la fiche de « Pull rayé »
        assertThat(rows()).hasCount(2);
        assertThat(rows().nth(0)).containsText("15/11/2026 11:01:00");
        assertThat(rows().nth(0)).containsText("Suppression");
        assertThat(rows().nth(0).locator("strong")).hasText("refusée");
        assertThat(rows().nth(0)).containsText("sauvegarde non validée");
        assertThat(rows().nth(0).getByRole(AriaRole.LINK)).hasText("Pull <b>rayé</b>");
        assertThat(rows().nth(0).getByRole(AriaRole.LINK)).hasAttribute("href", "listing.html?id=3");
        // Et le scan terminé, sans annonce, sans mise en évidence
        assertThat(rows().nth(1)).containsText("Scan du dressing");
        assertThat(rows().nth(1)).containsText("réussie");
        assertThat(rows().nth(1).locator("strong, a")).hasCount(0);
        // Et « Plus ancien » charge la page suivante quand il y en a une
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Plus ancien")).click();
        assertThat(rows()).hasCount(3);
        assertThat(rows().nth(2)).containsText("session non connectée");
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Plus ancien"))).isHidden();
        assertThat(queries).containsExactly("", "before=1");
    }

    @Test
    void says_when_the_journal_is_unavailable() {
        applicationDown.set(true);
        open();
        assertThat(page.getByRole(AriaRole.ALERT)).hasText("Journal indisponible.");
        assertThat(rows()).hasCount(0);
    }

    private void open() {
        page.navigate("http://127.0.0.1:" + fakeApi.getAddress().getPort() + "/journal.html",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
    }

    private Locator rows() {
        return page.locator("#journal tbody tr");
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
