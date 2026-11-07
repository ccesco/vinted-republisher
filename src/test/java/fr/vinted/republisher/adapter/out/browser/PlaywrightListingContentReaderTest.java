package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.vinted.republisher.domain.backup.BackupPace;
import fr.vinted.republisher.domain.backup.FormOption;
import fr.vinted.republisher.domain.backup.FormOptions;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.PauseRange;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static fr.vinted.republisher.adapter.out.browser.EditFormFixtures.CATALOG_ROW;
import static fr.vinted.republisher.adapter.out.browser.EditFormFixtures.anItem;
import static fr.vinted.republisher.adapter.out.browser.EditFormFixtures.itemRow;
import static fr.vinted.republisher.adapter.out.browser.EditFormFixtures.scriptsWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the installed Chrome against local pages imitating Vinted's edit form, as observed on 2026-11-07: never
 * the real site. The form displays its labels a little after loading, as Vinted does.
 */
@Tag("browser")
class PlaywrightListingContentReaderTest {

    private static final Path INSTALLED_CHROME = Path.of(Objects.requireNonNullElse(
            System.getenv("CHROME_PATH"), "C:/Program Files/Google/Chrome/Application/chrome.exe"));
    private static final Duration LABEL_TIMEOUT = Duration.ofSeconds(2);

    private static final String FORM_PAGE = """
            <!DOCTYPE html><html><head><title>Modifier l'annonce | Vinted</title>
            <script>self.__next_f = self.__next_f || [];</script></head><body>
            <form action="/save" method="post">
              <input name="title" data-testid="title--input" value="Paire chaussure 9-12 mois">
              <textarea name="description" data-testid="description--input">2 paires</textarea>
              <input name="condition" type="text" readonly>
              <div data-testid="category-condition-single-list-input--value" id="condition">Sélectionne un état</div>
              <div data-testid="category-size-single-grid_chips-input--value" id="size">Sélectionne une taille</div>
              <div data-testid="color-select-dropdown-input--value" id="color">Sélectionne une couleur</div>
              <div data-testid="category-material-multi-list-input--value">Sélectionne un matériau</div>
              <div data-testid="1-package-size--cell--title"><span
                data-testid="package-size-suggestion-badge-id-1">Recommandé</span> Petit</div>
              <input type="radio" name="package" data-testid="package_type_selector_1--input" checked>
              <div data-testid="2-package-size--cell--title">Moyen</div>
              <input type="radio" name="package" data-testid="package_type_selector_2--input">
              <button type="submit">Enregistrer</button>
            </form>
            <img src="/missing-photo">
            %s
            <script>
              setTimeout(() => {
                document.getElementById('condition').textContent = 'Très bon état';
                document.getElementById('color').textContent = 'Blanc';
                const size = %s;
                if (size) document.getElementById('size').textContent = size;
              }, 300);
              for (const type of ['click', 'input', 'change', 'submit', 'keydown']) {
                addEventListener(type, () => fetch('/interaction?' + type), true);
              }
            </script>
            </body></html>""";

    @TempDir
    Path profileDirectory;
    private HttpServer fakeVinted;
    private int debugPort;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final Map<Long, Integer> formStatus = new ConcurrentHashMap<>();
    private final Map<Long, String> displayedSize = new ConcurrentHashMap<>();
    private final Map<Long, String> pageOf = new ConcurrentHashMap<>();

    @BeforeEach
    void startFakeVinted() throws IOException {
        fakeVinted = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeVinted.createContext("/items/", exchange -> {
            record(exchange);
            long id = Long.parseLong(exchange.getRequestURI().getPath().replaceFirst("/items/(\\d+)/edit", "$1"));
            if (formStatus.getOrDefault(id, 200) == 302) {
                exchange.getResponseHeaders().add("Location", "/member/login");
                respond(exchange, 302, "");
                return;
            }
            respond(exchange, formStatus.getOrDefault(id, 200), pageOf.getOrDefault(id, formPage(id)));
        });
        fakeVinted.createContext("/", exchange -> {
            record(exchange);
            respond(exchange, exchange.getRequestURI().getPath().startsWith("/member/login") ? 200 : 404,
                    "<!DOCTYPE html><html><body>Connexion</body></html>");
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
    class Local_form {

        @Test
        void reads_identifiers_and_the_labels_displayed_late() {
            displayedSize.put(10L, "36");

            ListingContent content = readOnly(10);

            assertThat(content.id()).isEqualTo(new VintedListingId(10));
            assertThat(content.category()).isEqualTo(new FormOption(1653, "Chaussures bébé"));
            assertThat(content.brand()).contains(new FormOption(60, "Kiabi"));
            assertThat(content.condition()).isEqualTo(new FormOption(2, "Très bon état"));
            assertThat(content.size()).contains(new FormOption(588, "36"));
            assertThat(content.colors()).contains(new FormOptions(List.of(12L), "Blanc"));
            assertThat(content.packageSize()).isEqualTo(new FormOption(1, "Petit"));
        }

        @Test
        void saves_an_empty_label_when_the_form_never_displays_it() {
            ListingContent content = readOnly(10);

            assertThat(content.size()).contains(new FormOption(588, ""));
            assertThat(content.condition().label()).isEqualTo("Très bon état");
        }

        @Test
        void never_clicks_types_nor_submits_and_only_sends_reading_requests() {
            displayedSize.put(10L, "36");

            readOnly(10);

            assertThat(requests).isNotEmpty()
                    .allMatch(request -> request.startsWith("GET "))
                    .noneMatch(request -> request.contains("/interaction") || request.contains("/save"));
        }

        @Test
        void reads_each_listing_in_one_tab_with_a_pause_and_closes_it() {
            List<Long> tabs = new ArrayList<>();
            List<VintedListingId> read = new ArrayList<>();

            reader().read(List.of(new VintedListingId(10), new VintedListingId(11)), briskPace(), content -> {
                read.add(content.id());
                tabs.add(tabsOnFakeVinted());
            });

            assertThat(read).containsExactly(new VintedListingId(10), new VintedListingId(11));
            assertThat(tabs).containsExactly(1L, 1L);
            assertThat(tabsOnFakeVinted()).isZero();
        }
    }

    @Nested
    class Login_redirect {

        @Test
        void redirection_by_the_server_means_the_session_is_disconnected() {
            formStatus.put(10L, 302);

            assertThatThrownBy(() -> readOnly(10)).isInstanceOf(SessionDisconnected.class)
                    .hasMessageContaining("/member/login");
        }

        @Test
        void redirection_by_script_means_the_session_is_disconnected() {
            pageOf.put(10L, "<!DOCTYPE html><html><body><script>location.replace('/member/login')</script></body></html>");

            assertThatThrownBy(() -> readOnly(10)).isInstanceOf(SessionDisconnected.class);
        }
    }

    @Nested
    class Blocked_form {

        @Test
        void http_error_means_vinted_is_unreachable_and_nothing_is_read() {
            formStatus.put(11L, 403);
            List<VintedListingId> read = new ArrayList<>();

            assertThatThrownBy(() -> reader().read(List.of(new VintedListingId(10), new VintedListingId(11),
                    new VintedListingId(12)), briskPace(), content -> read.add(content.id())))
                    .isInstanceOf(VintedUnreachable.class).hasMessageContaining("HTTP 403");
            assertThat(read).containsExactly(new VintedListingId(10));
            assertThat(requests).noneMatch(request -> request.contains("/items/12/"));
        }
    }

    private ListingContent readOnly(long id) {
        List<ListingContent> read = new ArrayList<>();
        reader().read(List.of(new VintedListingId(id)), briskPace(), read::add);
        return read.getFirst();
    }

    private PlaywrightListingContentReader reader() {
        ChromeTabs tabs = new ChromeTabs(new Chrome(INSTALLED_CHROME, profileDirectory, debugPort));
        return new PlaywrightListingContentReader(address("/items/{id}/edit"), LABEL_TIMEOUT, tabs, Clock.systemUTC());
    }

    private String formPage(long id) {
        String scripts = scriptsWith(itemRow(anItem(id)), CATALOG_ROW).stream()
                .map(script -> "<script>" + script + "</script>").collect(Collectors.joining("\n"));
        String size = Optional.ofNullable(displayedSize.get(id)).map(label -> "'" + label + "'").orElse("null");
        return FORM_PAGE.formatted(scripts, size);
    }

    private long tabsOnFakeVinted() {
        try (Playwright playwright = Playwright.create();
             Browser chrome = playwright.chromium().connectOverCDP("http://127.0.0.1:" + debugPort)) {
            return chrome.contexts().getFirst().pages().stream()
                    .filter(page -> page.url().startsWith(address(""))).count();
        }
    }

    private String address(String path) {
        return "http://127.0.0.1:" + fakeVinted.getAddress().getPort() + path;
    }

    private static BackupPace briskPace() {
        return new BackupPace(new PauseRange(Duration.ofMillis(20), Duration.ofMillis(50)), new Random(42));
    }

    private void record(HttpExchange exchange) {
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
