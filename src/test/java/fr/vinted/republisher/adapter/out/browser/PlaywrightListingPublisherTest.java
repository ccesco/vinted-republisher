package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.vinted.republisher.domain.backup.CategoryDetails;
import fr.vinted.republisher.domain.backup.DownloadedPhoto;
import fr.vinted.republisher.domain.backup.FormOption;
import fr.vinted.republisher.domain.backup.FormOptions;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingFormField;
import fr.vinted.republisher.domain.republication.FieldNotFillable;
import fr.vinted.republisher.domain.republication.ListingFormUnreadable;
import fr.vinted.republisher.domain.republication.RepublicationVisit;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static fr.vinted.republisher.adapter.out.browser.ListingFormFixtures.ALL_DETAILS;
import static fr.vinted.republisher.adapter.out.browser.ListingFormFixtures.BRAND;
import static fr.vinted.republisher.adapter.out.browser.ListingFormFixtures.COLOR;
import static fr.vinted.republisher.adapter.out.browser.ListingFormFixtures.CONDITION;
import static fr.vinted.republisher.adapter.out.browser.ListingFormFixtures.LISTING_FORM;
import static fr.vinted.republisher.adapter.out.browser.ListingFormFixtures.MATERIAL;
import static fr.vinted.republisher.adapter.out.browser.ListingFormFixtures.PUBLISH_BUTTON;
import static fr.vinted.republisher.adapter.out.browser.ListingFormFixtures.SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AT-12-14: runs the installed Chrome against a local imitation of Vinted's listing form, as observed with the member
 * on 2026-11-15 (AT-12-18). Never the real site.
 */
@Tag("browser")
class PlaywrightListingPublisherTest {

    private static final Duration CONTROL_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration LOAD_TIMEOUT = Duration.ofSeconds(5);
    private static final DownloadedPhoto COVER = new DownloadedPhoto(new byte[]{1, 2, 3}, "image/jpeg");
    private static final DownloadedPhoto BACK = new DownloadedPhoto(new byte[]{1, 2, 3, 4, 5}, "image/webp");

    @RegisterExtension
    static final SharedChrome CHROME = new SharedChrome();

    private HttpServer fakeVinted;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> published = new CopyOnWriteArrayList<>();
    private volatile String details = ALL_DETAILS;
    private volatile String publishButton = PUBLISH_BUTTON;
    private volatile boolean redirectsToLogin;
    private volatile boolean leavesTheFormOncePublished = true;
    private volatile boolean lateBrandSuggestion;
    private volatile long uploadMillis = 300;
    private volatile Answer publicationAnswer = new Answer(200, "");
    private volatile boolean publicationDropped;
    private final RecordingVisit visit = new RecordingVisit();

    @BeforeEach
    void startFakeVinted() throws IOException {
        fakeVinted = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeVinted.createContext("/items/", exchange -> {
            record(exchange);
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/items/publish")) {
                published.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                if (publicationDropped) {
                    exchange.close();
                } else if (leavesTheFormOncePublished) {
                    respond(exchange, 200, "/items/77-pyjama-petit-bateau");
                } else {
                    respond(exchange, publicationAnswer.status(), publicationAnswer.body());
                }
            } else if (path.equals("/items/new") && redirectsToLogin) {
                exchange.getResponseHeaders().add("Location", "/member/login");
                respond(exchange, 302, "");
            } else if (path.equals("/items/new")) {
                respond(exchange, 200, LISTING_FORM.formatted(details, publishButton, lateBrandSuggestion, uploadMillis));
            } else {
                respond(exchange, 200, "<!DOCTYPE html><html><body>Pyjama petit bateau 3 mois</body></html>");
            }
        });
        fakeVinted.createContext("/telemetry", exchange -> {
            record(exchange);
            respond(exchange, 200, "datadome=secret-session-token");
        });
        fakeVinted.createContext("/", exchange -> {
            record(exchange);
            respond(exchange, 200, "<!DOCTYPE html><html><body>Connexion</body></html>");
        });
        fakeVinted.setExecutor(Executors.newCachedThreadPool());
        fakeVinted.start();
    }

    @AfterEach
    void stopFakeVinted() {
        fakeVinted.stop(0);
    }

    @Nested
    class Publication {

        @Test
        void fills_every_field_with_the_saved_values_over_vinteds_suggestions_then_publishes() {
            publisher().publish(pyjama(), List.of(COVER, BACK), visit);

            assertThat(published).containsExactly("""
                    {"photos":["photo-1.jpg:3","photo-2.webp:5"],"title":"Pyjama petit bateau 3 mois",\
                    "description":"Pyjama en coton\\nTrès doux","price":"12,50","category":1596,"brand":149,\
                    "size":614,"condition":2,"colors":[12,15],"materials":[44],"packageSize":2,"boost":false}""");
        }

        @Test
        void asks_the_visit_once_the_form_is_filled_and_right_before_publishing() {
            publisher().publish(pyjama(), List.of(COVER, BACK), visit);

            assertThat(visit.steps).containsExactly("ready to publish, nothing published yet");
        }

        @Test
        void never_touches_the_draft_nor_the_boost() {
            publisher().publish(pyjama(), List.of(COVER, BACK), visit);

            assertThat(clicks()).contains("click=upload-form-save-button")
                    .doesNotContain("click=upload-form-save-draft-button", "click=boost-checkbox");
        }

        @Test
        void listing_without_brand_is_put_on_sale_without_brand() {
            publisher().publish(withoutBrand(pyjama()), List.of(COVER), visit);

            assertThat(published).singleElement().asString().contains("\"brand\":0,");
        }

        @Test
        void form_still_shown_once_published_is_unreadable_and_tells_what_vinted_shows() {
            leavesTheFormOncePublished = false;

            assertThatThrownBy(() -> publisher().publish(pyjama(), List.of(COVER), visit))
                    .isInstanceOf(ListingFormUnreadable.class)
                    .hasMessageContaining("Le prix minimum est de 1,00 €");
            assertThat(published).hasSize(1);
        }

        @Test
        void form_still_shown_once_published_tells_how_vinted_answered_the_submission() {
            leavesTheFormOncePublished = false;
            publicationAnswer = new Answer(403, "{\"code\":\"datadome\"}");

            assertThatThrownBy(() -> publisher().publish(pyjama(), List.of(COVER), visit))
                    .isInstanceOf(ListingFormUnreadable.class)
                    .hasMessageContaining("POST /items/publish 403 {\"code\":\"datadome\"}")
                    .hasMessageNotContaining("GET ");
        }

        @Test
        void answers_of_third_parties_are_never_told_they_may_carry_tokens() {
            leavesTheFormOncePublished = false;

            assertThatThrownBy(() -> publisher().publish(pyjama(), List.of(COVER), visit))
                    .isInstanceOf(ListingFormUnreadable.class)
                    .hasMessageContaining("POST /items/publish 200")
                    .hasMessageNotContaining("telemetry")
                    .hasMessageNotContaining("datadome=");
        }

        @Test
        void submission_that_never_got_an_answer_is_told() {
            leavesTheFormOncePublished = false;
            publicationDropped = true;

            assertThatThrownBy(() -> publisher().publish(pyjama(), List.of(COVER), visit))
                    .isInstanceOf(ListingFormUnreadable.class)
                    .hasMessageContaining("POST /items/publish failed");
        }

        @Test
        void waits_for_the_photos_to_be_sent_before_publishing() {
            uploadMillis = 1_500;

            publisher().publish(pyjama(), List.of(COVER), visit);

            assertThat(published).hasSize(1);
        }

        @Test
        void photos_never_sent_cannot_be_filled_and_nothing_is_published() {
            uploadMillis = 60_000;

            assertThatThrownBy(() -> publisher().publish(pyjama(), List.of(COVER), visit))
                    .isInstanceOfSatisfying(FieldNotFillable.class,
                            notFillable -> assertThat(notFillable.field()).isEqualTo(ListingFormField.PHOTOS));
            assertThat(visit.steps).isEmpty();
            assertThat(published).isEmpty();
        }

        @Test
        void closes_its_tab() {
            publisher().publish(pyjama(), List.of(COVER), visit);

            assertThat(tabsOnFakeVinted()).isZero();
        }
    }

    @Nested
    class Dry_run {

        @Test
        void fills_the_whole_form_but_never_publishes() {
            visit.agreeing = false;

            publisher().publish(pyjama(), List.of(COVER, BACK), visit);

            assertThat(visit.steps).containsExactly("ready to publish, nothing published yet");
            assertThat(published).isEmpty();
            assertThat(clicks()).doesNotContain("click=upload-form-save-button",
                    "click=upload-form-save-draft-button", "click=boost-checkbox");
            assertThat(tabsOnFakeVinted()).isZero();
        }
    }

    @Nested
    class Before_publishing {

        @Test
        void category_not_found_by_its_identifier_cannot_be_filled() {
            assertNotFillable(withCategory(new FormOption(5000, "Pyjamas une pièce")),
                    ListingFormField.CATEGORY, Optional.of(5000L));
        }

        @Test
        void brand_not_found_by_its_identifier_cannot_be_filled() {
            assertNotFillable(withBrand(new FormOption(7, "Petit Bateau")), ListingFormField.BRAND, Optional.of(7L));
        }

        @Test
        void size_not_offered_cannot_be_filled() {
            assertNotFillable(withSize(new FormOption(588, "")), ListingFormField.SIZE, Optional.of(588L));
        }

        @Test
        void condition_not_offered_cannot_be_filled() {
            assertNotFillable(withCondition(new FormOption(9, "Neuf")), ListingFormField.CONDITION, Optional.of(9L));
        }

        @Test
        void colour_not_offered_cannot_be_filled() {
            assertNotFillable(withColors(new FormOptions(List.of(12L, 99L), "Blanc, Doré")),
                    ListingFormField.COLORS, Optional.of(99L));
        }

        @Test
        void material_not_offered_cannot_be_filled() {
            assertNotFillable(withMaterials(new FormOptions(List.of(77L), "Lin")),
                    ListingFormField.MATERIALS, Optional.of(77L));
        }

        @Test
        void package_size_not_offered_cannot_be_filled() {
            assertNotFillable(withPackageSize(new FormOption(4, "Très grand")),
                    ListingFormField.PACKAGE_SIZE, Optional.of(4L));
        }

        @Test
        void suggestion_of_vinted_replacing_a_filled_value_is_never_published() {
            lateBrandSuggestion = true;

            assertNotFillable(pyjama(), ListingFormField.BRAND, Optional.of(149L));
        }

        @Test
        void field_missing_from_the_form_cannot_be_filled() {
            details = BRAND + CONDITION + COLOR + MATERIAL;

            assertNotFillable(pyjama(), ListingFormField.SIZE, Optional.of(614L));
        }

        @Test
        void details_never_observed_cannot_be_filled_and_chrome_is_not_opened(@TempDir Path unusedProfile)
                throws IOException {
            // A Chrome never started, rather than the shared one already running: starting it would add a process.
            Chrome notStarted = new Chrome(SharedChrome.INSTALLED_CHROME, unusedProfile, SharedChrome.freePort());
            List<ProcessHandle> before = ProcessHandle.current().children().toList();
            ListingContent book = withDetails(new CategoryDetails(Optional.of("9782070612758"), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty()));

            assertThatThrownBy(() -> publisher(notStarted).publish(book, List.of(COVER), visit))
                    .isInstanceOfSatisfying(FieldNotFillable.class,
                            notFillable -> assertThat(notFillable.field()).isEqualTo(ListingFormField.ISBN));
            assertThat(requests).isEmpty();
            assertThat(ProcessHandle.current().children()).allMatch(before::contains);
        }

        @Test
        void form_without_publish_button_is_unreadable_and_nothing_is_published() {
            publishButton = "";

            assertThatThrownBy(() -> publisher().publish(pyjama(), List.of(COVER), visit))
                    .isInstanceOf(ListingFormUnreadable.class);
            assertThat(visit.steps).isEmpty();
        }

        @Test
        void form_with_two_publish_buttons_is_unreadable_and_nothing_is_published() {
            publishButton = PUBLISH_BUTTON + PUBLISH_BUTTON;

            assertThatThrownBy(() -> publisher().publish(pyjama(), List.of(COVER), visit))
                    .isInstanceOf(ListingFormUnreadable.class);
            assertThat(visit.steps).isEmpty();
        }

        @Test
        void login_page_means_the_session_is_disconnected() {
            redirectsToLogin = true;

            assertThatThrownBy(() -> publisher().publish(pyjama(), List.of(COVER), visit))
                    .isInstanceOf(SessionDisconnected.class);
            assertThat(visit.steps).isEmpty();
        }

        private void assertNotFillable(ListingContent content, ListingFormField field, Optional<Long> optionId) {
            assertThatThrownBy(() -> publisher().publish(content, List.of(COVER), visit))
                    .isInstanceOfSatisfying(FieldNotFillable.class, notFillable -> {
                        assertThat(notFillable.field()).isEqualTo(field);
                        assertThat(notFillable.optionId()).isEqualTo(optionId);
                    });
            assertThat(visit.steps).isEmpty();
            assertThat(published).isEmpty();
            assertThat(tabsOnFakeVinted()).isZero();
        }
    }

    private PlaywrightListingPublisher publisher() {
        return publisher(CHROME.chrome());
    }

    private PlaywrightListingPublisher publisher(Chrome chrome) {
        return new PlaywrightListingPublisher(URI.create(address("/items/new")), CONTROL_TIMEOUT, CONTROL_TIMEOUT,
                CONTROL_TIMEOUT, new ChromeTabs(chrome, LOAD_TIMEOUT));
    }

    private static ListingContent pyjama() {
        return new ListingContent(new VintedListingId(10208513136L), "Pyjama petit bateau 3 mois",
                "Pyjama en coton\nTrès doux", new Price(new BigDecimal("12.50"), "EUR"),
                new FormOption(1596, "Pyjamas une pièce"), Optional.of(new FormOption(149, "Petit Bateau")),
                new FormOption(2, "Très bon état"), Optional.of(new FormOption(614, "")),
                Optional.of(new FormOptions(List.of(12L, 15L), "Blanc, Multicolore")),
                Optional.of(new FormOptions(List.of(44L), "Coton")), new FormOption(2, "Moyen"),
                CategoryDetails.none());
    }

    private static ListingContent withCategory(FormOption category) {
        ListingContent p = pyjama();
        return new ListingContent(p.id(), p.title(), p.description(), p.price(), category, p.brand(), p.condition(),
                p.size(), p.colors(), p.materials(), p.packageSize(), p.details());
    }

    private static ListingContent withBrand(FormOption brand) {
        ListingContent p = pyjama();
        return new ListingContent(p.id(), p.title(), p.description(), p.price(), p.category(), Optional.of(brand),
                p.condition(), p.size(), p.colors(), p.materials(), p.packageSize(), p.details());
    }

    private static ListingContent withoutBrand(ListingContent p) {
        return new ListingContent(p.id(), p.title(), p.description(), p.price(), p.category(), Optional.empty(),
                p.condition(), p.size(), p.colors(), p.materials(), p.packageSize(), p.details());
    }

    private static ListingContent withCondition(FormOption condition) {
        ListingContent p = pyjama();
        return new ListingContent(p.id(), p.title(), p.description(), p.price(), p.category(), p.brand(), condition,
                p.size(), p.colors(), p.materials(), p.packageSize(), p.details());
    }

    private static ListingContent withSize(FormOption size) {
        ListingContent p = pyjama();
        return new ListingContent(p.id(), p.title(), p.description(), p.price(), p.category(), p.brand(),
                p.condition(), Optional.of(size), p.colors(), p.materials(), p.packageSize(), p.details());
    }

    private static ListingContent withColors(FormOptions colors) {
        ListingContent p = pyjama();
        return new ListingContent(p.id(), p.title(), p.description(), p.price(), p.category(), p.brand(),
                p.condition(), p.size(), Optional.of(colors), p.materials(), p.packageSize(), p.details());
    }

    private static ListingContent withMaterials(FormOptions materials) {
        ListingContent p = pyjama();
        return new ListingContent(p.id(), p.title(), p.description(), p.price(), p.category(), p.brand(),
                p.condition(), p.size(), p.colors(), Optional.of(materials), p.packageSize(), p.details());
    }

    private static ListingContent withPackageSize(FormOption packageSize) {
        ListingContent p = pyjama();
        return new ListingContent(p.id(), p.title(), p.description(), p.price(), p.category(), p.brand(),
                p.condition(), p.size(), p.colors(), p.materials(), packageSize, p.details());
    }

    private static ListingContent withDetails(CategoryDetails details) {
        ListingContent p = pyjama();
        return new ListingContent(p.id(), p.title(), p.description(), p.price(), p.category(), p.brand(),
                p.condition(), p.size(), p.colors(), p.materials(), p.packageSize(), details);
    }

    private List<String> clicks() {
        return requests.stream().filter(request -> request.startsWith("GET /interaction?click="))
                .map(request -> request.substring("GET /interaction?".length())).toList();
    }

    private long tabsOnFakeVinted() {
        try (Playwright playwright = Playwright.create();
             Browser chrome = playwright.chromium().connectOverCDP(CHROME.endpoint())) {
            return chrome.contexts().getFirst().pages().stream()
                    .filter(page -> page.url().startsWith(address(""))).count();
        }
    }

    private String address(String path) {
        return "http://127.0.0.1:" + fakeVinted.getAddress().getPort() + path;
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

    private record Answer(int status, String body) {
    }

    /** Records when it was asked, and whether anything had already been published then. */
    private final class RecordingVisit implements RepublicationVisit {

        private final List<String> steps = new CopyOnWriteArrayList<>();
        private boolean agreeing = true;

        @Override
        public boolean readyToPublish() {
            steps.add(published.isEmpty() ? "ready to publish, nothing published yet" : "ready, already published");
            return agreeing;
        }
    }
}
