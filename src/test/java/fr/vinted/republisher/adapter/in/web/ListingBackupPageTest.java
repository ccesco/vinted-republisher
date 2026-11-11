package fr.vinted.republisher.adapter.in.web;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
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
 * The static page of a listing's backup in the installed Chrome, against a simulated API answering as the
 * contract says: never the real application nor Vinted.
 */
@Tag("browser")
class ListingBackupPageTest {

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
        fakeApi.createContext("/listing-backup.html", exchange -> {
            try (InputStream html = getClass().getResourceAsStream("/static/listing-backup.html")) {
                respond(exchange, 200, "text/html; charset=utf-8", html.readAllBytes());
            }
        });
        fakeApi.createContext("/api/listings/abc/backup", exchange -> respond(exchange, 400, "text/plain", new byte[0]));
        fakeApi.createContext("/api/listings/3/backup", exchange ->
                respond(exchange, backupStatus.get(), "application/json", bytes(backup.get())));
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
        fakeApi.start();
        page = chrome.newPage();
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
    void listing_without_backup_says_so() {
        backupStatus.set(404);
        backup.set("");

        open();

        assertThat(problem()).containsText("Aucune sauvegarde");
        assertThat(validateButton()).isHidden();
    }

    @Test
    void unknown_listing_identifier_says_there_is_no_backup() {
        page.navigate("http://127.0.0.1:" + fakeApi.getAddress().getPort() + "/listing-backup.html?id=abc");

        assertThat(problem()).containsText("Aucune sauvegarde");
    }

    @Test
    void links_back_to_the_wardrobe() {
        open();

        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Retour au dressing")))
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

    private void open() {
        page.navigate("http://127.0.0.1:" + fakeApi.getAddress().getPort() + "/listing-backup.html?id=3");
    }

    private Locator verification() {
        return page.locator("#verification");
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
