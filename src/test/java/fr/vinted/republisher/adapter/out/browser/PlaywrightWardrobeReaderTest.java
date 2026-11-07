package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.PauseRange;
import fr.vinted.republisher.domain.wardrobe.ScanPace;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.domain.wardrobe.WardrobeListing;
import fr.vinted.republisher.domain.wardrobe.WardrobeUnreadable;
import fr.vinted.republisher.domain.wardrobe.WardrobeVisit;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the installed Chrome against local pages imitating the wardrobe and its listings: never the real site.
 * Like Vinted, the wardrobe shows more listings when its end comes into view while the member scrolls.
 */
@Tag("browser")
class PlaywrightWardrobeReaderTest {

    private static final Path INSTALLED_CHROME = Path.of(Objects.requireNonNullElse(
            System.getenv("CHROME_PATH"), "C:/Program Files/Google/Chrome/Application/chrome.exe"));
    private static final String MEMBER = "/member/7";
    private static final String ITEMS = "/items/";

    private static final String WARDROBE_PAGE = """
            <!DOCTYPE html><html><body>
            <p>À propos : 2 articles neufs</p>
            <div>%s</div>
            <a href="/items/999-a-suggestion">Vous aimerez aussi</a>
            <img src="/tracker?campaign=a|b">
            <iframe src="/missing-advert"></iframe>
            <div id="grid">%s</div>
            <div id="end" style="margin-top: 3000px">fin</div>
            <script>
              const onVinted = %d;
              let shown = 0;
              function showNextListings() {
                const grid = document.getElementById('grid');
                for (const last = Math.min(shown + 20, onVinted); shown < last; ) {
                  shown++;
                  const item = document.createElement('div');
                  item.dataset.testid = 'grid-item';
                  item.style.height = '100px';
                  const link = document.createElement('a');
                  link.href = '/items/' + shown + '-pull-raye?referrer=catalog';
                  link.textContent = 'Pull ' + shown;
                  item.append(link);
                  grid.append(item);
                }
              }
              showNextListings();
              addEventListener('scroll', () => {
                if (document.getElementById('end').getBoundingClientRect().top < innerHeight) {
                  showNextListings();
                }
              });
            </script>
            </body></html>""";

    private static final String LISTING_PAGE = """
            <!DOCTYPE html><html><head>
            <script type="application/ld+json">{"@type": "BreadcrumbList", "itemListElement": []}</script>
            %s
            </head><body><h1>Annonce</h1><img src="/missing-photo"></body></html>""";

    @TempDir
    Path profileDirectory;

    private HttpServer fakeVinted;
    private int debugPort;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<Long> listingRequestTimes = new CopyOnWriteArrayList<>();
    private final Map<Long, Integer> listingStatus = new ConcurrentHashMap<>();
    private final Map<Long, String> availability = new ConcurrentHashMap<>();
    private final Map<Long, String> structuredData = new ConcurrentHashMap<>();
    private String header = "45 articles";
    private String strayLinks = "";
    private int listingsOnVinted = 45;
    private int wardrobeStatus = 200;

    @BeforeEach
    void startFakeVinted() throws IOException {
        fakeVinted = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeVinted.createContext(MEMBER, exchange -> {
            record(exchange);
            respond(exchange, wardrobeStatus, WARDROBE_PAGE.formatted(header, strayLinks, listingsOnVinted));
        });
        fakeVinted.createContext(ITEMS, exchange -> {
            record(exchange);
            listingRequestTimes.add(System.nanoTime());
            long id = Long.parseLong(exchange.getRequestURI().getPath().replaceFirst("/items/(\\d+).*", "$1"));
            respond(exchange, listingStatus.getOrDefault(id, 200), listingPage(id));
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
        void reads_the_total_displayed_and_every_link_without_duplicates() {
            WardrobeListing wardrobe = goThrough();

            assertThat(wardrobe.displayedTotal()).isEqualTo(45);
            assertThat(wardrobe.links()).extracting(ListingLink::id).containsExactlyInAnyOrderElementsOf(
                    LongStream.rangeClosed(1, 45).mapToObj(VintedListingId::new).toList());
        }

        @Test
        void keeps_the_address_of_a_listing_without_its_tracking_parameters() {
            assertThat(goThrough().links().getFirst().url())
                    .isEqualTo(URI.create(address("/items/1-pull-raye")));
        }

        @Test
        void reads_each_listing_it_is_asked_to_open() {
            listingsOnVinted = 3;
            header = "3 articles";
            availability.put(2L, "https://schema.org/OutOfStock");

            List<ScannedListing> read = readEverything().read;

            assertThat(read).extracting(listing -> listing.id().value()).containsExactly(1L, 2L, 3L);
            assertThat(read).extracting(ScannedListing::active).containsExactly(true, false, true);
            assertThat(read.getFirst().title()).isEqualTo("Pull 1");
            assertThat(read.getFirst().price().amount()).isEqualByComparingTo("12.50");
            assertThat(read.getFirst().price().currency()).isEqualTo("EUR");
            assertThat(read.getFirst().url()).isEqualTo(URI.create(address("/items/1-pull-raye")));
        }

        @Test
        void opens_only_what_the_visit_asks_for() {
            listingsOnVinted = 3;
            header = "3 articles";

            read(new Visit(links -> links.subList(2, 3)), briskPace());

            assertThat(requests).filteredOn(request -> request.contains(ITEMS)).hasSize(1).first()
                    .asString().contains("/items/3-");
        }

        @Test
        void stays_in_one_tab_from_the_wardrobe_to_the_last_listing() {
            listingsOnVinted = 3;
            header = "3 articles";
            List<Long> tabs = new ArrayList<>();

            read(new Visit(UnaryOperator.identity()) {
                @Override
                public void record(ScannedListing listing) {
                    tabs.add(tabsOnFakeVinted());
                }
            }, briskPace());

            assertThat(tabs).containsExactly(1L, 1L, 1L);
        }

        @Test
        void pauses_between_two_listings() {
            listingsOnVinted = 3;
            header = "3 articles";
            PauseRange brisk = new PauseRange(Duration.ofMillis(20), Duration.ofMillis(20));
            PauseRange steady = new PauseRange(Duration.ofMillis(300), Duration.ofMillis(300));

            read(new Visit(UnaryOperator.identity()), new ScanPace(brisk, steady, new Random(42)));

            assertThat(listingRequestTimes).hasSize(3);
            assertThat(Duration.ofNanos(listingRequestTimes.get(1) - listingRequestTimes.get(0)))
                    .isGreaterThanOrEqualTo(Duration.ofMillis(300));
            assertThat(Duration.ofNanos(listingRequestTimes.get(2) - listingRequestTimes.get(1)))
                    .isGreaterThanOrEqualTo(Duration.ofMillis(300));
        }

        @Test
        void pauses_after_each_scroll() {
            // 45 listings shown by 20: at least two scrolls after the first ones.
            PauseRange steady = new PauseRange(Duration.ofMillis(300), Duration.ofMillis(300));
            long start = System.nanoTime();

            read(new Visit(links -> List.of()), new ScanPace(steady, steady, new Random(42)));

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(600));
        }

        @Test
        void only_sends_reading_requests_and_never_calls_the_api() {
            readEverything();

            assertThat(requests).isNotEmpty()
                    .allMatch(request -> request.startsWith("GET "))
                    .noneMatch(request -> request.contains("/api/"));
        }

        @Test
        void reads_a_total_written_with_a_non_breaking_space() {
            header = "45\u00A0articles";

            assertThat(goThrough().displayedTotal()).isEqualTo(45);
        }

        @Test
        void reads_a_total_written_with_a_thousands_separator() {
            header = "1 046 articles";

            assertThat(goThrough().displayedTotal()).isEqualTo(1046);
        }

        @Test
        void reads_a_wardrobe_of_one_listing() {
            header = "1 article";
            listingsOnVinted = 1;

            assertThat(readEverything().read).hasSize(1);
        }

        @Test
        void reads_links_of_a_grid_marked_by_a_class() {
            listingsOnVinted = 0;
            header = "1 article";
            strayLinks = "<div class=\"grid-item\"><a href=\"/items/1-pull\">Pull</a></div>";

            assertThat(goThrough().links()).extracting(link -> link.id().value()).containsExactly(1L);
        }

        @Test
        void ignores_links_that_do_not_lead_to_a_listing_of_the_site() {
            listingsOnVinted = 1;
            header = "1 article";
            strayLinks = """
                    <div data-testid="grid-item"><a href="https://ailleurs.test/items/2-pull">Ailleurs</a></div>
                    <div data-testid="grid-item"><a href="/items/new">Vendre</a></div>
                    <div data-testid="grid-item"><a href="/items/4-pull/edit">Modifier</a></div>
                    <div data-testid="grid-item"><a href="javascript:void('/items/3')">Script</a></div>""";

            assertThat(goThrough().links()).extracting(link -> link.id().value()).containsExactly(1L);
        }

        @Test
        void closes_its_tab_and_leaves_chrome_open() {
            readEverything();

            assertThat(tabsOnFakeVinted()).isZero();
        }
    }

    @Nested
    class Incomplete_wardrobe {

        @Test
        void stops_when_no_new_link_comes_and_gives_what_it_found() {
            header = "46 articles";

            WardrobeListing wardrobe = goThrough();

            assertThat(wardrobe.displayedTotal()).isEqualTo(46);
            assertThat(wardrobe.links()).hasSize(45);
        }
    }

    @Nested
    class Blocked_wardrobe {

        @Test
        void error_on_the_wardrobe_page_means_vinted_is_unreachable() {
            wardrobeStatus = 403;

            assertThatThrownBy(PlaywrightWardrobeReaderTest.this::goThrough)
                    .isInstanceOf(VintedUnreachable.class).hasMessageContaining("HTTP 403");
        }

        @Test
        void error_on_a_listing_stops_the_reading_without_trying_again() {
            listingsOnVinted = 3;
            header = "3 articles";
            listingStatus.put(2L, 403);
            Visit visit = new Visit(UnaryOperator.identity());

            assertThatThrownBy(() -> read(visit, briskPace()))
                    .isInstanceOf(VintedUnreachable.class).hasMessageContaining("HTTP 403");

            assertThat(visit.read).extracting(listing -> listing.id().value()).containsExactly(1L);
            assertThat(requests).filteredOn(request -> request.contains(ITEMS)).hasSize(2);
        }

        @Test
        void listing_gone_means_vinted_is_unreachable() {
            listingsOnVinted = 1;
            header = "1 article";
            listingStatus.put(1L, 404);

            assertThatThrownBy(PlaywrightWardrobeReaderTest.this::readEverything)
                    .isInstanceOf(VintedUnreachable.class).hasMessageContaining("HTTP 404");
        }

        @Test
        void closes_its_tab_after_a_failure() {
            listingsOnVinted = 1;
            header = "1 article";
            listingStatus.put(1L, 403);

            assertThatThrownBy(PlaywrightWardrobeReaderTest.this::readEverything)
                    .isInstanceOf(VintedUnreachable.class);

            assertThat(tabsOnFakeVinted()).isZero();
        }
    }

    @Nested
    class Unexpected_page {

        @Test
        void header_without_a_total_is_unreadable() {
            header = "Beaucoup d'articles";

            assertThatThrownBy(PlaywrightWardrobeReaderTest.this::goThrough)
                    .isInstanceOf(WardrobeUnreadable.class).hasMessageContaining("no total");
        }

        @Test
        void two_different_totals_on_the_page_are_unreadable() {
            header = "45 articles</div><div>20 articles";

            assertThatThrownBy(PlaywrightWardrobeReaderTest.this::goThrough)
                    .isInstanceOf(WardrobeUnreadable.class).hasMessageContaining("several totals");
        }

        @Test
        void listing_page_without_a_product_is_unreadable() {
            listingsOnVinted = 1;
            header = "1 article";
            structuredData.put(1L, "");

            assertThatThrownBy(PlaywrightWardrobeReaderTest.this::readEverything)
                    .isInstanceOf(WardrobeUnreadable.class).hasMessageContaining("no product");
        }
    }

    /** Opens what its choice keeps of the links found, and remembers what it read. */
    private static class Visit implements WardrobeVisit {

        private final UnaryOperator<List<ListingLink>> choice;
        private final List<ScannedListing> read = new ArrayList<>();
        private WardrobeListing wardrobe;

        Visit(UnaryOperator<List<ListingLink>> choice) {
            this.choice = choice;
        }

        @Override
        public List<ListingLink> listingsToOpen(WardrobeListing found) {
            wardrobe = found;
            return choice.apply(found.links());
        }

        @Override
        public void record(ScannedListing listing) {
            read.add(listing);
        }
    }

    private WardrobeListing goThrough() {
        Visit visit = new Visit(links -> List.of());
        read(visit, briskPace());
        return visit.wardrobe;
    }

    private Visit readEverything() {
        Visit visit = new Visit(UnaryOperator.identity());
        read(visit, briskPace());
        return visit;
    }

    private void read(Visit visit, ScanPace pace) {
        ChromeTabs tabs = new ChromeTabs(new Chrome(INSTALLED_CHROME, profileDirectory, debugPort));
        new PlaywrightWardrobeReader(URI.create(address(MEMBER)), Duration.ofSeconds(2), tabs, Clock.systemUTC())
                .read(pace, visit);
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

    private static ScanPace briskPace() {
        PauseRange brisk = new PauseRange(Duration.ofMillis(20), Duration.ofMillis(50));
        return new ScanPace(brisk, brisk, new Random(42));
    }

    private void record(HttpExchange exchange) {
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
    }

    private String listingPage(long id) {
        return LISTING_PAGE.formatted(structuredData.getOrDefault(id, """
                <script type="application/ld+json">{"@context": "https://schema.org", "@type": "Product",
                  "name": "Pull %d", "offers": {"@type": "Offer", "price": "12.50", "priceCurrency": "EUR",
                  "url": "%s", "availability": "%s"}}</script>"""
                .formatted(id, address(ITEMS + id), availability.getOrDefault(id, "https://schema.org/InStock"))));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
