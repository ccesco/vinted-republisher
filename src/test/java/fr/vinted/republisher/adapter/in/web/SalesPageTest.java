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
import java.util.concurrent.atomic.AtomicReference;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * US-36 AT-36-07: the static sales page in the installed Chrome, against a simulated API answering as the contract
 * says: never the real application nor Vinted.
 */
@Tag("browser")
class SalesPageTest {

    private static final String TWO_SALES = """
            {"sales": [
               {"listingId": 2, "title": "Jean <b>bleu</b>", "price": {"amount": 25.5, "currency": "EUR"},
                "noticedAt": "2026-11-17T10:00:00Z", "origin": "NOTED"},
               {"listingId": 1, "title": "Pull rayé", "price": {"amount": 10, "currency": "EUR"},
                "noticedAt": "2026-11-15T10:00:00Z", "origin": "NOTICED"}],
             "totals": [{"amount": 35.5, "currency": "EUR"}]}""";

    private static Playwright playwright;
    private static Browser chrome;

    private HttpServer fakeApi;
    private final AtomicReference<String> answer = new AtomicReference<>(TWO_SALES);
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
        fakeApi.createContext("/api/sales", exchange -> {
            String body = answer.get();
            if (body == null) {
                respond(exchange, 500, "text/plain", new byte[0]);
            } else {
                respond(exchange, 200, "application/json", body.getBytes(StandardCharsets.UTF_8));
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
    void shows_the_sales_and_their_total() {
        open();

        assertThat(page.locator("#sales-summary")).hasText("2 ventes, total 35,50 €.");
        assertThat(rows()).hasCount(2);
        assertThat(rows().nth(0)).containsText("17/11/2026 11:00:00");
        assertThat(rows().nth(0)).containsText("25,50 €");
        assertThat(rows().nth(0).getByRole(AriaRole.LINK)).hasText("Jean <b>bleu</b>");
        assertThat(rows().nth(0).getByRole(AriaRole.LINK)).hasAttribute("href", "#/listings/2");
        assertThat(rows().nth(1)).containsText("Pull rayé");
        assertThat(page.getByRole(AriaRole.ALERT)).isHidden();
    }

    @Test
    void tells_noted_sales_from_noticed_ones() {
        // AT-51-09 : une vente notée à la main est « notée le », une vente vue dans le dressing « constatée le »
        open();

        assertThat(rows().nth(0)).containsText("Notée le 17/11/2026 11:00:00");
        assertThat(rows().nth(1)).containsText("Constatée le 15/11/2026 11:00:00");
    }

    @Test
    void says_when_there_is_no_sale() {
        answer.set("""
                {"sales": [], "totals": []}""");
        open();

        assertThat(page.locator("#sales-summary")).hasText("Aucune vente constatée.");
        assertThat(page.locator("#sales")).isHidden();
    }

    @Test
    void says_when_the_sales_are_unavailable() {
        answer.set(null);
        open();

        assertThat(page.getByRole(AriaRole.ALERT)).hasText("Ventes indisponibles.");
        assertThat(rows()).hasCount(0);
    }

    private void open() {
        BuiltFrontend.open(page, fakeApi, "sales",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
    }

    private Locator rows() {
        return page.locator("#sales tbody tr");
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
