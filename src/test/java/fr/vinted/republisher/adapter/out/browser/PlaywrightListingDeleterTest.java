package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.vinted.republisher.domain.deletion.DeletionVisit;
import fr.vinted.republisher.domain.deletion.ListingPageUnreadable;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the installed Chrome against a local page imitating a listing on Vinted, as observed with the member on
 * 2026-11-14 (AT-09-20): its delete button, then its confirmation dialog. Never the real site.
 */
@Tag("browser")
class PlaywrightListingDeleterTest {

    private static final Path INSTALLED_CHROME = Path.of(Objects.requireNonNullElse(
            System.getenv("CHROME_PATH"), "C:/Program Files/Google/Chrome/Application/chrome.exe"));
    private static final Duration CONTROL_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration LOAD_TIMEOUT = Duration.ofSeconds(5);
    private static final long LISTING = 10;

    private static final String LISTING_PAGE = """
            <!DOCTYPE html><html><head><title>Pull rayé | Vinted</title>
            <script type="application/ld+json">{"@context": "https://schema.org", "@type": "Product",
              "name": "Pull rayé", "offers": {"@type": "Offer", "price": "12.50", "priceCurrency": "EUR",
              "availability": "https://schema.org/InStock"}}</script></head><body>
            <h1>Pull rayé</h1>
            <button type="button" data-testid="item-edit-button">Modifier l'annonce</button>
            %s
            <script>
              addEventListener('click', (event) => {
                const control = event.target.closest('[data-testid]');
                fetch('/interaction?click=' + (control ? control.dataset.testid : 'page'));
              }, true);
              for (const type of ['input', 'change', 'submit', 'keydown']) {
                addEventListener(type, () => fetch('/interaction?' + type), true);
              }
              const button = document.querySelector('[data-testid=item-delete-button]');
              if (button) button.addEventListener('click', () => setTimeout(() => {
                if (!%s) return;
                document.body.insertAdjacentHTML('beforeend', `
                  <div data-testid="item-delete-modal--overlay"><div role="dialog" data-testid="item-delete-modal">
                    Supprimer l'article ?
                    <button type="button" data-testid="item-delete-cancelation-button">Annuler</button>
                    <button type="button" data-testid="item-delete-confirmation-button">Supprimer</button>
                  </div></div>`);
                const closeDialog = () => {
                  if (%s) document.querySelector('[data-testid=item-delete-modal--overlay]').remove();
                };
                document.querySelector('[data-testid=item-delete-confirmation-button]').addEventListener('click',
                  () => {
                    if (%s) closeDialog();
                    fetch(location.pathname + '/delete', {method: 'POST'}).then(closeDialog);
                  });
              }, 300));
            </script>
            </body></html>""";
    private static final String DELETE_BUTTON =
            "<button type=\"button\" data-testid=\"item-delete-button\">Supprimer</button>";

    @TempDir
    Path profileDirectory;
    private HttpServer fakeVinted;
    private int debugPort;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile boolean shown = true;
    private volatile boolean goneOnceConfirmed = true;
    private volatile int deleteButtons = 1;
    private volatile boolean confirmationShows = true;
    private volatile boolean dialogCloses = true;
    private volatile boolean dialogClosesBeforeVintedAnswers;
    private volatile Duration deletionAnswerDelay = Duration.ZERO;
    private volatile int statusOnceGone = 404;
    private volatile boolean redirectsToLogin;
    private volatile boolean sessionToRefresh;
    private volatile boolean sessionComesBack = true;
    private final RecordingVisit visit = new RecordingVisit();

    @BeforeEach
    void startFakeVinted() throws IOException {
        fakeVinted = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeVinted.createContext("/items/", exchange -> {
            record(exchange);
            if ("POST".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().endsWith("/delete")) {
                pause(deletionAnswerDelay);
                if (goneOnceConfirmed) {
                    shown = false;
                }
                respond(exchange, 200, "{}");
            } else if (sessionToRefresh) {
                exchange.getResponseHeaders().add("Location",
                        "/session-refresh?ref_url=" + exchange.getRequestURI().getPath());
                respond(exchange, 302, "");
            } else if (redirectsToLogin) {
                exchange.getResponseHeaders().add("Location", "/member/login");
                respond(exchange, 302, "");
            } else if (!shown) {
                respond(exchange, statusOnceGone, "<!DOCTYPE html><html><body>Page introuvable</body></html>");
            } else if (exchange.getRequestURI().getPath().equals("/items/" + LISTING)) {
                exchange.getResponseHeaders().add("Location", "/items/" + LISTING + "-pull-raye");
                respond(exchange, 302, "");
            } else {
                respond(exchange, 200, LISTING_PAGE.formatted(DELETE_BUTTON.repeat(deleteButtons), confirmationShows,
                        dialogCloses, dialogClosesBeforeVintedAnswers));
            }
        });
        // As observed on 2026-11-14: Vinted renews its session on a page of its own, which then goes back by script.
        fakeVinted.createContext("/session-refresh", exchange -> {
            record(exchange);
            String back = exchange.getRequestURI().getQuery().replaceFirst("^ref_url=", "");
            sessionToRefresh = false;
            respond(exchange, 200, "<!DOCTYPE html><html><body><script>"
                    + (sessionComesBack ? "setTimeout(() => location.href = '" + back + "', 500);" : "")
                    + "</script></body></html>");
        });
        fakeVinted.createContext("/", exchange -> {
            record(exchange);
            respond(exchange, 200, "<!DOCTYPE html><html><body>Connexion</body></html>");
        });
        fakeVinted.setExecutor(Executors.newCachedThreadPool());
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
    class Deletion {

        @Test
        void clicks_the_delete_button_then_the_confirmation_and_nothing_else() {
            deleter().delete(link(), visit);

            assertThat(clicks()).containsExactly("click=item-delete-button", "click=item-delete-confirmation-button");
            assertThat(requests).containsOnlyOnce("POST /items/" + LISTING + "-pull-raye/delete")
                    .noneMatch(request -> request.contains("/interaction?") && !request.contains("click="));
        }

        @Test
        void asks_the_visit_after_reading_the_listing_and_right_before_confirming() {
            deleter().delete(link(), visit);

            assertThat(visit.steps).containsExactly("agrees to delete Pull rayé 12.50 EUR active", "about to confirm");
            assertThat(visit.deletionRequestedBeforeConfirming).isFalse();
        }

        @Test
        void listing_gone_once_confirmed_is_proven_deleted() {
            assertThat(deleter().delete(link(), visit)).isEmpty();
        }

        @Test
        void listing_still_shown_once_confirmed_is_answered_as_shown() {
            goneOnceConfirmed = false;

            assertThat(deleter().delete(link(), visit)).contains(pullRaye());
        }

        @Test
        void lets_vinted_answer_the_deletion_before_leaving_the_page() {
            dialogClosesBeforeVintedAnswers = true;
            deletionAnswerDelay = Duration.ofSeconds(1);

            assertThat(deleter().delete(link(), visit)).isEmpty();
        }

        @Test
        void http_error_once_confirmed_is_never_taken_for_a_deletion() {
            statusOnceGone = 500;

            assertThatThrownBy(() -> deleter().delete(link(), visit)).isInstanceOf(VintedUnreachable.class);
        }

        @Test
        void other_page_once_confirmed_is_never_taken_for_a_deletion() {
            statusOnceGone = 200;

            assertThatThrownBy(() -> deleter().delete(link(), visit)).isInstanceOf(ListingPageUnreadable.class);
        }

        @Test
        void dialog_that_never_closes_is_never_taken_for_a_deletion() {
            dialogCloses = false;

            assertThatThrownBy(() -> deleter().delete(link(), visit)).isInstanceOf(BrowserUnavailable.class);
            assertThat(tabsOnFakeVinted()).isZero();
        }

        @Test
        void closes_its_tab() {
            deleter().delete(link(), visit);

            assertThat(tabsOnFakeVinted()).isZero();
        }
    }

    @Nested
    class Before_any_click {

        @Test
        void page_without_delete_button_is_unreadable_and_nothing_is_clicked() {
            deleteButtons = 0;

            assertThatThrownBy(() -> deleter().delete(link(), visit))
                    .isInstanceOf(ListingPageUnreadable.class).hasMessageContaining("delete button");
            assertThat(clicks()).isEmpty();
            assertThat(visit.steps).isEmpty();
        }

        @Test
        void page_with_two_delete_buttons_is_unreadable_and_nothing_is_clicked() {
            deleteButtons = 2;

            assertThatThrownBy(() -> deleter().delete(link(), visit))
                    .isInstanceOf(ListingPageUnreadable.class).hasMessageContaining("2 controls");
            assertThat(clicks()).isEmpty();
            assertThat(visit.steps).isEmpty();
        }

        @Test
        void refusal_of_the_visit_goes_through_and_nothing_is_clicked() {
            visit.refusing = true;

            assertThatThrownBy(() -> deleter().delete(link(), visit)).isInstanceOf(VisitRefused.class);
            assertThat(clicks()).isEmpty();
        }

        @Test
        void login_page_means_the_session_is_disconnected() {
            redirectsToLogin = true;

            assertThatThrownBy(() -> deleter().delete(link(), visit)).isInstanceOf(SessionDisconnected.class);
            assertThat(clicks()).isEmpty();
        }

        @Test
        void listing_already_gone_means_vinted_does_not_show_it_and_nothing_is_clicked() {
            shown = false;

            assertThatThrownBy(() -> deleter().delete(link(), visit))
                    .isInstanceOf(VintedUnreachable.class).hasMessageContaining("404");
            assertThat(clicks()).isEmpty();
        }

        @Test
        void missing_confirmation_is_unreadable_and_never_confirmed() {
            confirmationShows = false;

            assertThatThrownBy(() -> deleter().delete(link(), visit))
                    .isInstanceOf(ListingPageUnreadable.class).hasMessageContaining("confirmation");
            assertThat(clicks()).containsExactly("click=item-delete-button");
            assertThat(visit.steps).doesNotContain("about to confirm");
            assertThat(requests).noneMatch(request -> request.startsWith("POST "));
            assertThat(tabsOnFakeVinted()).isZero();
        }
    }

    @Nested
    class Dry_run {

        @Test
        void waits_for_vinted_to_renew_the_session_before_reading_the_listing() {
            sessionToRefresh = true;
            visit.agreeing = false;

            assertThat(deleter().delete(link(), visit)).contains(pullRaye());
            assertThat(clicks()).isEmpty();
        }

        @Test
        void finds_the_button_without_clicking_anything_nor_asking_for_a_deletion() {
            visit.agreeing = false;

            assertThat(deleter().delete(link(), visit)).contains(pullRaye());
            assertThat(visit.steps).containsExactly("agrees to delete Pull rayé 12.50 EUR active");
            assertThat(clicks()).isEmpty();
            assertThat(requests).allMatch(request -> request.startsWith("GET "))
                    .noneMatch(request -> request.contains("/delete"));
            assertThat(tabsOnFakeVinted()).isZero();
        }
    }

    @Nested
    class Showing {

        @Test
        void reads_the_listing_without_clicking() {
            assertThat(deleter().show(link())).contains(pullRaye());
            assertThat(clicks()).isEmpty();
            assertThat(tabsOnFakeVinted()).isZero();
        }

        @Test
        void listing_answering_404_is_no_longer_shown() {
            shown = false;

            assertThat(deleter().show(link())).isEmpty();
        }

        @Test
        void listing_answering_404_once_the_session_is_renewed_is_no_longer_shown() {
            sessionToRefresh = true;
            shown = false;

            assertThat(deleter().show(link())).isEmpty();
        }

        @Test
        void waits_for_vinted_to_renew_the_session_then_reads_the_listing() {
            sessionToRefresh = true;

            assertThat(deleter().show(link())).contains(pullRaye());
        }

        @Test
        void session_never_renewed_means_the_session_is_disconnected() {
            sessionToRefresh = true;
            sessionComesBack = false;

            assertThatThrownBy(() -> deleter().show(link())).isInstanceOf(SessionDisconnected.class);
        }

        @Test
        void login_page_means_the_session_is_disconnected() {
            redirectsToLogin = true;

            assertThatThrownBy(() -> deleter().show(link())).isInstanceOf(SessionDisconnected.class);
        }
    }

    private PlaywrightListingDeleter deleter() {
        return new PlaywrightListingDeleter(CONTROL_TIMEOUT,
                new ChromeTabs(new Chrome(INSTALLED_CHROME, profileDirectory, debugPort), LOAD_TIMEOUT));
    }

    private ListingLink link() {
        return new ListingLink(new VintedListingId(LISTING), URI.create(address("/items/" + LISTING)));
    }

    private ScannedListing pullRaye() {
        return new ScannedListing(link(), "Pull rayé", new Price(new BigDecimal("12.50"), "EUR"), true);
    }

    private List<String> clicks() {
        return requests.stream().filter(request -> request.startsWith("GET /interaction?click="))
                .map(request -> request.substring("GET /interaction?".length())).toList();
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

    private static void pause(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
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

    private static final class VisitRefused extends RuntimeException {
    }

    /** Records what the deletion was asked, and whether Vinted had already been asked to delete when it confirmed. */
    private final class RecordingVisit implements DeletionVisit {

        private final List<String> steps = new CopyOnWriteArrayList<>();
        private boolean agreeing = true;
        private boolean refusing;
        private boolean deletionRequestedBeforeConfirming;

        @Override
        public boolean agreesToDelete(ScannedListing read) {
            steps.add("agrees to delete " + read.title() + " " + read.price().amount() + " " + read.price().currency()
                    + (read.active() ? " active" : " inactive"));
            if (refusing) {
                throw new VisitRefused();
            }
            return agreeing;
        }

        @Override
        public void aboutToConfirm() {
            steps.add("about to confirm");
            deletionRequestedBeforeConfirming = requests.stream().anyMatch(request -> request.startsWith("POST "));
        }
    }
}
