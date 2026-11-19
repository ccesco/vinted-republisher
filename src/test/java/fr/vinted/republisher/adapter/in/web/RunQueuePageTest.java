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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-41: the run queue page in the installed Chrome, against a simulated API answering as the contract says: never
 * the real application nor Vinted.
 */
@Tag("browser")
class RunQueuePageTest {

    private static final String QUEUE = """
            {"computedAt": "2026-11-19T10:00:00Z", "actions": [
              {"listingId": 3, "listingTitle": "Pull <b>bleu</b>", "action": "DELETION",
               "earliestAt": "2026-11-19T10:00:00Z", "dryRun": true},
              {"listingId": 4, "listingTitle": "Robe", "action": "REPUBLICATION",
               "earliestAt": "2026-11-19T10:05:00Z", "dryRun": false}]}""";

    private static Playwright playwright;
    private static Browser chrome;

    private HttpServer fakeApi;
    private final AtomicReference<String> queue = new AtomicReference<>(QUEUE);
    private final AtomicBoolean applicationDown = new AtomicBoolean();
    private final AtomicInteger reads = new AtomicInteger();
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
        fakeApi.createContext("/api/automatic-run/queue", exchange -> {
            reads.incrementAndGet();
            if (applicationDown.get()) {
                respond(exchange, 500, "text/plain", new byte[0]);
            } else {
                respond(exchange, 200, "application/json", queue.get().getBytes(StandardCharsets.UTF_8));
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
    void shows_the_queued_actions() {
        // AT-41-07. Étant donné une file avec une suppression maintenant en dry-run et une republication dans 5 minutes
        // Quand le membre ouvre « File d'attente »
        open();
        // Alors il voit deux lignes numérotées, liens vers les fiches, « maintenant » et l'heure, et la mention dry-run
        assertThat(rows()).hasCount(2);
        assertThat(rows().nth(0).locator("td").nth(0)).hasText("1");
        assertThat(rows().nth(0).getByRole(AriaRole.LINK)).hasText("Pull <b>bleu</b>");
        assertThat(rows().nth(0).getByRole(AriaRole.LINK)).hasAttribute("href", "#/listings/3");
        assertThat(rows().nth(0)).containsText("Suppression");
        assertThat(rows().nth(0)).containsText("maintenant");
        assertThat(rows().nth(0)).containsText("dry-run");
        assertThat(rows().nth(1).locator("td").nth(0)).hasText("2");
        assertThat(rows().nth(1)).containsText("Republication");
        assertThat(rows().nth(1)).containsText("19/11/2026 11:05:00");
        assertThat(rows().nth(1)).not().containsText("dry-run");
        assertThat(page.locator("#queue-note")).containsText("au plus tôt");
    }

    @Test
    void refresh_reads_the_queue_again() {
        open();
        queue.set("{\"computedAt\": \"2026-11-19T10:01:00Z\", \"actions\": []}");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Actualiser")).click();
        assertThat(page.locator("#no-queued-action")).hasText("Aucune action en attente pour la série.");
        assertThat(reads.get()).isEqualTo(2);
    }

    @Test
    void says_when_the_queue_is_empty() {
        queue.set("{\"computedAt\": \"2026-11-19T10:00:00Z\", \"actions\": []}");
        open();
        assertThat(page.locator("#no-queued-action")).hasText("Aucune action en attente pour la série.");
        assertThat(rows()).hasCount(0);
    }

    @Test
    void says_when_the_queue_is_unavailable() {
        applicationDown.set(true);
        open();
        assertThat(page.getByRole(AriaRole.ALERT)).hasText("File d'attente indisponible.");
        assertThat(page.locator("#no-queued-action")).hasCount(0);
    }

    @Test
    void navigation_leads_to_the_queue_page() {
        BuiltFrontend.open(page, fakeApi, "journal",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("File d'attente")).click();
        assertThat(rows()).hasCount(2);
    }

    private void open() {
        BuiltFrontend.open(page, fakeApi, "queue",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
    }

    private Locator rows() {
        return page.locator("#run-queue tbody tr");
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
