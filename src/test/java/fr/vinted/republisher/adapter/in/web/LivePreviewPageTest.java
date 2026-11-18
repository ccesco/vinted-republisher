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
import java.util.concurrent.atomic.AtomicReference;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * US-22 AT-22-06: the static live preview page in the installed Chrome, against a simulated API answering as the
 * contract says: never the real application nor Vinted.
 */
@Tag("browser")
class LivePreviewPageTest {

    // A 1x1 JPEG, as Chrome would send it, in base64.
    private static final String JPEG = "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP////////////////////////////////////////"
            + "//////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=";

    private static final String TWO_BROWSERS = """
            {"previews": [
               {"browser": "Chrome (port 9222)", "image": "%s", "capturedAt": "2026-11-18T10:00:00Z"},
               {"browser": "Chrome (port 9333)"}]}""".formatted(JPEG);

    private static Playwright playwright;
    private static Browser chrome;

    private HttpServer fakeApi;
    private final AtomicReference<String> answer = new AtomicReference<>(TWO_BROWSERS);
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
        fakeApi.createContext("/live-preview.html", exchange -> {
            try (InputStream content = getClass().getResourceAsStream("/static/live-preview.html")) {
                respond(exchange, 200, "text/html; charset=utf-8", content.readAllBytes());
            }
        });
        fakeApi.createContext("/api/live-previews", exchange -> {
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
    void shows_each_browser_at_work() {
        open();

        assertThat(previews()).hasCount(2);
        assertThat(previews().nth(0).getByRole(AriaRole.HEADING)).hasText("Chrome (port 9222)");
        assertThat(previews().nth(0)).containsText("Capturé à 11:00:00");
        assertThat(previews().nth(0).getByRole(AriaRole.IMG))
                .hasAttribute("src", "data:image/jpeg;base64," + JPEG);
        assertThat(previews().nth(0).getByRole(AriaRole.IMG)).hasAttribute("alt", "Page de Chrome (port 9222)");
        assertThat(previews().nth(1).getByRole(AriaRole.HEADING)).hasText("Chrome (port 9333)");
        assertThat(previews().nth(1)).containsText("Aperçu indisponible.");
        assertThat(previews().nth(1).getByRole(AriaRole.IMG)).hasCount(0);
        assertThat(page.getByRole(AriaRole.ALERT)).isHidden();
    }

    @Test
    void says_when_no_browser_is_at_work() {
        answer.set("""
                {"previews": []}""");
        open();

        assertThat(page.locator("#live-preview-summary")).hasText("Aucun navigateur au travail.");
        assertThat(previews()).hasCount(0);
    }

    @Test
    void says_when_the_previews_are_unavailable() {
        answer.set(null);
        open();

        assertThat(page.getByRole(AriaRole.ALERT)).hasText("Aperçus indisponibles.");
        assertThat(previews()).hasCount(0);
    }

    @Test
    void refreshes_by_itself() {
        answer.set(null);
        open();
        assertThat(page.getByRole(AriaRole.ALERT)).isVisible();

        answer.set(TWO_BROWSERS);

        assertThat(previews()).hasCount(2);
        assertThat(page.getByRole(AriaRole.ALERT)).isHidden();
        answer.set("""
                {"previews": []}""");
        assertThat(page.locator("#live-preview-summary")).hasText("Aucun navigateur au travail.");
        assertThat(previews()).hasCount(0);
    }

    private void open() {
        page.navigate("http://127.0.0.1:" + fakeApi.getAddress().getPort() + "/live-preview.html",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
    }

    private Locator previews() {
        return page.locator("#live-previews > article");
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
