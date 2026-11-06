package fr.vinted.republisher.adapter.in.web;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
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
import java.util.concurrent.atomic.AtomicReference;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The static wardrobe page in the installed Chrome, against a simulated API answering as the
 * contract says: never the real application nor Vinted.
 */
@Tag("browser")
class WardrobePageTest {

    private static final String EMPTY = "{\"scanInProgress\": false, \"listings\": []}";
    private static final String TWO_LISTINGS = """
            {"scanInProgress": false, "lastScanAt": "2026-11-06T10:15:30Z", "listings": [
              {"vintedId": 2, "title": "Pull <b>rayé</b>", "price": {"amount": 12.5, "currency": "EUR"},
               "url": "https://vinted.test/items/2"},
              {"vintedId": 1, "title": "Bonnet", "price": {"amount": 1, "currency": "EUR"},
               "url": "https://vinted.test/items/1"}]}""";

    private static Playwright playwright;
    private static Browser chrome;

    private HttpServer fakeApi;
    private final AtomicReference<String> overview = new AtomicReference<>(EMPTY);
    private final AtomicReference<String> launchAnswer = new AtomicReference<>(EMPTY);
    private final AtomicBoolean applicationDown = new AtomicBoolean();
    private final List<String> launchedScans = new CopyOnWriteArrayList<>();
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
        fakeApi.createContext("/wardrobe.html", exchange -> {
            try (InputStream html = getClass().getResourceAsStream("/static/wardrobe.html")) {
                respond(exchange, 200, "text/html; charset=utf-8", html.readAllBytes());
            }
        });
        fakeApi.createContext("/api/wardrobe", exchange -> {
            if (applicationDown.get()) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            } else {
                respond(exchange, 200, "application/json", overview.get().getBytes(StandardCharsets.UTF_8));
            }
        });
        fakeApi.createContext("/api/wardrobe/scan", exchange -> {
            launchedScans.add(exchange.getRequestMethod());
            respond(exchange, 202, "application/json", launchAnswer.get().getBytes(StandardCharsets.UTF_8));
        });
        fakeApi.start();
        page = chrome.newPage();
    }

    @AfterEach
    void stopFakeApi() {
        page.close();
        fakeApi.stop(0);
    }

    @Test
    void shows_the_inventory_and_offers_to_scan() {
        // Étant donné un inventaire de deux annonces
        overview.set(TWO_LISTINGS);

        // Quand j'ouvre la page du dressing
        open();

        // Alors je vois la date du scan, le nombre d'annonces, leur titre, leur prix et leur lien
        // Et un bouton "Scanner"
        assertThat(summary()).containsText("2 annonces");
        assertThat(summary()).containsText("06/11/2026");
        assertThat(rows()).hasCount(2);
        assertThat(rows().nth(0)).containsText("12,50");
        Locator link = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Bonnet"));
        assertThat(link).hasAttribute("href", "https://vinted.test/items/1");
        assertThat(button()).isVisible();
    }

    @Test
    void shows_a_title_as_text_never_as_html() {
        overview.set(TWO_LISTINGS);

        open();

        assertThat(rows().nth(0)).containsText("Pull <b>rayé</b>");
    }

    @Test
    void says_when_no_scan_has_succeeded_yet() {
        open();

        assertThat(summary()).hasText("Aucun scan réussi pour l'instant.");
        assertThat(rows()).hasCount(0);
    }

    @Test
    void scan_button_launches_a_scan() {
        open();

        button().click();

        page.waitForCondition(() -> !launchedScans.isEmpty());
        assertThat(launchedScans).containsExactly("POST");
    }

    @Test
    void shows_the_scan_launched_from_the_answer_to_the_request() {
        open();
        launchAnswer.set("{\"scanInProgress\": true, \"listings\": []}");

        button().click();

        assertThat(progress()).isVisible();
        assertThat(button()).isHidden();
    }

    @Test
    void follows_a_scan_in_progress_until_done() {
        overview.set("{\"scanInProgress\": true, \"listings\": []}");
        open();
        assertThat(progress()).isVisible();

        overview.set(TWO_LISTINGS);

        assertThat(rows()).hasCount(2, new LocatorAssertions.HasCountOptions().setTimeout(5000));
        assertThat(progress()).isHidden();
        assertThat(button()).isVisible();
    }

    @Test
    void explains_an_unreadable_wardrobe() {
        overview.set("{\"scanInProgress\": false, \"problem\": \"WARDROBE_UNREADABLE\", \"listings\": []}");

        open();

        assertThat(problem()).containsText("Le dressing est illisible");
    }

    @Test
    void explains_an_unreachable_vinted() {
        overview.set("{\"scanInProgress\": false, \"problem\": \"VINTED_UNREACHABLE\", \"listings\": []}");

        open();

        assertThat(problem()).containsText("Vinted est inaccessible");
    }

    @Test
    void explains_an_unavailable_browser() {
        overview.set("{\"scanInProgress\": false, \"problem\": \"BROWSER_UNAVAILABLE\", \"listings\": []}");

        open();

        assertThat(problem()).containsText("Le navigateur est indisponible");
    }

    @Test
    void explains_a_failed_scan() {
        overview.set("{\"scanInProgress\": false, \"problem\": \"SCAN_FAILED\", \"listings\": []}");

        open();

        assertThat(problem()).containsText("Le scan a échoué");
    }

    @Test
    void retries_until_the_application_answers_again() {
        applicationDown.set(true);
        open();
        assertThat(problem()).containsText("L'application ne répond pas");

        overview.set(TWO_LISTINGS);
        applicationDown.set(false);

        assertThat(rows()).hasCount(2, new LocatorAssertions.HasCountOptions().setTimeout(5000));
        assertThat(problem()).isHidden();
    }

    private void open() {
        page.navigate("http://127.0.0.1:" + fakeApi.getAddress().getPort() + "/wardrobe.html");
    }

    private Locator summary() {
        return page.locator("#wardrobe-summary");
    }

    private Locator progress() {
        return page.locator("#scan-progress");
    }

    private Locator problem() {
        return page.locator("#wardrobe-problem");
    }

    private Locator rows() {
        return page.locator("#listings li");
    }

    private Locator button() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Scanner").setExact(true));
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
