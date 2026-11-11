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
            {"vintedId": 3, "savedAt": "2026-11-10T10:00:00Z",
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
