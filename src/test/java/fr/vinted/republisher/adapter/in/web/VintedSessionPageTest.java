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
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The static session page in the installed Chrome, against a simulated API answering as the
 * contract says: never the real application nor Vinted.
 */
@Tag("browser")
class VintedSessionPageTest {

    private static Playwright playwright;
    private static Browser chrome;

    private HttpServer fakeApi;
    private final AtomicReference<String> overview = new AtomicReference<>("{}");
    private final AtomicReference<String> launchAnswer = new AtomicReference<>("{}");
    private final AtomicBoolean applicationDown = new AtomicBoolean();
    private final List<String> launchedActions = new CopyOnWriteArrayList<>();
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
        fakeApi.createContext("/vinted-session.html", exchange -> {
            try (InputStream html = getClass().getResourceAsStream("/static/vinted-session.html")) {
                respond(exchange, "text/html; charset=utf-8", html.readAllBytes());
            }
        });
        fakeApi.createContext("/api/vinted-session", exchange -> {
            if (applicationDown.get()) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            } else {
                respond(exchange, "application/json", overview.get().getBytes(StandardCharsets.UTF_8));
            }
        });
        fakeApi.createContext("/api/vinted-session/check", exchange -> launch(exchange, "check"));
        fakeApi.createContext("/api/vinted-session/login", exchange -> launch(exchange, "login"));
        fakeApi.start();
        page = chrome.newPage();
    }

    @AfterEach
    void stopFakeApi() {
        page.close();
        fakeApi.stop(0);
    }

    @Test
    void unchecked_session_offers_to_log_in_and_to_check() {
        // Étant donné l'application qui vient de démarrer
        overview.set("{}");

        // Quand j'ouvre la page de la session Vinted
        open();

        // Alors je vois "non vérifié"
        // Et un bouton "Se connecter"
        // Et un bouton "Vérifier"
        assertStatus("non vérifié");
        assertThat(button("Se connecter")).isVisible();
        assertThat(button("Vérifier")).isVisible();
    }

    @Test
    void closed_session_offers_to_log_in_and_to_check() {
        // Étant donné une session fermée
        overview.set("{\"status\": \"DISCONNECTED\"}");

        // Quand j'ouvre la page de la session Vinted
        open();

        // Alors je vois "non connecté"
        // Et un bouton "Se connecter"
        // Et un bouton "Vérifier"
        assertStatus("non connecté");
        assertThat(button("Se connecter")).isVisible();
        assertThat(button("Vérifier")).isVisible();
    }

    @Test
    void open_session_offers_to_check_only() {
        overview.set("{\"status\": \"CONNECTED\"}");

        open();

        assertStatus("connecté");
        assertThat(button("Se connecter")).isHidden();
        assertThat(button("Vérifier")).isVisible();
    }

    @Test
    void checking_is_shown_while_in_progress() {
        overview.set("{\"currentAction\": \"CHECKING\"}");

        open();

        assertStatus("vérification en cours");
    }

    @Test
    void scanning_is_shown_while_in_progress() {
        overview.set("{\"currentAction\": \"SCANNING\"}");

        open();

        assertStatus("scan en cours");
    }

    @Test
    void backup_is_shown_while_in_progress() {
        overview.set("{\"currentAction\": \"BACKING_UP\"}");

        open();

        assertStatus("sauvegarde en cours");
    }

    @Test
    void action_in_progress_hides_the_buttons_and_is_followed_until_done() {
        overview.set("{\"status\": \"DISCONNECTED\", \"currentAction\": \"LOGGING_IN\"}");
        open();
        assertStatus("connexion en cours");
        assertThat(button("Vérifier")).isHidden();

        overview.set("{\"status\": \"CONNECTED\"}");

        assertThat(status()).hasText(exactly("connecté"), new LocatorAssertions.HasTextOptions().setTimeout(5000));
    }

    @Test
    void explains_a_manual_login_not_completed() {
        overview.set("{\"status\": \"DISCONNECTED\", \"problem\": \"MANUAL_LOGIN_NOT_COMPLETED\"}");

        open();

        assertThat(problem()).containsText("La connexion n'a pas abouti");
    }

    @Test
    void explains_an_unavailable_browser() {
        overview.set("{\"status\": \"DISCONNECTED\", \"problem\": \"BROWSER_UNAVAILABLE\"}");

        open();

        assertThat(problem()).containsText("Le navigateur est indisponible");
    }

    @Test
    void explains_an_unreachable_vinted() {
        overview.set("{\"status\": \"DISCONNECTED\", \"problem\": \"VINTED_UNREACHABLE\"}");

        open();

        assertThat(problem()).containsText("Vinted est inaccessible");
    }

    @Test
    void shows_no_problem_when_there_is_none() {
        overview.set("{\"status\": \"CONNECTED\"}");

        open();

        assertStatus("connecté");
        assertThat(problem()).isHidden();
    }

    @Test
    void retries_until_the_application_answers_again() {
        applicationDown.set(true);
        open();
        assertThat(problem()).containsText("L'application ne répond pas");

        overview.set("{\"status\": \"CONNECTED\"}");
        applicationDown.set(false);

        assertThat(status()).hasText(exactly("connecté"), new LocatorAssertions.HasTextOptions().setTimeout(5000));
        assertThat(problem()).isHidden();
    }

    @Test
    void check_button_launches_a_check() {
        overview.set("{\"status\": \"CONNECTED\"}");
        open();

        button("Vérifier").click();

        page.waitForCondition(() -> !launchedActions.isEmpty());
        assertThat(launchedActions).containsExactly("check");
    }

    @Test
    void shows_the_action_launched_from_the_answer_to_the_request() {
        overview.set("{\"status\": \"CONNECTED\"}");
        open();
        assertStatus("connecté");
        // The next reading would still show the previous state: only the answer tells the check started.
        launchAnswer.set("{\"status\": \"CONNECTED\", \"currentAction\": \"CHECKING\"}");

        button("Vérifier").click();

        assertStatus("vérification en cours");
    }

    @Test
    void log_in_button_launches_a_manual_login() {
        overview.set("{\"status\": \"DISCONNECTED\"}");
        open();

        button("Se connecter").click();

        page.waitForCondition(() -> !launchedActions.isEmpty());
        assertThat(launchedActions).containsExactly("login");
    }

    private void open() {
        page.navigate("http://127.0.0.1:" + fakeApi.getAddress().getPort() + "/vinted-session.html");
    }

    private void assertStatus(String label) {
        assertThat(status()).hasText(exactly(label));
    }

    // Playwright's hasText(String) matches a substring: "non connecté" would pass for "connecté".
    // The pattern runs in the browser, where Pattern.quote's escaping is unknown; labels hold no regex syntax.
    private static Pattern exactly(String label) {
        return Pattern.compile("^" + label + "$");
    }

    private Locator status() {
        return page.locator("#session-status");
    }

    private Locator problem() {
        return page.locator("#session-problem");
    }

    private Locator button(String label) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(label).setExact(true));
    }

    private void launch(HttpExchange exchange, String action) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            launchedActions.add(action);
        }
        respond(exchange, 202, "application/json", launchAnswer.get().getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, String contentType, byte[] body) throws IOException {
        respond(exchange, 200, contentType, body);
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
