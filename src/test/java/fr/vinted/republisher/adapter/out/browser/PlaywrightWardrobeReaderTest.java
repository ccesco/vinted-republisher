package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.ScanPace;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.domain.wardrobe.WardrobeSnapshot;
import fr.vinted.republisher.domain.wardrobe.WardrobeUnreadable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the installed Chrome against a local page imitating the wardrobe: never the real site.
 * Like Vinted, it loads the following slices when its end comes into view while the member scrolls.
 */
@Tag("browser")
class PlaywrightWardrobeReaderTest {

    private static final Path INSTALLED_CHROME = Path.of(Objects.requireNonNullElse(
            System.getenv("CHROME_PATH"), "C:/Program Files/Google/Chrome/Application/chrome.exe"));
    private static final String MEMBER = "/member/7";
    private static final String ITEMS = "/api/v2/wardrobe/7/items";
    private static final int SLICE_SIZE = 20;

    private static final String WARDROBE_PAGE = """
            <!DOCTYPE html><html><body>
            <p>À propos : 2 articles neufs</p>
            <div>%s</div>
            <img src="/tracker?campaign=a|b">
            <iframe src="/missing-advert"></iframe>
            <script>fetch(URL.createObjectURL(new Blob(['tracker'])));</script>
            <div id="grid" style="height: 3000px"></div>
            <div id="end">fin</div>
            <script>
              let page = 2;
              new IntersectionObserver(entries => {
                if (entries[0].isIntersecting) {
                  fetch('%s?page=' + page++ + '&per_page=20');
                  document.getElementById('grid').style.height = (3000 * page) + 'px';
                }
              }).observe(document.getElementById('end'));
            </script>
            </body></html>""";

    @TempDir
    Path profileDirectory;

    private HttpServer fakeVinted;
    private int debugPort;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private String header = "45 articles";
    private int listingsOnVinted = 45;
    private int refusedSlice = -1;
    private int redirectedSlice = -1;
    private String answerOfSlices;
    private int wardrobeStatus = 200;

    @BeforeEach
    void startFakeVinted() throws IOException {
        fakeVinted = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeVinted.createContext(MEMBER, exchange -> {
            record(exchange);
            respond(exchange, wardrobeStatus, "text/html; charset=utf-8", WARDROBE_PAGE.formatted(header, ITEMS));
        });
        fakeVinted.createContext(ITEMS, exchange -> {
            record(exchange);
            int slice = slice(exchange);
            if (slice == refusedSlice) {
                respond(exchange, 403, "text/html", "Ta session a été bloquée");
            } else if (slice == redirectedSlice) {
                exchange.getResponseHeaders().add("Location", "/blocked");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            } else if (answerOfSlices != null) {
                respond(exchange, 200, "application/json", answerOfSlices);
            } else {
                respond(exchange, 200, "application/json", itemsOf(slice));
            }
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
    class Reading {

        @Test
        void reads_the_total_displayed_and_every_listing_without_duplicates() {
            WardrobeSnapshot snapshot = read();

            assertThat(snapshot.displayedTotal()).isEqualTo(45);
            assertThat(snapshot.listings()).extracting(ScannedListing::id).containsExactlyInAnyOrderElementsOf(
                    LongStream.rangeClosed(1, 45).mapToObj(VintedListingId::new).toList());
        }

        @Test
        void pauses_after_each_scroll() {
            // 45 listings by slices of 20: at least two scrolls after the first slice.
            ScanPace steadyPace = new ScanPace(Duration.ofMillis(300), Duration.ofMillis(300), new Random(42));
            long start = System.nanoTime();

            readerOf(MEMBER).read(steadyPace);

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(600));
        }

        @Test
        void only_sends_reading_requests() {
            read();

            assertThat(requests).isNotEmpty().allMatch(request -> request.startsWith("GET "));
        }

        @Test
        void asks_for_the_first_slice_only_once() {
            read();

            assertThat(requests).filteredOn(request -> request.contains("page=1&")).hasSize(1);
        }

        @Test
        void reads_a_total_written_with_a_non_breaking_space() {
            header = "45 articles";

            assertThat(read().displayedTotal()).isEqualTo(45);
        }

        @Test
        void reads_a_total_written_with_a_thousands_separator() {
            header = "1 046 articles";

            assertThat(read().displayedTotal()).isEqualTo(1046);
        }

        @Test
        void reads_a_wardrobe_of_one_listing() {
            header = "1 article";
            listingsOnVinted = 1;

            assertThat(read().listings()).hasSize(1);
        }

        @Test
        void closes_its_tab_and_leaves_chrome_open() {
            read();

            try (Playwright playwright = Playwright.create();
                 Browser chrome = playwright.chromium().connectOverCDP("http://127.0.0.1:" + debugPort)) {
                assertThat(chrome.contexts().getFirst().pages())
                        .noneMatch(page -> page.url().contains(MEMBER));
            }
        }
    }

    @Nested
    class Incomplete_wardrobe {

        @Test
        void stops_when_no_new_slice_comes_and_gives_what_it_read() {
            header = "46 articles";

            WardrobeSnapshot snapshot = read();

            assertThat(snapshot.displayedTotal()).isEqualTo(46);
            assertThat(snapshot.listings()).hasSize(45);
        }
    }

    @Nested
    class Blocked_wardrobe {

        @Test
        void error_on_the_wardrobe_page_means_vinted_is_unreachable() {
            wardrobeStatus = 403;

            assertThatThrownBy(PlaywrightWardrobeReaderTest.this::read)
                    .isInstanceOf(VintedUnreachable.class).hasMessageContaining("HTTP 403");
        }

        @Test
        void error_on_the_first_slice_means_vinted_is_unreachable() {
            refusedSlice = 1;

            assertThatThrownBy(PlaywrightWardrobeReaderTest.this::read)
                    .isInstanceOf(VintedUnreachable.class).hasMessageContaining("HTTP 403");
        }

        @Test
        void error_on_a_later_slice_stops_the_reading_without_trying_again() {
            refusedSlice = 2;

            assertThatThrownBy(PlaywrightWardrobeReaderTest.this::read)
                    .isInstanceOf(VintedUnreachable.class).hasMessageContaining("HTTP 403");
            assertThat(requests).filteredOn(request -> request.contains("page=2&")).hasSize(1);
        }
    }

    @Nested
    class Unexpected_page {

        @Test
        void header_without_a_total_is_unreadable() {
            header = "Beaucoup d'articles";

            assertThatThrownBy(PlaywrightWardrobeReaderTest.this::read)
                    .isInstanceOf(WardrobeUnreadable.class).hasMessageContaining("no total");
        }

        @Test
        void two_different_totals_on_the_page_are_unreadable() {
            header = "45 articles</div><div>20 articles";

            assertThatThrownBy(PlaywrightWardrobeReaderTest.this::read)
                    .isInstanceOf(WardrobeUnreadable.class)
                    .hasMessageContaining("several totals");
        }

        @Test
        void slice_redirected_elsewhere_is_unreadable() {
            redirectedSlice = 2;

            assertThatThrownBy(PlaywrightWardrobeReaderTest.this::read)
                    .isInstanceOf(WardrobeUnreadable.class).hasMessageContaining("no content");
        }

        @Test
        void answer_of_an_unexpected_format_is_unreadable() {
            answerOfSlices = "{\"objets\": []}";

            assertThatThrownBy(PlaywrightWardrobeReaderTest.this::read)
                    .isInstanceOf(WardrobeUnreadable.class).hasMessageContaining("unexpected answer");
        }

        @Test
        void wardrobe_address_without_a_member_is_unreadable() {
            PlaywrightWardrobeReader reader = readerOf("/settings/profile");

            assertThatThrownBy(() -> reader.read(briskPace()))
                    .isInstanceOf(WardrobeUnreadable.class).hasMessageContaining("no member");
        }
    }

    private WardrobeSnapshot read() {
        return readerOf(MEMBER).read(briskPace());
    }

    private PlaywrightWardrobeReader readerOf(String path) {
        URI wardrobe = URI.create("http://127.0.0.1:" + fakeVinted.getAddress().getPort() + path);
        ChromeTabs tabs = new ChromeTabs(new Chrome(INSTALLED_CHROME, profileDirectory, debugPort));
        return new PlaywrightWardrobeReader(wardrobe, Duration.ofSeconds(2), tabs, Clock.systemUTC());
    }

    private static ScanPace briskPace() {
        return new ScanPace(Duration.ofMillis(20), Duration.ofMillis(50), new Random(42));
    }

    private void record(HttpExchange exchange) {
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
    }

    private static int slice(HttpExchange exchange) {
        return Integer.parseInt(exchange.getRequestURI().getQuery().replaceFirst("page=(\\d+).*", "$1"));
    }

    /** Each slice repeats the last listing of the previous one, as when a listing moves during the reading. */
    private String itemsOf(int slice) {
        long first = Math.max(1, (slice - 1L) * SLICE_SIZE);
        long last = Math.min(slice * (long) SLICE_SIZE, listingsOnVinted);
        return LongStream.rangeClosed(first, last).mapToObj(PlaywrightWardrobeReaderTest::item)
                .collect(Collectors.joining(",", "{\"items\": [", "]}"));
    }

    private static String item(long id) {
        return """
                {"id": %d, "title": "Pull %d", "price": {"amount": "12.5", "currency_code": "EUR"},
                 "url": "https://vinted.test/items/%d", "is_draft": false, "is_closed": false,
                 "is_reserved": false, "is_hidden": false, "is_processing": false}""".formatted(id, id, id);
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
