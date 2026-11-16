package fr.vinted.republisher.adapter.in.web;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Clock;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The static page of a listing (its detail, then its backup) in the installed Chrome, against a simulated API
 * answering as the contract says: never the real application nor Vinted.
 */
@Tag("browser")
class ListingDetailPageTest {

    private static final String DETAIL = """
            {"listing": {"vintedId": 3, "title": "Pull <b>rayé</b>", "price": {"amount": 12.5, "currency": "EUR"},
                         "url": "https://vinted.test/items/3", "present": true},
             "lastSeenAt": "2026-11-03T09:00:00Z",
             "journal": [],
             "statisticsHistory": [
               {"viewCount": 12, "favouriteCount": 3, "readAt": "2026-11-02T09:00:00Z"},
               {"viewCount": 5, "favouriteCount": 1, "readAt": "2026-11-01T09:00:00Z"}]}""";
    private static final String NEVER_READ = """
            {"listing": {"vintedId": 3, "title": "Jupe", "price": {"amount": 3, "currency": "EUR"},
                         "url": "https://vinted.test/items/3", "present": true},
             "lastSeenAt": "2026-11-03T09:00:00Z", "statisticsHistory": [], "journal": []}""";
    private static final String ABSENT = NEVER_READ.replace("\"present\": true", "\"present\": false");

    private static final String VERIFIED = """
            {"vintedId": 3, "savedAt": "2026-11-10T10:00:00Z", "deletionDryRun": true,
             "verification": {"passed": true, "failures": []},
             "content": {"title": "Pull <b>rayé</b>", "description": "Porté deux fois",
                         "price": {"amount": 12.5, "currency": "EUR"},
                         "category": "Pulls", "brand": "Kiabi", "condition": "Très bon état", "size": "M",
                         "colors": "Blanc, Bleu", "packageSize": "Petit", "isbn": "9782070612758"},
             "photos": [{"rank": 0, "url": "/api/listings/3/backup/photos/0"},
                        {"rank": 1, "url": "/api/listings/3/backup/photos/1"}]}""";
    private static final String VALIDATED = VERIFIED.replace("\"savedAt\": \"2026-11-10T10:00:00Z\",",
            "\"savedAt\": \"2026-11-10T10:00:00Z\", \"validatedAt\": \"2026-11-11T09:00:00Z\",");
    private static final String FAILED_VERIFICATION = """
            {"passed": false, "failures": [
              {"kind": "NO_PHOTO"},
              {"kind": "PHOTO_MISSING", "rank": 1},
              {"kind": "PHOTO_ALTERED", "rank": 0},
              {"kind": "LISTING_ABSENT"},
              {"kind": "TITLE_CHANGED", "savedTitle": "Robe bleue", "scannedTitle": "Robe bleue M"},
              {"kind": "PRICE_CHANGED", "savedPrice": {"amount": 12, "currency": "EUR"},
                                        "scannedPrice": {"amount": 10, "currency": "EUR"}}]}""";
    private static final String NOT_VERIFIED = VERIFIED.replace("{\"passed\": true, \"failures\": []}",
            FAILED_VERIFICATION);

    private static Playwright playwright;
    private static Browser chrome;

    private HttpServer fakeApi;
    private final AtomicReference<String> detail = new AtomicReference<>(DETAIL);
    private final AtomicInteger detailStatus = new AtomicInteger(200);
    private final List<String> backupReads = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> backup = new AtomicReference<>(VERIFIED);
    private final AtomicInteger backupStatus = new AtomicInteger(200);
    private final AtomicInteger validationStatus = new AtomicInteger(200);
    private final AtomicReference<String> validationAnswer = new AtomicReference<>(VALIDATED);
    private final List<String> validations = new CopyOnWriteArrayList<>();
    private final AtomicInteger deletionStatus = new AtomicInteger(200);
    private final AtomicReference<String> deletionAnswer = new AtomicReference<>(VALIDATED);
    private final AtomicInteger recheckStatus = new AtomicInteger(200);
    private final AtomicReference<String> recheckAnswer = new AtomicReference<>(VALIDATED);
    private final List<String> deletions = new CopyOnWriteArrayList<>();
    private final AtomicInteger republicationStatus = new AtomicInteger(200);
    private final AtomicReference<String> republicationAnswer = new AtomicReference<>(VALIDATED);
    private final List<String> republications = new CopyOnWriteArrayList<>();
    private final AtomicInteger verificationStatus = new AtomicInteger(200);
    private final AtomicReference<String> verificationAnswer = new AtomicReference<>(VALIDATED);
    private final List<String> verifications = new CopyOnWriteArrayList<>();
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
        fakeApi.createContext("/listing.html", exchange -> {
            try (InputStream html = getClass().getResourceAsStream("/static/listing.html")) {
                respond(exchange, 200, "text/html; charset=utf-8", html.readAllBytes());
            }
        });
        fakeApi.createContext("/journal.js", exchange -> {
            try (InputStream script = getClass().getResourceAsStream("/static/journal.js")) {
                respond(exchange, 200, "text/javascript; charset=utf-8", script.readAllBytes());
            }
        });
        fakeApi.createContext("/api/listings/abc", exchange -> respond(exchange, 400, "text/plain", new byte[0]));
        fakeApi.createContext("/api/listings/999", exchange -> respond(exchange, 404, "text/plain", new byte[0]));
        fakeApi.createContext("/api/listings/3", exchange ->
                respond(exchange, detailStatus.get(), "application/json", bytes(detail.get())));
        fakeApi.createContext("/api/listings/3/backup", exchange -> {
            backupReads.add(exchange.getRequestMethod());
            respond(exchange, backupStatus.get(), "application/json", bytes(backup.get()));
        });
        fakeApi.createContext("/api/listings/3/backup/photos/", exchange ->
                respond(exchange, 200, "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'}));
        fakeApi.createContext("/api/listings/3/backup/validation", exchange -> {
            validations.add(exchange.getRequestMethod() + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, validationStatus.get(), "application/json", bytes(validationAnswer.get()));
        });
        fakeApi.createContext("/api/listings/3/deletion", exchange -> {
            deletions.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            boolean recheck = exchange.getRequestURI().getPath().endsWith("/recheck");
            respond(exchange, recheck ? recheckStatus.get() : deletionStatus.get(), "application/json",
                    bytes(recheck ? recheckAnswer.get() : deletionAnswer.get()));
        });
        fakeApi.createContext("/api/listings/3/republication", exchange -> {
            republications.add(exchange.getRequestMethod() + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, republicationStatus.get(), "application/json", bytes(republicationAnswer.get()));
        });
        fakeApi.createContext("/api/listings/3/republication/verification", exchange -> {
            verifications.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            respond(exchange, verificationStatus.get(), "application/json", bytes(verificationAnswer.get()));
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
    void shows_a_verified_backup_with_its_photos_and_labels() {
        // Étant donné une sauvegarde dont la vérification est réussie
        // Quand j'ouvre son aperçu
        open();
        // Alors je vois « vérification réussie », ses photos dans l'ordre et tout son contenu par ses libellés
        assertThat(verification()).containsText("Vérification réussie");
        assertThat(page.locator("#photos img")).hasCount(2);
        assertThat(page.locator("#photos img").nth(0)).hasAttribute("src", "/api/listings/3/backup/photos/0");
        assertThat(page.locator("#photos img").nth(1)).hasAttribute("src", "/api/listings/3/backup/photos/1");
        assertThat(content()).containsText("Pull <b>rayé</b>");
        assertThat(content()).containsText("Porté deux fois");
        assertThat(content()).containsText("12,50");
        assertThat(content()).containsText("Kiabi");
        assertThat(content()).containsText("Blanc, Bleu");
        assertThat(content()).containsText("9782070612758");
        assertThat(content()).not().containsText("Modèle");
        assertThat(page.locator("#saved-at")).containsText("10/11/2026");
        assertThat(validateButton()).isEnabled();
    }

    @Test
    void member_validates_the_version_seen() {
        // Étant donné l'aperçu d'une sauvegarde dont la vérification est réussie
        open();
        // Quand je clique sur « Valider »
        validateButton().click();
        // Alors la sauvegarde est validée, avec la date de validation
        assertThat(page.locator("#validated-at")).containsText("Validée le 11/11/2026");
        assertThat(validateButton()).isHidden();
        assertThat(validations).containsExactly("PUT {\"savedAt\":\"2026-11-10T10:00:00Z\"}");
    }

    @Test
    void validated_backup_offers_no_validation() {
        backup.set(VALIDATED);

        open();

        assertThat(page.locator("#validated-at")).containsText("Validée le 11/11/2026");
        assertThat(validateButton()).isHidden();
    }

    @Test
    void failed_verification_lists_its_failures_and_cannot_be_validated() {
        // Étant donné une sauvegarde dont la vérification échoue
        backup.set(NOT_VERIFIED);
        // Quand j'ouvre son aperçu
        open();
        // Alors l'aperçu affiche ses échecs et un bouton « Valider » inactif
        assertThat(failures()).hasCount(6);
        assertThat(failures().nth(0)).containsText("aucune photo");
        assertThat(failures().nth(1)).containsText("photo 1 manquante");
        assertThat(failures().nth(2)).containsText("photo 0 modifiée");
        assertThat(failures().nth(3)).containsText("annonce absente du dressing");
        assertThat(failures().nth(4)).containsText("« Robe bleue »");
        assertThat(failures().nth(4)).containsText("« Robe bleue M »");
        assertThat(failures().nth(5)).containsText("12,00");
        assertThat(failures().nth(5)).containsText("10,00");
        assertThat(validateButton()).isDisabled();
    }

    @Test
    void replaced_backup_asks_to_look_again() {
        open();
        validationStatus.set(409);
        validationAnswer.set("{\"reason\": \"BACKUP_REPLACED\"}");

        validateButton().click();

        assertThat(problem()).containsText("remplacée");
        assertThat(page.locator("#validated-at")).isEmpty();
        assertThat(validateButton()).isDisabled();
    }

    @Test
    void verification_failed_at_validation_shows_the_failures() {
        open();
        validationStatus.set(422);
        validationAnswer.set("{\"reason\": \"VERIFICATION_FAILED\", \"verification\": " + FAILED_VERIFICATION + "}");

        validateButton().click();

        assertThat(failures()).hasCount(6);
        assertThat(validateButton()).isDisabled();
    }

    @Test
    void shows_listing_history_and_backup() {
        // Étant donné l'annonce 3 présente, relevée à 5 vues et 1 favori le 1er novembre puis 12 vues et 3 favoris
        //   le 2, et sauvegardée
        // Quand le membre ouvre sa fiche
        open();
        // Alors il voit son titre, son prix, son lien Vinted et sa présence
        assertThat(page.locator("#listing-title")).hasText("Pull <b>rayé</b>");
        assertThat(page.locator("#listing-price")).containsText("12,50");
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Voir sur Vinted")))
                .hasAttribute("href", "https://vinted.test/items/3");
        assertThat(page.locator("#presence")).hasText("Présente dans le dressing.");
        //   et les relevés du 2 (12 vues, 3 favoris) puis du 1er (5 vues, 1 favori)
        assertThat(historyRows()).hasCount(2);
        assertThat(historyRows().nth(0).locator("td")).hasText(new String[]{"02/11/2026 10:00:00", "12", "3"});
        assertThat(historyRows().nth(1).locator("td")).hasText(new String[]{"01/11/2026 10:00:00", "5", "1"});
        assertThat(page.locator("#no-reading")).isHidden();
        //   et la section de sa sauvegarde
        assertThat(verification()).containsText("Vérification réussie");
        assertThat(page.locator("#not-backed-up")).isHidden();
    }

    @Test
    void listing_never_read_nor_backed_up() {
        // Étant donné l'annonce 3 présente, jamais relevée ni sauvegardée
        detail.set(NEVER_READ);
        backupStatus.set(404);
        backup.set("");
        // Quand le membre ouvre sa fiche
        open();
        // Alors il voit « Aucun relevé » et « Non sauvegardée », sans bouton
        assertThat(page.locator("#no-reading")).hasText("Aucun relevé");
        assertThat(page.locator("#history")).isHidden();
        assertThat(page.locator("#not-backed-up")).hasText("Non sauvegardée");
        assertThat(page.locator("#backup")).isHidden();
        assertThat(problem()).isHidden();
        assertThat(page.getByRole(AriaRole.BUTTON)).hasCount(0);
    }

    @Test
    void absent_listing() {
        // Étant donné l'annonce 3 vue au scan du 3 novembre puis absente
        detail.set(ABSENT);
        // Quand le membre ouvre sa fiche
        open();
        // Alors il voit « absente du dressing, vue pour la dernière fois le 03/11/2026 »
        assertThat(page.locator("#presence")).containsText("Absente du dressing, vue pour la dernière fois le 03/11/2026");
    }

    @Test
    void shows_the_stage() {
        // Étant donné une annonce « À republier »
        detail.set(ABSENT.replace("\"present\": false", "\"present\": false, \"stage\": \"TO_REPUBLISH\""));
        // Quand le membre ouvre sa fiche
        open();
        // Alors l'étape « À republier » est affichée sous le titre
        assertThat(page.locator("#stage")).hasText("Étape : À republier");
    }

    @Test
    void shows_the_journal_of_the_listing() {
        // US-23 AT-23-14. Étant donné une annonce avec une validation et une suppression en dry-run
        detail.set(NEVER_READ.replace("\"journal\": []", """
                "journal": [
                  {"entryId": 2, "occurredAt": "2026-11-15T10:01:00Z", "action": "DELETION", "outcome": "DRY_RUN",
                   "listingId": 3, "listingTitle": "Jupe"},
                  {"entryId": 1, "occurredAt": "2026-11-15T10:00:00Z", "action": "BACKUP_VALIDATION",
                   "outcome": "SUCCEEDED", "listingId": 3, "listingTitle": "Jupe"}]"""));
        // Quand le membre ouvre sa fiche
        open();
        // Alors la section « Journal » montre ces deux lignes
        assertThat(page.locator("#listing-journal tbody tr")).hasCount(2);
        assertThat(page.locator("#listing-journal tbody tr").nth(0)).containsText("Suppression");
        assertThat(page.locator("#listing-journal tbody tr").nth(0)).containsText("dry-run réussi");
        assertThat(page.locator("#listing-journal tbody tr").nth(1)).containsText("Validation");
        assertThat(page.locator("#no-journal")).isHidden();
    }

    @Test
    void unknown_listing() {
        // Quand le membre ouvre listing.html?id=999, ou listing.html sans identifiant, ou avec un identifiant invalide
        // Alors il voit « Annonce inconnue. » et le lien de retour à la liste
        for (String query : List.of("?id=999", "", "?id=abc")) {
            page.navigate("http://127.0.0.1:" + fakeApi.getAddress().getPort() + "/listing.html" + query);

            assertThat(problem()).hasText("Annonce inconnue.");
            assertThat(page.locator("#listing")).isHidden();
            assertThat(page.locator("#backup")).isHidden();
            assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Retour à la liste")))
                    .hasAttribute("href", "wardrobe.html");
        }
    }

    @Test
    void shows_the_title_as_text_never_as_html() {
        open();

        assertThat(page.locator("#listing-title")).hasText("Pull <b>rayé</b>");
        assertThat(page.locator("#listing-title b")).hasCount(0);
        assertThat(page).hasTitle("Pull <b>rayé</b>");
    }

    @Test
    void application_down_shows_neither_the_listing_nor_its_backup() {
        // Étant donné la base indisponible
        detailStatus.set(500);
        detail.set("");
        // Quand le membre ouvre la fiche
        open();
        // Alors il voit « L'application ne répond pas » et rien de l'annonce, sans lire sa sauvegarde
        assertThat(problem()).containsText("L'application ne répond pas");
        assertThat(page.locator("#listing")).isHidden();
        assertThat(page.locator("#backup")).isHidden();
        assertThat(backupReads).isEmpty();
    }

    @Test
    void links_back_to_the_list() {
        open();

        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Retour à la liste")))
                .hasAttribute("href", "wardrobe.html");
    }

    @Nested
    class Deletion {

        private static final String UNCONFIRMED = VALIDATED.replace("\"deletionDryRun\": true,",
                "\"deletionDryRun\": false, \"deletion\": {\"outcome\": \"UNCONFIRMED\","
                        + " \"attemptedAt\": \"2026-11-11T10:00:00Z\", \"backupSavedAt\": \"2026-11-10T10:00:00Z\"},");
        private static final String CONFIRMED = VALIDATED.replace("\"deletionDryRun\": true,",
                "\"deletionDryRun\": false, \"deletion\": {\"outcome\": \"CONFIRMED\","
                        + " \"attemptedAt\": \"2026-11-11T10:00:00Z\", \"settledAt\": \"2026-11-11T10:00:05Z\","
                        + " \"backupSavedAt\": \"2026-11-10T10:00:00Z\"},");
        private static final String REPUBLISHABLE_AT_10_05_05 = CONFIRMED.replace(
                "\"backupSavedAt\": \"2026-11-10T10:00:00Z\"},",
                "\"backupSavedAt\": \"2026-11-10T10:00:00Z\", \"republishableAt\": \"2026-11-11T10:05:05Z\"},");

        @Test
        void shows_the_mode_deletions_run_in() {
            // Étant donné le dry-run actif, puis les suppressions réelles activées
            // Quand j'ouvre l'aperçu d'une sauvegarde
            // Alors je vois « Dry-run actif : aucune suppression réelle », puis « Suppressions réelles activées »
            open();
            assertThat(mode()).hasText("Dry-run actif : aucune suppression réelle");

            backup.set(VERIFIED.replace("\"deletionDryRun\": true", "\"deletionDryRun\": false"));
            open();
            assertThat(mode()).hasText("Suppressions réelles activées");
        }

        @Test
        void only_a_validated_and_verified_backup_can_be_deleted() {
            open();
            assertThat(deleteButton()).isDisabled();

            backup.set(VALIDATED.replace("{\"passed\": true, \"failures\": []}", FAILED_VERIFICATION));
            open();
            assertThat(deleteButton()).isDisabled();

            backup.set(VALIDATED);
            open();
            assertThat(deleteButton()).isEnabled();
        }

        @Test
        void asks_for_a_confirmation_recalling_the_listing_before_deleting() {
            // Quand je clique sur « Supprimer de Vinted »
            backup.set(VALIDATED);
            open();
            deleteButton().click();
            // Alors une confirmation rappelle titre, prix, nombre de photos, date de validation et « irréversible »
            assertThat(confirmation()).isVisible();
            assertThat(confirmation()).containsText("Pull <b>rayé</b>");
            assertThat(confirmation()).containsText("12,50");
            assertThat(confirmation()).containsText("2 photos");
            assertThat(confirmation()).containsText("11/11/2026");
            assertThat(confirmation()).containsText("irréversible");
            assertThat(deletions).isEmpty();
        }

        @Test
        void dry_run_says_nothing_was_deleted() {
            // Étant donné le dry-run actif
            backup.set(VALIDATED);
            deletionAnswer.set(VALIDATED.replace("\"deletionDryRun\": true,",
                    "\"deletionDryRun\": true, \"dryRunPassed\": true,"));
            open();
            // Quand je confirme « Supprimer définitivement »
            deleteButton().click();
            confirmButton().click();
            // Alors l'aperçu affiche « Dry-run réussi … Rien n'a été supprimé. »
            assertThat(deletionState()).containsText("Dry-run réussi");
            assertThat(deletionState()).containsText("Rien n'a été supprimé.");
            assertThat(deletions).containsExactly(
                    "POST /api/listings/3/deletion {\"savedAt\":\"2026-11-10T10:00:00Z\"}");
        }

        @Test
        void listing_changed_on_vinted_tells_how() {
            backup.set(VALIDATED);
            deletionStatus.set(422);
            deletionAnswer.set("""
                    {"reason": "LISTING_CHANGED", "differences": [{"kind": "PRICE_CHANGED",
                      "savedPrice": {"amount": 12, "currency": "EUR"}, "readPrice": {"amount": 10, "currency": "EUR"}}]}""");
            open();

            deleteButton().click();
            confirmButton().click();

            assertThat(problem()).containsText("rien n'a été supprimé");
            assertThat(problem()).containsText("12,00");
            assertThat(problem()).containsText("10,00");
        }

        @Test
        void unconfirmed_deletion_asks_to_check_on_vinted() {
            // Étant donné une suppression non confirmée
            backup.set(UNCONFIRMED);
            recheckAnswer.set(CONFIRMED);
            open();
            // Alors l'aperçu affiche « suppression non confirmée, vérifier sur Vinted » avec « Revérifier sur Vinted »
            assertThat(deletionState()).containsText("Suppression non confirmée, vérifier sur Vinted");
            assertThat(deleteButton()).isDisabled();
            // Quand je clique sur « Revérifier sur Vinted »
            recheckButton().click();
            assertThat(deletionState()).containsText("Supprimée de Vinted le 11/11/2026");
            assertThat(deletions).containsExactly("POST /api/listings/3/deletion/recheck ");
        }

        @Test
        void failed_recheck_says_the_doubt_remains() {
            backup.set(UNCONFIRMED);
            recheckStatus.set(502);
            recheckAnswer.set("{\"reason\": \"VINTED_UNREACHABLE\"}");
            open();

            recheckButton().click();

            assertThat(problem()).containsText("le doute reste");
            assertThat(problem()).containsText("Vinted est inaccessible");
            assertThat(problem()).not().containsText("rien n'a été supprimé");
            assertThat(recheckButton()).isEnabled();
        }

        @Test
        void unexpected_deletion_failure_shows_the_state_saved_since_without_claiming_nothing_was_deleted() {
            backup.set(VALIDATED);
            deletionStatus.set(500);
            deletionAnswer.set("{\"reason\": \"DELETION_FAILED\"}");
            open();
            backup.set(UNCONFIRMED);

            deleteButton().click();
            confirmButton().click();

            assertThat(problem()).containsText("vérifiez l'annonce sur Vinted");
            assertThat(problem()).not().containsText("rien n'a été supprimé");
            assertThat(deletionState()).containsText("Suppression non confirmée, vérifier sur Vinted");
            assertThat(recheckButton()).isVisible();
        }

        @Test
        void confirmed_deletion_says_when() {
            backup.set(CONFIRMED);

            open();

            assertThat(deletionState()).containsText("Supprimée de Vinted le 11/11/2026");
            assertThat(deleteButton()).isHidden();
            assertThat(recheckButton()).isHidden();
        }

        @Test
        void showsRemainingTimeUntilRepublication() {
            // Étant donné un aperçu dont la suppression est republiable dans 2 secondes
            backup.set(REPUBLISHABLE_AT_10_05_05);
            page.clock().install(new Clock.InstallOptions().setTime("2026-11-11T10:05:03Z"));
            // Quand la page est ouverte
            open();
            // Alors elle affiche « Republication possible dans 0 min 2 s (à 11:05) », heure de Paris
            assertThat(republicationState()).hasText("Republication possible dans 0 min 2 s (à 11:05).");
            // Et après 2 secondes, sans recharger, elle affiche « Republication possible »
            page.clock().runFor(2000);
            assertThat(republicationState()).hasText("Republication possible.");
        }

        @Test
        void showsRepublicationPossibleOnceDelayElapsed() {
            // Et une suppression republiable depuis 10 minutes affiche directement « Republication possible »
            backup.set(REPUBLISHABLE_AT_10_05_05);
            page.clock().install(new Clock.InstallOptions().setTime("2026-11-11T10:15:05Z"));

            open();

            assertThat(republicationState()).hasText("Republication possible.");
        }

        @Test
        void says_nothing_about_republication_without_a_confirmed_deletion() {
            backup.set(UNCONFIRMED);

            open();

            assertThat(deletionState()).containsText("non confirmée");
            assertThat(republicationState()).isEmpty();
        }

        private Locator republicationState() {
            return page.locator("#republication-state");
        }

        private Locator mode() {
            return page.locator("#deletion-mode");
        }

        private Locator deletionState() {
            return page.locator("#deletion-state");
        }

        private Locator confirmation() {
            return page.locator("#deletion-confirmation");
        }

        private Locator deleteButton() {
            return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Supprimer de Vinted"));
        }

        private Locator confirmButton() {
            return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Supprimer définitivement"));
        }

        private Locator recheckButton() {
            return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Revérifier sur Vinted"));
        }
    }

    @Nested
    class Republication {

        private static final String REPUBLISHABLE = VALIDATED.replace("\"deletionDryRun\": true,",
                "\"deletionDryRun\": false, \"republicationDryRun\": false, \"deletion\": {\"outcome\": \"CONFIRMED\","
                        + " \"attemptedAt\": \"2026-11-11T10:00:00Z\", \"settledAt\": \"2026-11-11T10:00:05Z\","
                        + " \"backupSavedAt\": \"2026-11-10T10:00:00Z\", \"republishableAt\": \"2026-11-11T10:05:05Z\"},");
        private static final String PUBLISHED = REPUBLISHABLE.replace("\"republicationDryRun\": false,",
                "\"republicationDryRun\": false, \"republication\": {\"outcome\": \"PUBLISHED\","
                        + " \"attemptedAt\": \"2026-11-11T10:06:00Z\", \"publishedAt\": \"2026-11-11T10:06:20Z\","
                        + " \"newListingId\": 10300000001,"
                        + " \"newListingUrl\": \"https://www.vinted.fr/items/10300000001-pull\","
                        + " \"backupSavedAt\": \"2026-11-10T10:00:00Z\"},");
        private static final String FAILED = PUBLISHED.replace("\"outcome\": \"PUBLISHED\"", "\"outcome\": \"FAILED\"")
                .replace(" \"publishedAt\": \"2026-11-11T10:06:20Z\", \"newListingId\": 10300000001,"
                        + " \"newListingUrl\": \"https://www.vinted.fr/items/10300000001-pull\",", "");
        private static final String NOT_PUBLISHED = FAILED.replace("\"outcome\": \"FAILED\"",
                "\"outcome\": \"NOT_PUBLISHED\", \"checkedAt\": \"2026-11-12T09:00:00Z\"");

        @Test
        void shows_the_mode_republications_run_in() {
            backup.set(REPUBLISHABLE.replace("\"republicationDryRun\": false", "\"republicationDryRun\": true"));
            open();
            assertThat(mode()).hasText("Dry-run actif : aucune republication réelle");

            backup.set(REPUBLISHABLE);
            open();
            assertThat(mode()).hasText("Republications réelles activées");
        }

        @Test
        void republish_is_offered_only_from_the_republication_date() {
            backup.set(VALIDATED);
            open();
            assertThat(republishButton()).isHidden();

            backup.set(REPUBLISHABLE);
            page.clock().install(new Clock.InstallOptions().setTime("2026-11-11T10:05:03Z"));
            open();
            assertThat(republishButton()).isDisabled();
            page.clock().runFor(2000);
            assertThat(republishButton()).isEnabled();
        }

        @Test
        void pageShowsRepublicationFlow() {
            // Étant donné une annonce supprimée, republiable
            backup.set(REPUBLISHABLE);
            republicationAnswer.set(PUBLISHED);
            page.clock().install(new Clock.InstallOptions().setTime("2026-11-11T10:06:00Z"));
            open();
            // Quand le membre clique « Republier sur Vinted »
            republishButton().click();
            // Alors la confirmation montre titre, prix, nombre de photos et date de suppression
            assertThat(confirmation()).isVisible();
            assertThat(confirmation()).containsText("Pull <b>rayé</b>");
            assertThat(confirmation()).containsText("12,50");
            assertThat(confirmation()).containsText("2 photos");
            assertThat(confirmation()).containsText("supprimée de Vinted le 11/11/2026");
            assertThat(republications).isEmpty();
            // Et « Republier maintenant » envoie la demande avec la version vue, puis affiche l'issue
            confirmButton().click();
            assertThat(outcome()).containsText("Republiée sur Vinted le 11/11/2026");
            assertThat(outcome().getByRole(AriaRole.LINK)).hasAttribute("href",
                    "https://www.vinted.fr/items/10300000001-pull");
            assertThat(republications).containsExactly("POST {\"savedAt\":\"2026-11-10T10:00:00Z\"}");
            assertThat(republishButton()).isHidden();
            // Et l'état du dry-run est affiché en permanence
            assertThat(mode()).hasText("Republications réelles activées");
        }

        @Test
        void republication_in_error_asks_to_check_on_vinted() {
            backup.set(FAILED);

            open();

            assertThat(outcome()).hasText("Erreur de republication : vérifiez sur Vinted avant toute relance.");
            assertThat(republishButton()).isHidden();
            assertThat(verifyButton()).isVisible();
            assertThat(page.locator("#republication-state")).isEmpty();
        }

        @Test
        void dry_run_says_nothing_was_published() {
            backup.set(REPUBLISHABLE);
            republicationAnswer.set(REPUBLISHABLE.replace("\"republicationDryRun\": false,",
                    "\"republicationDryRun\": true, \"republicationDryRunPassed\": true,"));
            page.clock().install(new Clock.InstallOptions().setTime("2026-11-11T10:06:00Z"));
            open();

            republishButton().click();
            confirmButton().click();

            assertThat(outcome()).containsText("Dry-run réussi");
            assertThat(outcome()).containsText("Rien n'a été publié.");
        }

        @Test
        void field_not_fillable_names_the_field() {
            backup.set(REPUBLISHABLE);
            republicationStatus.set(422);
            republicationAnswer.set("{\"reason\": \"FIELD_NOT_FILLABLE\", \"field\": \"SIZE\", \"optionId\": 588}");
            page.clock().install(new Clock.InstallOptions().setTime("2026-11-11T10:06:00Z"));
            open();

            republishButton().click();
            confirmButton().click();

            assertThat(problem()).hasText(
                    "Republication refusée, rien n'a été publié : Taille impossible à remplir (identifiant 588).");
            assertThat(republishButton()).isEnabled();
        }

        @Test
        void unexpected_failure_asks_to_check_on_vinted_without_claiming_nothing_was_published() {
            backup.set(REPUBLISHABLE);
            republicationStatus.set(500);
            republicationAnswer.set("{\"reason\": \"REPUBLICATION_FAILED\"}");
            page.clock().install(new Clock.InstallOptions().setTime("2026-11-11T10:06:00Z"));
            open();

            republishButton().click();
            confirmButton().click();

            assertThat(problem()).containsText("vérifiez sur Vinted avant de relancer");
            assertThat(problem()).not().containsText("rien n'a été publié");
        }

        @Test
        void failed_republication_is_verified_and_can_be_republished_once_not_published() {
            // Étant donné une republication en erreur
            backup.set(FAILED);
            verificationAnswer.set(NOT_PUBLISHED);
            page.clock().install(new Clock.InstallOptions().setTime("2026-11-11T10:06:00Z"));
            open();
            assertThat(republishButton()).isHidden();
            // Quand le membre clique sur « Vérifier sur Vinted »
            verifyButton().click();
            // Alors l'issue est affichée et « Republier sur Vinted » redevient possible
            assertThat(outcome()).containsText("Republication non faite : rien trouvé sur Vinted");
            assertThat(outcome()).containsText("vérifié le 12/11/2026");
            assertThat(verifications).containsExactly("POST /api/listings/3/republication/verification");
            assertThat(republications).isEmpty();
            assertThat(republishButton()).isVisible();
            assertThat(republishButton()).isEnabled();
            assertThat(verifyButton()).isHidden();
        }

        @Test
        void several_candidates_ask_to_remove_the_duplicates() {
            backup.set(FAILED);
            verificationAnswer.set(FAILED);
            open();

            verifyButton().click();

            assertThat(problem()).hasText(
                    "Plusieurs annonces correspondent sur Vinted : supprimez les doublons puis revérifiez.");
            assertThat(verifyButton()).isEnabled();
        }

        @Test
        void published_republication_shows_its_differences_as_text() {
            backup.set(PUBLISHED);
            verificationAnswer.set(PUBLISHED.replace("\"republicationDryRun\": false,",
                    "\"republicationDryRun\": false, \"republicationComparison\": [{\"field\": \"SIZE\","
                            + " \"saved\": \"1-3 mois (613)\", \"read\": \"<b>3-6 mois</b> (614)\"}],"));
            open();
            assertThat(verifyButton()).isVisible();

            verifyButton().click();

            assertThat(comparison()).hasText("Différences avec la sauvegarde, rien n'a été corrigé : Taille"
                    + " « 1-3 mois (613) » sauvegardé, « <b>3-6 mois</b> (614) » lu sur Vinted.");
            assertThat(outcome()).containsText("Republiée sur Vinted le 11/11/2026");
        }

        @Test
        void identical_new_listing_says_so() {
            backup.set(PUBLISHED);
            verificationAnswer.set(PUBLISHED.replace("\"republicationDryRun\": false,",
                    "\"republicationDryRun\": false, \"republicationComparison\": [],"));
            open();

            verifyButton().click();

            assertThat(comparison()).hasText("Nouvelle annonce identique à la sauvegarde.");
        }

        @Test
        void refused_verification_says_nothing_changed() {
            backup.set(PUBLISHED);
            verificationStatus.set(502);
            verificationAnswer.set("{\"reason\": \"LISTING_UNREADABLE\"}");
            open();

            verifyButton().click();

            assertThat(problem()).hasText("Vérification interrompue, l'état affiché est celui enregistré : la page"
                    + " de la nouvelle annonce sur Vinted n'est pas celle attendue.");
            assertThat(comparison()).isEmpty();
        }

        private Locator verifyButton() {
            return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Vérifier sur Vinted"));
        }

        private Locator comparison() {
            return page.locator("#republication-comparison");
        }

        private Locator mode() {
            return page.locator("#republication-mode");
        }

        private Locator outcome() {
            return page.locator("#republication-outcome");
        }

        private Locator confirmation() {
            return page.locator("#republication-confirmation");
        }

        private Locator republishButton() {
            return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Republier sur Vinted"));
        }

        private Locator confirmButton() {
            return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Republier maintenant"));
        }
    }

    /** The page reads the listing, then its backup: both are read once it returns. */
    private void open() {
        page.navigate("http://127.0.0.1:" + fakeApi.getAddress().getPort() + "/listing.html?id=3",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
    }

    private Locator verification() {
        return page.locator("#verification");
    }

    private Locator historyRows() {
        return page.locator("#history tbody tr");
    }

    private Locator failures() {
        return page.locator("#verification li");
    }

    private Locator content() {
        return page.locator("#content");
    }

    private Locator problem() {
        return page.locator("#problem");
    }

    private Locator validateButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Valider"));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        if (body.length == 0) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }
}
