package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionStatus;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static fr.vinted.republisher.domain.session.SessionStatus.CONNECTED;
import static fr.vinted.republisher.domain.session.SessionStatus.DISCONNECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Runs the installed Chrome against local pages imitating Vinted: never the real site.
 * Like Vinted, the fake profile settings page redirects a visitor to the login page.
 */
@Tag("browser")
class PlaywrightVintedAccountTest {

    private static final Path INSTALLED_CHROME = Path.of(Objects.requireNonNullElse(
            System.getenv("CHROME_PATH"), "C:/Program Files/Google/Chrome/Application/chrome.exe"));
    private static final String MEMBER = "/member/settings/profile";
    private static final String VISITOR = "/visitor/settings/profile";
    private static final String VISITOR_REDIRECTED_BY_SCRIPT = "/scripted-visitor/settings/profile";
    private static final String MEMBER_LOGGING_IN = "/logging-in/settings/profile";
    private static final String BLOCKED = "/blocked/settings/profile";
    private static final String MEMBER_DISPLAYED = "/displayed/settings/profile";
    private static final String MEMBER_WITH_TRAILING_SLASH = "/slash/settings/profile";
    private static final String VISITOR_SENT_TO_A_BLANK_PAGE = "/blank/settings/profile";

    @TempDir
    Path profileDirectory;

    private HttpServer fakeVinted;
    private int debugPort;
    private final AtomicBoolean memberHasLoggedIn = new AtomicBoolean();
    private final CompletableFuture<Void> memberSeesTheProfileSettings = new CompletableFuture<>();

    @BeforeEach
    void startFakeVinted() throws IOException {
        fakeVinted = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeVinted.createContext(MEMBER, exchange -> page(exchange, "Mon profil"));
        fakeVinted.createContext(VISITOR, exchange -> redirect(exchange, "/login"));
        fakeVinted.createContext(VISITOR_REDIRECTED_BY_SCRIPT,
                exchange -> page(exchange, "<script>setTimeout(() => location.href = '/login', 300)</script>"));
        fakeVinted.createContext(MEMBER_LOGGING_IN, exchange -> {
            if (memberHasLoggedIn.get()) {
                page(exchange, "Mon profil");
            } else {
                redirect(exchange, "/login-then-back");
            }
        });
        fakeVinted.createContext(MEMBER_WITH_TRAILING_SLASH, exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/")) {
                page(exchange, "Mon profil");
            } else {
                redirect(exchange, MEMBER_WITH_TRAILING_SLASH + "/");
            }
        });
        fakeVinted.createContext(VISITOR_SENT_TO_A_BLANK_PAGE,
                exchange -> page(exchange, "<script>setTimeout(() => location.href = 'about:blank', 300)</script>"));
        fakeVinted.createContext(MEMBER_DISPLAYED, exchange ->
                page(exchange, "<script>addEventListener('load', () => fetch('/displayed'))</script>"));
        fakeVinted.createContext("/displayed", exchange -> {
            memberSeesTheProfileSettings.complete(null);
            page(exchange, "");
        });
        fakeVinted.createContext(BLOCKED, exchange -> page(exchange, 403, "Ta session a été bloquée"));
        fakeVinted.createContext("/login", exchange -> page(exchange, "S'inscrire | Se connecter"));
        fakeVinted.createContext("/login-then-back", exchange -> {
            memberHasLoggedIn.set(true);
            page(exchange, "<script>setTimeout(() => location.href = '" + MEMBER_LOGGING_IN + "', 300)</script>");
        });
        fakeVinted.start();
        try (ServerSocket socket = new ServerSocket(0)) {
            debugPort = socket.getLocalPort();
        }
    }

    @AfterEach
    void stopChromeAndFakeVinted() {
        // Chrome must be gone before the temporary profile is deleted: a killed Chrome still writes to it.
        List<ProcessHandle> chromeProcesses = ProcessHandle.current().descendants().toList();
        if (!chromeProcesses.isEmpty()) {
            try (Playwright playwright = Playwright.create();
                 Browser chrome = playwright.chromium().connectOverCDP("http://127.0.0.1:" + debugPort)) {
                chrome.newBrowserCDPSession().send("Browser.close");
            } catch (PlaywrightException alreadyClosed) {
                // Nothing left to close.
            }
            chromeProcesses.forEach(process -> process.onExit().orTimeout(10, TimeUnit.SECONDS).join());
        }
        fakeVinted.stop(0);
    }

    @Nested
    class Session_check {

        @Test
        void finds_the_session_connected_when_the_profile_settings_stay_displayed() {
            assertThat(accountWith(MEMBER).checkSession()).isEqualTo(CONNECTED);
        }

        @Test
        void finds_the_session_disconnected_when_redirected_to_the_login_page() {
            assertThat(accountWith(VISITOR).checkSession()).isEqualTo(DISCONNECTED);
        }

        @Test
        void finds_the_session_disconnected_when_a_script_redirects_to_the_login_page() {
            assertThat(accountWith(VISITOR_REDIRECTED_BY_SCRIPT).checkSession()).isEqualTo(DISCONNECTED);
        }

        @Test
        void finds_the_session_connected_when_vinted_adds_a_trailing_slash() {
            assertThat(accountWith(MEMBER_WITH_TRAILING_SLASH).checkSession()).isEqualTo(CONNECTED);
        }

        @Test
        void finds_the_session_disconnected_when_sent_to_a_page_without_path() {
            assertThat(accountWith(VISITOR_SENT_TO_A_BLANK_PAGE).checkSession()).isEqualTo(DISCONNECTED);
        }
    }

    @Nested
    class Manual_login {

        @Test
        void connects_the_session_once_back_on_the_profile_settings() {
            PlaywrightVintedAccount account = accountWith(MEMBER_LOGGING_IN);

            assertThat(account.awaitManualLogin(Duration.ofSeconds(10))).isEqualTo(CONNECTED);
        }

        @Test
        void leaves_the_session_disconnected_when_the_timeout_expires() {
            PlaywrightVintedAccount account = accountWith(VISITOR);

            assertThat(account.awaitManualLogin(Duration.ofSeconds(1))).isEqualTo(DISCONNECTED);
        }

        @Test
        void leaves_the_session_disconnected_when_the_tab_is_closed_at_once() throws Exception {
            PlaywrightVintedAccount account = accountWith(MEMBER_DISPLAYED);
            CompletableFuture<SessionStatus> login =
                    CompletableFuture.supplyAsync(() -> account.awaitManualLogin(Duration.ofSeconds(10)));

            memberSeesTheProfileSettings.get(10, TimeUnit.SECONDS);
            closeTheTabShowing(MEMBER_DISPLAYED);

            assertThat(login.get(10, TimeUnit.SECONDS)).isEqualTo(DISCONNECTED);
        }
    }

    @Nested
    class Normal_chrome {

        @Test
        void is_started_once_and_reused_by_the_next_action() {
            PlaywrightVintedAccount account = accountWith(MEMBER);

            account.checkSession();
            account.checkSession();

            assertThat(ProcessHandle.current().children()).hasSize(1);
        }

        @Test
        void stays_open_without_the_tab_used() {
            accountWith(MEMBER).checkSession();

            try (Playwright playwright = Playwright.create();
                 Browser chrome = playwright.chromium().connectOverCDP("http://127.0.0.1:" + debugPort)) {
                List<String> openUrls = chrome.contexts().getFirst().pages().stream().map(page -> page.url()).toList();
                assertThat(openUrls).noneMatch(url -> url.contains(MEMBER));
            }
        }

        @Test
        void carries_no_automation_mark() {
            accountWith(MEMBER).checkSession();

            try (Playwright playwright = Playwright.create();
                 Browser chrome = playwright.chromium().connectOverCDP("http://127.0.0.1:" + debugPort)) {
                Page page = chrome.contexts().getFirst().newPage();
                page.navigate(fakeVintedUrl(MEMBER).toString());
                assertThat(page.evaluate("navigator.webdriver")).isEqualTo(false);
            }
        }
    }

    @Nested
    class Unreachable_vinted {

        @Test
        void is_reported_when_vinted_answers_with_an_http_error() {
            Throwable attempt = catchThrowable(() -> accountWith(BLOCKED).checkSession());

            assertThat(attempt).isInstanceOf(VintedUnreachable.class).hasMessageContaining("403");
        }

        @Test
        void is_reported_when_vinted_does_not_answer() {
            PlaywrightVintedAccount account = new PlaywrightVintedAccount(new VintedSiteProperties(URI.create("http://127.0.0.1:1" + MEMBER)),
                    new Chrome(INSTALLED_CHROME, profileDirectory, debugPort));

            Throwable attempt = catchThrowable(account::checkSession);

            assertThat(attempt).isInstanceOf(VintedUnreachable.class);
        }

        @Test
        void is_reported_during_a_manual_login_too() {
            Throwable attempt = catchThrowable(() -> accountWith(BLOCKED).awaitManualLogin(Duration.ofSeconds(1)));

            assertThat(attempt).isInstanceOf(VintedUnreachable.class);
        }
    }

    @Test
    void reports_an_unavailable_browser_when_chrome_is_not_found() {
        Throwable attempt = catchThrowable(accountWithoutChrome()::checkSession);

        assertThat(attempt).isInstanceOf(BrowserUnavailable.class);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logs_why_chrome_is_unavailable(CapturedOutput logs) {
        catchThrowable(accountWithoutChrome()::checkSession);

        assertThat(logs).contains("Chrome cannot be started from");
    }

    private PlaywrightVintedAccount accountWithoutChrome() {
        return new PlaywrightVintedAccount(new VintedSiteProperties(fakeVintedUrl(MEMBER)),
                new Chrome(profileDirectory.resolve("no-chrome.exe"), profileDirectory, debugPort));
    }

    /** As the member would, from outside the tool. */
    private void closeTheTabShowing(String path) {
        try (Playwright playwright = Playwright.create();
             Browser chrome = playwright.chromium().connectOverCDP("http://127.0.0.1:" + debugPort)) {
            chrome.contexts().getFirst().pages().stream()
                    .filter(page -> page.url().contains(path))
                    .forEach(Page::close);
        }
    }

    private PlaywrightVintedAccount accountWith(String profileSettingsPath) {
        return new PlaywrightVintedAccount(new VintedSiteProperties(fakeVintedUrl(profileSettingsPath)),
                new Chrome(INSTALLED_CHROME, profileDirectory, debugPort));
    }

    private URI fakeVintedUrl(String path) {
        return URI.create("http://127.0.0.1:" + fakeVinted.getAddress().getPort() + path);
    }

    private static void page(HttpExchange exchange, String body) throws IOException {
        page(exchange, 200, body);
    }

    private static void page(HttpExchange exchange, int status, String body) throws IOException {
        byte[] html = ("<html><body>" + body + "</body></html>").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, html.length);
        exchange.getResponseBody().write(html);
        exchange.close();
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }
}
