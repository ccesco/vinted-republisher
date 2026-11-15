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
 * The static wardrobe page in the installed Chrome, against a simulated API answering as the
 * contract says: never the real application nor Vinted.
 */
@Tag("browser")
class WardrobePageTest {

    private static final String IDLE = "{\"scanInProgress\": false, \"backupInProgress\": false}";
    private static final String SCANNED = """
            {"scanInProgress": false, "backupInProgress": false, "lastScanAt": "2026-11-06T10:15:30Z"}""";
    private static final String NO_LISTING = "{\"listings\": []}";
    private static final String TWO_LISTINGS = """
            {"listings": [
              {"vintedId": 2, "title": "Pull <b>rayé</b>", "price": {"amount": 12.5, "currency": "EUR"},
               "url": "https://vinted.test/items/2", "present": true},
              {"vintedId": 1, "title": "Bonnet", "price": {"amount": 1, "currency": "EUR"},
               "url": "https://vinted.test/items/1", "present": false}]}""";

    private static Playwright playwright;
    private static Browser chrome;

    private HttpServer fakeApi;
    private final AtomicReference<String> overview = new AtomicReference<>(IDLE);
    private final AtomicReference<String> inventory = new AtomicReference<>(NO_LISTING);
    private final AtomicReference<String> launchAnswer = new AtomicReference<>(IDLE);
    private final AtomicBoolean applicationDown = new AtomicBoolean();
    private final List<String> launchedScans = new CopyOnWriteArrayList<>();
    private final List<String> launchedBackups = new CopyOnWriteArrayList<>();
    private final List<String> listingQueries = new CopyOnWriteArrayList<>();
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
        fakeApi.createContext("/wardrobe.html", exchange -> {
            try (InputStream html = getClass().getResourceAsStream("/static/wardrobe.html")) {
                respond(exchange, 200, "text/html; charset=utf-8", html.readAllBytes());
            }
        });
        fakeApi.createContext("/api/wardrobe", exchange -> answer(exchange, overview.get()));
        fakeApi.createContext("/api/listings", exchange -> {
            listingQueries.add(exchange.getRequestURI().getRawQuery());
            answer(exchange, inventory.get());
        });
        fakeApi.createContext("/api/wardrobe/scan", exchange -> {
            launchedScans.add(exchange.getRequestMethod());
            respond(exchange, 202, "application/json", launchAnswer.get().getBytes(StandardCharsets.UTF_8));
        });
        fakeApi.createContext("/api/wardrobe/backup", exchange -> {
            launchedBackups.add(exchange.getRequestMethod());
            respond(exchange, 202, "application/json", launchAnswer.get().getBytes(StandardCharsets.UTF_8));
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
    void shows_listing_table_and_requests_chosen_criteria() {
        // Étant donné une API qui renvoie deux annonces, dont une au titre "<b>gras</b>"
        overview.set(SCANNED);
        inventory.set(TWO_LISTINGS);

        // Quand le membre ouvre wardrobe.html
        open();

        // Alors il voit un tableau de deux lignes avec titre, prix, vues, favoris, présence, sauvegarde et validation
        //   et le titre affiché tel quel
        assertThat(page.locator("#listings thead th")).hasText(new String[]{
                "Titre", "Prix", "Vues", "Favoris", "Présence", "Sauvegarde", "Validation", "Étape"});
        assertThat(rows()).hasCount(2);
        assertThat(listingCount()).hasText("2 annonces");
        assertThat(rows().nth(0)).containsText("Pull <b>rayé</b>");
        assertThat(rows().nth(0)).containsText("12,50");
        assertThat(cell(1, PRESENCE)).hasText("absente");
        assertThat(cell(0, PRESENCE)).hasText("");
        Locator link = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Bonnet"));
        assertThat(link).hasAttribute("href", "listing.html?id=1");
        assertThat(listingQueries).contains("presence=PRESENT&backup=ALL&sort=RECENCY&direction=DESC");

        // Quand il choisit « toutes », « validées », tape "pull" et trie par vues croissantes
        page.locator("#presence").selectOption("ALL");
        page.locator("#backup-filter").selectOption("VALIDATED");
        page.locator("#sort").selectOption("VIEWS");
        page.locator("#direction").selectOption("ASC");
        page.locator("#search").fill("pull");

        // Alors la page demande GET /api/listings?presence=ALL&backup=VALIDATED&search=pull&sort=VIEWS&direction=ASC
        page.waitForCondition(() -> listingQueries.contains(
                "presence=ALL&backup=VALIDATED&search=pull&sort=VIEWS&direction=ASC"));
    }

    @Test
    void shows_the_answer_to_the_last_criteria() {
        open();
        assertThat(listingCount()).hasText("0 annonce");

        inventory.set(TWO_LISTINGS);
        page.locator("#presence").selectOption("ALL");

        assertThat(rows()).hasCount(2);
    }

    @Test
    void says_when_the_list_is_unavailable() {
        open();

        applicationDown.set(true);
        page.locator("#presence").selectOption("ALL");

        assertThat(page.locator("#listings-problem")).hasText("Liste indisponible.");
    }

    @Test
    void shows_the_date_of_the_last_scan_and_offers_to_scan() {
        overview.set(SCANNED);

        open();

        assertThat(summary()).containsText("06/11/2026");
        assertThat(button()).isVisible();
    }

    @Test
    void says_when_no_scan_has_succeeded_yet() {
        open();

        assertThat(summary()).hasText("Aucun scan terminé pour l'instant.");
        assertThat(rows()).hasCount(0);
    }

    @Test
    void scan_button_launches_a_scan() {
        open();

        button().click();

        page.waitForCondition(() -> !launchedScans.isEmpty());
        assertThat(launchedScans).containsExactly("POST");
    }

    @Test
    void shows_the_scan_launched_from_the_answer_to_the_request() {
        open();
        launchAnswer.set("{\"scanInProgress\": true, \"backupInProgress\": false}");

        button().click();

        assertThat(progress()).isVisible();
        assertThat(button()).isHidden();
    }

    @Test
    void follows_a_scan_in_progress_until_done_and_reads_the_list_again() {
        overview.set("{\"scanInProgress\": true, \"backupInProgress\": false}");
        open();
        assertThat(progress()).isVisible();

        inventory.set(TWO_LISTINGS);
        overview.set(SCANNED);

        assertThat(rows()).hasCount(2, new LocatorAssertions.HasCountOptions().setTimeout(5000));
        assertThat(progress()).isHidden();
        assertThat(button()).isVisible();
    }

    @Test
    void explains_an_unreadable_wardrobe() {
        overview.set("{\"scanInProgress\": false, \"problem\": \"WARDROBE_UNREADABLE\"}");

        open();

        assertThat(problem()).hasText("Le dressing est illisible (total incohérent ou page changée) : "
                + "ce qui a été lu est conservé, le prochain scan reprendra. Voir les logs.");
    }

    @Test
    void explains_an_unreachable_vinted() {
        overview.set("{\"scanInProgress\": false, \"problem\": \"VINTED_UNREACHABLE\"}");

        open();

        assertThat(problem()).containsText("Vinted est inaccessible");
    }

    @Test
    void explains_an_unavailable_browser() {
        overview.set("{\"scanInProgress\": false, \"problem\": \"BROWSER_UNAVAILABLE\"}");

        open();

        assertThat(problem()).containsText("Le navigateur est indisponible");
    }

    @Test
    void explains_a_failed_scan() {
        overview.set("{\"scanInProgress\": false, \"problem\": \"SCAN_FAILED\"}");

        open();

        assertThat(problem()).hasText("Le scan a échoué : ce qui a été lu est conservé, "
                + "le prochain scan reprendra. Voir les logs.");
    }

    @Test
    void retries_until_the_application_answers_again() {
        applicationDown.set(true);
        open();
        assertThat(problem()).containsText("L'application ne répond pas");

        inventory.set(TWO_LISTINGS);
        applicationDown.set(false);

        assertThat(rows()).hasCount(2, new LocatorAssertions.HasCountOptions().setTimeout(5000));
        assertThat(problem()).isHidden();
    }

    @Test
    void shows_backup_state() {
        // Étant donné une annonce sauvegardée et une annonce jamais sauvegardée
        overview.set("""
                {"scanInProgress": false, "backupInProgress": false, "lastBackupAt": "2026-11-07T11:02:10Z"}""");
        inventory.set("""
                {"listings": [
                  {"vintedId": 2, "title": "Pull", "price": {"amount": 12.5, "currency": "EUR"},
                   "url": "https://vinted.test/items/2", "present": true, "savedAt": "2026-11-07T10:31:45Z",
                   "photoCount": 3},
                  {"vintedId": 1, "title": "Bonnet", "price": {"amount": 1, "currency": "EUR"},
                   "url": "https://vinted.test/items/1", "present": true}]}""");
        // Quand j'ouvre la page du dressing
        open();
        // Alors je vois la date de sauvegarde de la première et « non sauvegardée » pour la seconde
        // Et je vois la date de la dernière sauvegarde terminée et le bouton « Sauvegarder »
        assertThat(cell(0, BACKUP)).containsText("sauvegardée le 07/11/2026");
        // Et le nombre de photos gardées de la première (AT-06-11)
        assertThat(cell(0, BACKUP)).containsText("· 3 photos");
        assertThat(cell(1, BACKUP)).hasText("non sauvegardée");
        assertThat(backupSummary()).containsText("Dernière sauvegarde terminée le 07/11/2026");
        assertThat(backupButton()).isVisible();
    }

    @Test
    void shows_statistics() {
        // Étant donné une annonce dont le dernier relevé est 12 vues et 3 favoris, et une annonce jamais relevée
        inventory.set("""
                {"listings": [
                  {"vintedId": 3, "title": "Pull", "price": {"amount": 12.5, "currency": "EUR"},
                   "url": "https://vinted.test/items/3", "present": true,
                   "statistics": {"viewCount": 12, "favouriteCount": 3, "readAt": "2026-11-07T10:05:00Z"}},
                  {"vintedId": 1, "title": "Bonnet", "price": {"amount": 1, "currency": "EUR"},
                   "url": "https://vinted.test/items/1", "present": true}]}""");
        // Quand j'ouvre la page du dressing
        open();
        // Alors je vois 12 vues et 3 favoris pour la première, avec la date du relevé, et rien pour la seconde
        assertThat(cell(0, VIEWS)).hasText("12");
        assertThat(cell(0, FAVOURITES)).hasText("3");
        assertThat(cell(0, VIEWS)).hasAttribute("title", Pattern.compile("Relevé le 07/11/2026.*"));
        assertThat(cell(1, VIEWS)).hasText("");
        assertThat(cell(1, FAVOURITES)).hasText("");
    }

    @Test
    void shows_validation() {
        // Étant donné une annonce sauvegardée et validée, et une autre sauvegardée non validée, et une jamais sauvegardée
        inventory.set("""
                {"listings": [
                   {"vintedId": 3, "title": "Pull", "price": {"amount": 12.5, "currency": "EUR"},
                    "url": "https://vinted.test/items/3", "present": true, "savedAt": "2026-11-07T10:00:00Z",
                    "photoCount": 2, "validatedAt": "2026-11-08T09:00:00Z"},
                   {"vintedId": 2, "title": "Jupe", "price": {"amount": 4, "currency": "EUR"},
                    "url": "https://vinted.test/items/2", "present": true, "savedAt": "2026-11-07T10:00:00Z",
                    "photoCount": 1},
                   {"vintedId": 1, "title": "Bonnet", "price": {"amount": 1, "currency": "EUR"},
                    "url": "https://vinted.test/items/1", "present": true}]}""");
        // Quand j'ouvre la page du dressing
        open();
        // Alors le dressing affiche « validée le … » pour la première
        assertThat(cell(0, VALIDATION)).containsText("validée le 08/11/2026");
        assertThat(cell(1, VALIDATION)).hasText("");
    }

    @Test
    void stage_column_highlights_listings_to_check() {
        // Étant donné une annonce « Suppression à vérifier » et une annonce « À sauvegarder »
        inventory.set("""
                {"listings": [
                   {"vintedId": 3, "title": "Pull", "price": {"amount": 12.5, "currency": "EUR"},
                    "url": "https://vinted.test/items/3", "present": true, "stage": "DELETION_TO_CHECK"},
                   {"vintedId": 1, "title": "Bonnet", "price": {"amount": 1, "currency": "EUR"},
                    "url": "https://vinted.test/items/1", "present": true, "stage": "TO_BACK_UP"}]}""");
        // Quand le membre ouvre la page du dressing
        open();
        // Alors la colonne « Étape » montre leurs libellés
        assertThat(cell(0, STAGE)).hasText("Suppression à vérifier");
        assertThat(cell(1, STAGE)).hasText("À sauvegarder");
        // Et seule « Suppression à vérifier » est mise en évidence
        assertThat(cell(0, STAGE).locator("strong")).hasCount(1);
        assertThat(cell(1, STAGE).locator("strong")).hasCount(0);
    }

    @Test
    void title_opens_the_listing_detail() {
        // Étant donné la liste avec une annonce sauvegardée et une annonce absente non sauvegardée
        inventory.set("""
                {"listings": [
                   {"vintedId": 3, "title": "Pull", "price": {"amount": 12.5, "currency": "EUR"},
                    "url": "https://vinted.test/items/3", "present": true, "savedAt": "2026-11-07T10:00:00Z",
                    "photoCount": 2},
                   {"vintedId": 1, "title": "Bonnet", "price": {"amount": 1, "currency": "EUR"},
                    "url": "https://vinted.test/items/1", "present": false}]}""");
        // Quand le membre regarde le tableau
        open();
        // Alors le titre de chacune mène à listing.html?id=<son identifiant>
        //   et aucune ligne n'a de lien « Voir la sauvegarde »
        assertThat(rows().nth(0).getByRole(AriaRole.LINK)).hasAttribute("href", "listing.html?id=3");
        assertThat(rows().nth(0).getByRole(AriaRole.LINK)).hasText("Pull");
        assertThat(rows().nth(1).getByRole(AriaRole.LINK)).hasAttribute("href", "listing.html?id=1");
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Voir la sauvegarde")))
                .hasCount(0);
    }

    @Test
    void says_when_no_backup_has_finished_yet() {
        open();

        assertThat(backupSummary()).hasText("Aucune sauvegarde terminée pour l'instant.");
    }

    @Test
    void backup_button_launches_a_backup() {
        open();

        backupButton().click();

        page.waitForCondition(() -> !launchedBackups.isEmpty());
        assertThat(launchedBackups).containsExactly("POST");
    }

    @Test
    void follows_a_backup_in_progress_until_done_without_offering_another_action() {
        overview.set("{\"scanInProgress\": false, \"backupInProgress\": true}");
        open();
        assertThat(backupProgress()).isVisible();
        assertThat(backupButton()).isHidden();
        assertThat(button()).isHidden();

        overview.set(IDLE);

        assertThat(backupProgress()).isHidden(new LocatorAssertions.IsHiddenOptions().setTimeout(5000));
        assertThat(backupButton()).isVisible();
        assertThat(button()).isVisible();
    }

    @Test
    void explains_a_disconnected_session() {
        overview.set("{\"scanInProgress\": false, \"backupInProgress\": false, "
                + "\"backupProblem\": \"SESSION_DISCONNECTED\"}");

        open();

        assertThat(backupProblem()).hasText("La session Vinted n'est pas connectée : connectez-vous depuis la page "
                + "de la session, puis relancez la sauvegarde, qui reprendra là où elle s'est arrêtée.");
    }

    @Test
    void explains_an_unreadable_listing() {
        overview.set("{\"scanInProgress\": false, \"backupInProgress\": false, "
                + "\"backupProblem\": \"LISTING_UNREADABLE\"}");

        open();

        assertThat(backupProblem()).containsText("Une annonce est illisible");
    }

    private static final int VIEWS = 2;
    private static final int FAVOURITES = 3;
    private static final int PRESENCE = 4;
    private static final int BACKUP = 5;
    private static final int VALIDATION = 6;
    private static final int STAGE = 7;

    private void answer(HttpExchange exchange, String json) throws IOException {
        if (applicationDown.get()) {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        } else {
            respond(exchange, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private Locator backupSummary() {
        return page.locator("#backup-summary");
    }

    private Locator backupProgress() {
        return page.locator("#backup-progress");
    }

    private Locator backupProblem() {
        return page.locator("#backup-problem");
    }

    private Locator backupButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sauvegarder").setExact(true));
    }

    private void open() {
        page.navigate("http://127.0.0.1:" + fakeApi.getAddress().getPort() + "/wardrobe.html");
    }

    private Locator summary() {
        return page.locator("#wardrobe-summary");
    }

    private Locator listingCount() {
        return page.locator("#listing-count");
    }

    private Locator progress() {
        return page.locator("#scan-progress");
    }

    private Locator problem() {
        return page.locator("#wardrobe-problem");
    }

    private Locator rows() {
        return page.locator("#listings tbody tr");
    }

    private Locator cell(int row, int column) {
        return rows().nth(row).locator("td").nth(column);
    }

    private Locator button() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Scanner").setExact(true));
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
