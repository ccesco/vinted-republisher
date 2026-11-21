package fr.vinted.republisher.adapter.in.web;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    private static final String SCANNING = "{\"scanInProgress\": true, \"backupInProgress\": false}";
    private static final String NO_LISTING = "{\"listings\": []}";
    /** The follow-up period of the page (US-42). */
    private static final int PERIOD_MS = 2000;
    /** Real time left to the page to ask what an answer led to, for what must not happen. */
    private static final int SETTLE_MS = 500;
    private static final String IDLE_RUN = "{\"status\": \"IDLE\", \"relaunchCount\": 0, \"skippedListingIds\": []}";
    private static final String TWO_LISTINGS = """
            {"listings": [
              {"vintedId": 2, "title": "Pull <b>rayé</b>", "price": {"amount": 12.5, "currency": "EUR"},
               "url": "https://vinted.test/items/2", "present": true},
              {"vintedId": 1, "title": "Bonnet", "price": {"amount": 1, "currency": "EUR"},
               "url": "https://vinted.test/items/1", "present": false}]}""";

    private static final byte[] ARCHIVE = {'P', 'K', 5, 6};
    private static final String IDLE_VERIFICATION = """
            {"status": "IDLE", "verifiedCount": 0, "totalCount": 0, "validatedCount": 0, "failedCount": 0}""";

    private static Playwright playwright;
    private static Browser chrome;

    private HttpServer fakeApi;
    private final AtomicReference<String> overview = new AtomicReference<>(IDLE);
    private final AtomicReference<String> inventory = new AtomicReference<>(NO_LISTING);
    private final AtomicReference<String> searchedInventory = new AtomicReference<>();
    private final AtomicReference<Integer> inventoryStatus = new AtomicReference<>(200);
    private final AtomicReference<String> launchAnswer = new AtomicReference<>(IDLE);
    private final AtomicBoolean applicationDown = new AtomicBoolean();
    private final List<String> launchedScans = new CopyOnWriteArrayList<>();
    private final List<String> launchedBackups = new CopyOnWriteArrayList<>();
    private final List<String> launchedRefreshes = new CopyOnWriteArrayList<>();
    private final List<String> listingQueries = new CopyOnWriteArrayList<>();
    private final List<String> relaunches = new CopyOnWriteArrayList<>();
    private final AtomicReference<Integer> relaunchStatus = new AtomicReference<>(200);
    private final AtomicReference<String> relaunchAnswer = new AtomicReference<>("{}");
    private final CountDownLatch relaunchAnswered = new CountDownLatch(1);
    private final AtomicReference<String> runOverview = new AtomicReference<>(IDLE_RUN);
    private final List<String> launchedRuns = new CopyOnWriteArrayList<>();
    private final List<String> runControls = new CopyOnWriteArrayList<>();
    private final AtomicReference<Integer> archiveStatus = new AtomicReference<>(200);
    private final AtomicReference<String> verification = new AtomicReference<>(IDLE_VERIFICATION);
    private final List<String> launchedVerifications = new CopyOnWriteArrayList<>();
    private final List<String> wardrobeReadings = new CopyOnWriteArrayList<>();
    private final List<String> runReadings = new CopyOnWriteArrayList<>();
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
        BuiltFrontend.serveFrom(fakeApi);
        fakeApi.createContext("/api/wardrobe", exchange -> {
            wardrobeReadings.add("GET");
            answer(exchange, overview.get());
        });
        fakeApi.createContext("/api/listings", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            listingQueries.add(query);
            if (inventoryStatus.get() == 200) {
                boolean searched = query != null && query.contains("search=") && searchedInventory.get() != null;
                answer(exchange, searched ? searchedInventory.get() : inventory.get());
            } else {
                exchange.sendResponseHeaders(inventoryStatus.get(), -1);
                exchange.close();
            }
        });
        fakeApi.createContext("/api/wardrobe/scan", exchange -> {
            launchedScans.add(exchange.getRequestMethod());
            respond(exchange, 202, "application/json", launchAnswer.get().getBytes(StandardCharsets.UTF_8));
        });
        fakeApi.createContext("/api/wardrobe/backup", exchange -> {
            launchedBackups.add(exchange.getRequestMethod());
            respond(exchange, 202, "application/json", launchAnswer.get().getBytes(StandardCharsets.UTF_8));
        });
        fakeApi.createContext("/api/wardrobe/statistics-refresh", exchange -> {
            launchedRefreshes.add(exchange.getRequestMethod());
            respond(exchange, 202, "application/json", launchAnswer.get().getBytes(StandardCharsets.UTF_8));
        });
        fakeApi.createContext("/api/listings/3/relaunch", exchange -> {
            relaunches.add(exchange.getRequestMethod() + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                relaunchAnswered.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, relaunchStatus.get(), "application/json",
                    relaunchAnswer.get().getBytes(StandardCharsets.UTF_8));
        });
        fakeApi.createContext("/api/automatic-run", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                launchedRuns.add("POST");
                respond(exchange, 202, "application/json", runOverview.get().getBytes(StandardCharsets.UTF_8));
            } else {
                runReadings.add("GET");
                answer(exchange, runOverview.get());
            }
        });
        for (String control : List.of("pause", "resume", "stop")) {
            fakeApi.createContext("/api/automatic-run/" + control, exchange -> {
                runControls.add(exchange.getRequestMethod() + " " + control);
                respond(exchange, 202, "application/json", runOverview.get().getBytes(StandardCharsets.UTF_8));
            });
        }
        fakeApi.createContext("/api/backup-verifications", exchange -> {
            launchedVerifications.add(exchange.getRequestMethod());
            respond(exchange, 202, "application/json", verification.get().getBytes(StandardCharsets.UTF_8));
        });
        fakeApi.createContext("/api/backup-verifications/current", exchange -> answer(exchange, verification.get()));
        fakeApi.createContext("/api/data-archive", exchange -> {
            int status = archiveStatus.get();
            if (status == 200) {
                exchange.getResponseHeaders().add("Content-Disposition",
                        "attachment; filename=\"vinted-republisher-archive-2026-11-17T14-05-09.zip\"");
                respond(exchange, 200, "application/zip", ARCHIVE);
            } else {
                String reason = status == 409 ? "BROWSER_BUSY" : "ARCHIVE_FAILED";
                respond(exchange, status, "application/json",
                        ("{\"reason\": \"" + reason + "\"}").getBytes(StandardCharsets.UTF_8));
            }
        });
        fakeApi.setExecutor(Executors.newCachedThreadPool());
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

        // Quand le membre ouvre le Dressing
        open();

        // Alors il voit un tableau de deux lignes avec titre, prix, vues, favoris, présence, sauvegarde et validation
        //   et le titre affiché tel quel
        assertThat(page.locator("#listings thead th")).hasText(new String[]{
                "Titre", "Prix", "Vues", "Favoris", "Conseil", "Présence", "Sauvegarde", "Validation", "Étape",
                "Republication", "Republications", "Action", "Tags", ""});
        assertThat(rows()).hasCount(2);
        assertThat(listingCount()).hasText("2 annonces");
        assertThat(rows().nth(0)).containsText("Pull <b>rayé</b>");
        assertThat(rows().nth(0)).containsText("12,50");
        assertThat(cell(1, PRESENCE)).hasText("absente");
        assertThat(cell(0, PRESENCE)).hasText("");
        Locator link = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Bonnet"));
        assertThat(link).hasAttribute("href", "#/listings/1");
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
    void shows_the_sold_stage() {
        // AT-50-07. Étant donné l'annonce 1 « Vendue »
        overview.set(SCANNED);
        inventory.set("""
                {"listings": [
                  {"vintedId": 1, "title": "Pull", "price": {"amount": 10, "currency": "EUR"},
                   "url": "https://vinted.test/items/1", "present": true, "stage": "SOLD"}]}""");
        open();

        // Quand le membre filtre par l'étape « Vendue »
        page.locator("#stage-filter").selectOption(new SelectOption().setLabel("Vendue"));

        // Alors la liste est demandée avec cette étape, et l'annonce y est « Vendue »
        page.waitForCondition(() -> listingQueries.contains(
                "presence=ALL&backup=ALL&sort=RECENCY&direction=DESC&stage=SOLD"));
        assertThat(cell(0, STAGE)).hasText("Vendue");
    }

    @Test
    void filters_on_stage_and_shows_republication() {
        // Étant donné une annonce republiée, dont la nouvelle annonce est connue, et une annonce à vérifier
        overview.set(SCANNED);
        inventory.set("""
                {"listings": [
                  {"vintedId": 1, "title": "Pull", "price": {"amount": 10, "currency": "EUR"},
                   "url": "https://vinted.test/items/1", "present": false, "stage": "REPUBLISHED",
                   "republication": {"outcome": "PUBLISHED", "attemptedAt": "2026-11-18T11:00:00Z",
                     "publishedAt": "2026-11-18T11:05:00Z", "newListingId": 3,
                     "newListingUrl": "https://vinted.test/items/3", "backupSavedAt": "2026-11-10T10:00:00Z"}},
                  {"vintedId": 2, "title": "Jupe", "price": {"amount": 5, "currency": "EUR"},
                   "url": "https://vinted.test/items/2", "present": false, "stage": "REPUBLICATION_TO_CHECK",
                   "republication": {"outcome": "FAILED", "attemptedAt": "2026-11-18T11:00:00Z",
                     "backupSavedAt": "2026-11-10T10:00:00Z"}}]}""");
        open();

        // Quand le membre choisit l'étape « Republiée »
        page.locator("#stage-filter").selectOption("REPUBLISHED");

        // Alors la présence passe sur « Toutes » et la liste est demandée avec cette étape
        assertThat(page.locator("#presence")).hasValue("ALL");
        page.waitForCondition(() -> listingQueries.contains(
                "presence=ALL&backup=ALL&sort=RECENCY&direction=DESC&stage=REPUBLISHED"));
        // Et la republiée montre sa date et le lien vers sa nouvelle annonce, ouvert dans un nouvel onglet
        assertThat(cell(0, REPUBLICATION)).hasText("le 18/11/2026 nouvelle annonce");
        Locator newListing = cell(0, REPUBLICATION).getByRole(AriaRole.LINK);
        assertThat(newListing).hasAttribute("href", "https://vinted.test/items/3");
        assertThat(newListing).hasAttribute("target", "_blank");
        assertThat(newListing).hasAttribute("rel", "noopener noreferrer");
        // Et celle à vérifier sa tentative
        assertThat(cell(1, REPUBLICATION)).hasText("tentée le 18/11/2026, à vérifier");

        // Quand il revient à toutes les étapes, la présence reste « Toutes »
        page.locator("#stage-filter").selectOption("");
        assertThat(page.locator("#presence")).hasValue("ALL");
    }

    /** AT-44-05 */
    @Test
    void shows_republication_tally() {
        // Étant donné la page Annonces
        // Et une annonce avec 2 réussies et 1 échec, une avec 1 réussie, une avec 3 échecs, une sans republication
        overview.set(SCANNED);
        inventory.set("""
                {"listings": [%s, %s, %s, %s]}""".formatted(tallied(1, 2, 1), tallied(2, 1, 0), tallied(3, 0, 3),
                tallied(4, 0, 0)));

        // Quand la liste s'affiche
        open();

        // Alors la colonne « Republications » affiche « 2 réussies, 1 échec », « 1 réussie », « 3 échecs » et rien
        assertThat(page.locator("thead th").nth(REPUBLICATIONS)).hasText("Republications");
        assertThat(cell(0, REPUBLICATIONS)).hasText("2 réussies, 1 échec");
        assertThat(cell(1, REPUBLICATIONS)).hasText("1 réussie");
        assertThat(cell(2, REPUBLICATIONS)).hasText("3 échecs");
        assertThat(cell(3, REPUBLICATIONS)).hasText("");
    }

    private static String tallied(int id, int published, int notPublished) {
        return """
                {"vintedId": %d, "title": "Pull %d", "price": {"amount": 10, "currency": "EUR"},
                 "url": "https://vinted.test/items/%d", "present": true, "stage": "TO_DELETE",
                 "republicationTally": {"published": %d, "notPublished": %d}}"""
                .formatted(id, id, id, published, notPublished);
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
        assertThat(page.locator("#listings")).isVisible();
    }

    @Test
    void showsKeyFiguresComputedFromListings() {
        // AT-37-01. Étant donné que l'API renvoie 3 annonces présentes, dont 2 avec une sauvegarde validée,
        //   totalisant 120 vues et 9 favoris
        inventory.set("""
                {"listings": [
                  {"vintedId": 1, "title": "Pull", "price": {"amount": 10, "currency": "EUR"}, "url": "https://vinted.test/items/1",
                   "present": true, "stage": "TO_DELETE", "validatedAt": "2026-11-10T10:00:00Z",
                   "statistics": {"viewCount": 100, "favouriteCount": 5, "readAt": "2026-11-17T10:00:00Z"}},
                  {"vintedId": 2, "title": "Bonnet", "price": {"amount": 5, "currency": "EUR"}, "url": "https://vinted.test/items/2",
                   "present": true, "stage": "TO_DELETE", "validatedAt": "2026-11-11T10:00:00Z",
                   "statistics": {"viewCount": 20, "favouriteCount": 4, "readAt": "2026-11-17T10:00:00Z"}},
                  {"vintedId": 3, "title": "Écharpe", "price": {"amount": 8, "currency": "EUR"}, "url": "https://vinted.test/items/3",
                   "present": true, "stage": "TO_BACK_UP"}]}""");

        // Quand j'ouvre le Dressing
        open();

        // Alors je vois « 3 » annonces en ligne, « 2 / 3 » sauvegardes validées, « 120 » vues et « 9 » favoris
        assertThat(page.locator("#key-online")).hasText("3");
        assertThat(page.locator("#key-validated")).hasText("2 / 3");
        assertThat(page.locator("#key-views")).hasText("120");
        assertThat(page.locator("#key-favourites")).hasText("9");
    }

    @Test
    void keepsKeyFiguresOfTheWholeWardrobeWhenTheListIsFiltered() {
        // AT-37-07. Étant donné que le dressing compte 3 annonces présentes, dont 2 avec une sauvegarde validée,
        //   totalisant 120 vues et 9 favoris
        inventory.set("""
                {"listings": [
                  {"vintedId": 1, "title": "Pull", "price": {"amount": 10, "currency": "EUR"}, "url": "https://vinted.test/items/1",
                   "present": true, "stage": "TO_DELETE", "validatedAt": "2026-11-10T10:00:00Z",
                   "statistics": {"viewCount": 100, "favouriteCount": 5, "readAt": "2026-11-17T10:00:00Z"}},
                  {"vintedId": 2, "title": "Bonnet", "price": {"amount": 5, "currency": "EUR"}, "url": "https://vinted.test/items/2",
                   "present": true, "stage": "TO_DELETE", "validatedAt": "2026-11-11T10:00:00Z",
                   "statistics": {"viewCount": 20, "favouriteCount": 4, "readAt": "2026-11-17T10:00:00Z"}},
                  {"vintedId": 3, "title": "Écharpe", "price": {"amount": 8, "currency": "EUR"}, "url": "https://vinted.test/items/3",
                   "present": true, "stage": "TO_BACK_UP"}]}""");
        // Et que la recherche « pull » ne renvoie qu'une de ces annonces
        searchedInventory.set("""
                {"listings": [
                  {"vintedId": 1, "title": "Pull", "price": {"amount": 10, "currency": "EUR"}, "url": "https://vinted.test/items/1",
                   "present": true, "stage": "TO_DELETE", "validatedAt": "2026-11-10T10:00:00Z",
                   "statistics": {"viewCount": 100, "favouriteCount": 5, "readAt": "2026-11-17T10:00:00Z"}}]}""");

        // Quand j'ouvre le Dressing et que je cherche « pull »
        open();
        page.locator("#search").fill("pull");

        // Alors la liste n'affiche qu'une annonce
        assertThat(page.locator("#listings tbody tr")).hasCount(1);
        // Et je vois toujours « 3 » annonces en ligne, « 2 / 3 » sauvegardes validées, « 120 » vues et « 9 » favoris
        assertThat(page.locator("#key-online")).hasText("3");
        assertThat(page.locator("#key-validated")).hasText("2 / 3");
        assertThat(page.locator("#key-views")).hasText("120");
        assertThat(page.locator("#key-favourites")).hasText("9");
        // Et changer un filtre ne relit pas tout le dressing
        settle();
        assertThat(listingQueries).doesNotContainNull();
    }

    @Test
    void readsKeyFiguresOfTheWholeWardrobeAtEachFollowUpWhenTheListIsFiltered() {
        // AT-37-08. Étant donné un scan en cours et une recherche « pull » qui ne renvoie qu'une annonce
        overview.set(SCANNING);
        searchedInventory.set("""
                {"listings": [
                  {"vintedId": 1, "title": "Pull", "price": {"amount": 10, "currency": "EUR"}, "url": "https://vinted.test/items/1",
                   "present": true, "stage": "TO_BACK_UP"}]}""");
        openFollowing();
        page.locator("#search").fill("pull");
        page.clock().runFor(PERIOD_MS);
        assertThat(page.locator("#listings tbody tr")).hasCount(1);
        // Quand le scan trouve 4 annonces présentes et qu'une période de suivi passe
        inventory.set("""
                {"listings": [
                  {"vintedId": 1, "title": "Pull", "price": {"amount": 10, "currency": "EUR"}, "url": "https://vinted.test/items/1",
                   "present": true, "stage": "TO_BACK_UP"},
                  {"vintedId": 2, "title": "Bonnet", "price": {"amount": 5, "currency": "EUR"}, "url": "https://vinted.test/items/2",
                   "present": true, "stage": "TO_BACK_UP"},
                  {"vintedId": 3, "title": "Écharpe", "price": {"amount": 8, "currency": "EUR"}, "url": "https://vinted.test/items/3",
                   "present": true, "stage": "TO_BACK_UP"},
                  {"vintedId": 4, "title": "Gants", "price": {"amount": 6, "currency": "EUR"}, "url": "https://vinted.test/items/4",
                   "present": true, "stage": "TO_BACK_UP"}]}""");
        followPeriods(1, "/api/wardrobe");
        // Alors la page relit tout le dressing, sans critère, et je vois « 4 » annonces en ligne
        assertThat(page.locator("#key-online")).hasText("4");
        assertThat(listingQueries).containsNull();
        // Et la liste n'affiche toujours qu'une annonce
        assertThat(page.locator("#listings tbody tr")).hasCount(1);
    }

    @Test
    void showsAnErrorWhenListingsCannotBeLoaded() {
        // AT-37-06. Étant donné que l'API répond en erreur à la liste des annonces
        inventoryStatus.set(500);

        // Quand j'ouvre le Dressing
        open();

        // Alors un message d'erreur s'affiche à la place de la liste
        assertThat(page.locator("#listings-problem")).hasText("Liste indisponible.");
        assertThat(page.locator("#listings")).isHidden();
        // Et la barre de navigation reste utilisable
        page.getByRole(AriaRole.NAVIGATION).getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Journal")).click();
        assertThat(page).hasURL(Pattern.compile("#/journal$"));
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
    void shows_the_republication_advice() {
        // Étant donné une annonce conseillée à republier (6 vues gagnées depuis le 8 novembre), et une autre non
        inventory.set("""
                {"listings": [
                  {"vintedId": 3, "title": "Pull", "price": {"amount": 12.5, "currency": "EUR"},
                   "url": "https://vinted.test/items/3", "present": true,
                   "republicationAdvice": {"viewsGained": 6, "since": "2026-11-08T10:00:00Z"}},
                  {"vintedId": 1, "title": "Bonnet", "price": {"amount": 1, "currency": "EUR"},
                   "url": "https://vinted.test/items/1", "present": true}]}""");
        // Quand j'ouvre la page du dressing
        open();
        // Alors la première est marquée « À republier », avec sa raison en infobulle, et rien pour la seconde
        assertThat(cell(0, ADVICE)).hasText("À republier");
        assertThat(cell(0, ADVICE)).hasAttribute("title", "+6 vues depuis le 08/11/2026");
        assertThat(cell(1, ADVICE)).hasText("");
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
    void relaunches_a_listing_and_shows_its_new_stage() {
        // Étant donné une liste avec une annonce « Prête à supprimer » et une annonce « À valider »
        inventory.set(TO_DELETE_AND_TO_VALIDATE);
        relaunchAnswer.set("""
                {"action": "DELETION", "dryRun": false,
                 "listing": {"vintedId": 3, "title": "Pull", "price": {"amount": 12.5, "currency": "EUR"},
                             "url": "https://vinted.test/items/3", "present": false, "stage": "WAITING"}}""");
        // Quand le membre l'affiche
        open();
        // Alors seule la première a un bouton « Supprimer de Vinted » (action de la relance)
        assertThat(cell(0, ACTION).getByRole(AriaRole.BUTTON)).hasText("Supprimer de Vinted");
        assertThat(cell(1, ACTION).getByRole(AriaRole.BUTTON)).hasCount(0);
        // Quand il clique dessus
        cell(0, ACTION).getByRole(AriaRole.BUTTON).click();
        // Alors le bouton est désactivé pendant l'action
        assertThat(cell(0, ACTION).getByRole(AriaRole.BUTTON)).isDisabled();
        relaunchAnswered.countDown();
        // Puis la ligne montre la nouvelle étape et « Suppression faite »
        assertThat(cell(0, STAGE)).hasText("En attente avant republication");
        assertThat(cell(0, ACTION)).containsText("Suppression faite");
        // Et l'annonce en attente se relance tout de suite (US-49)
        assertThat(cell(0, ACTION).getByRole(AriaRole.BUTTON)).hasText("Republier sur Vinted");
        assertThat(relaunches).containsExactly("POST {\"stage\":\"TO_DELETE\"}");
    }

    @Test
    void shows_an_uncertain_outcome() {
        // Étant donné une annonce « Prête à supprimer » dont la suppression reste non confirmée après la relance
        inventory.set(TO_DELETE_AND_TO_VALIDATE);
        relaunchAnswer.set("""
                {"action": "DELETION", "dryRun": false,
                 "listing": {"vintedId": 3, "title": "Pull", "price": {"amount": 12.5, "currency": "EUR"},
                             "url": "https://vinted.test/items/3", "present": true, "stage": "DELETION_TO_CHECK"}}""");
        relaunchAnswered.countDown();
        open();
        // Quand la réponse arrive
        cell(0, ACTION).getByRole(AriaRole.BUTTON).click();
        // Alors la ligne montre « Suppression à vérifier » et « Issue incertaine : à vérifier sur Vinted »
        assertThat(cell(0, STAGE)).hasText("Suppression à vérifier");
        assertThat(cell(0, ACTION)).containsText("Issue incertaine : à vérifier sur Vinted");
        assertThat(cell(0, ACTION)).not().containsText("Suppression faite");
    }

    @Test
    void shows_a_dry_run_relaunch() {
        // Étant donné une relance arrêtée en dry-run
        inventory.set(TO_DELETE_AND_TO_VALIDATE);
        relaunchAnswer.set("""
                {"action": "DELETION", "dryRun": true,
                 "listing": {"vintedId": 3, "title": "Pull", "price": {"amount": 12.5, "currency": "EUR"},
                             "url": "https://vinted.test/items/3", "present": true, "stage": "TO_DELETE"}}""");
        relaunchAnswered.countDown();
        open();
        // Quand le membre relance
        cell(0, ACTION).getByRole(AriaRole.BUTTON).click();
        // Alors la ligne le dit, et le bouton reste proposé
        assertThat(cell(0, ACTION)).containsText("Dry-run réussi, rien n'a été modifié sur Vinted");
        assertThat(cell(0, ACTION).getByRole(AriaRole.BUTTON)).isEnabled();
    }

    @Test
    void shows_a_refusal_of_the_launched_action_with_a_link_to_the_listing() {
        // Étant donné une relance refusée par le cas d'usage lancé
        inventory.set(TO_DELETE_AND_TO_VALIDATE);
        relaunchStatus.set(409);
        relaunchAnswer.set("{\"reason\": \"ACTION_REFUSED\"}");
        relaunchAnswered.countDown();
        open();
        // Quand la réponse arrive
        cell(0, ACTION).getByRole(AriaRole.BUTTON).click();
        // Alors la ligne affiche « refusée : voir la fiche », avec le lien de la fiche
        assertThat(cell(0, ACTION)).containsText("Relance refusée : voir la fiche");
        assertThat(cell(0, ACTION).getByRole(AriaRole.LINK)).hasAttribute("href", "#/listings/3");
    }

    @Test
    void shows_the_current_stage_when_it_changed_and_relaunches_from_it() {
        // Étant donné une annonce affichée « Prête à supprimer », passée depuis à « Suppression à vérifier »
        inventory.set(TO_DELETE_AND_TO_VALIDATE);
        relaunchStatus.set(409);
        relaunchAnswer.set("{\"reason\": \"STAGE_CHANGED\", \"stage\": \"DELETION_TO_CHECK\"}");
        relaunchAnswered.countDown();
        open();
        // Quand le membre la relance
        cell(0, ACTION).getByRole(AriaRole.BUTTON).click();
        // Alors la ligne montre l'étape actuelle et le refus
        assertThat(cell(0, STAGE)).hasText("Suppression à vérifier");
        assertThat(cell(0, ACTION)).containsText("L'étape a changé");
        // Et un nouveau clic relance depuis l'étape actuelle
        cell(0, ACTION).getByRole(AriaRole.BUTTON).click();
        assertThat(cell(0, ACTION).getByRole(AriaRole.BUTTON)).isEnabled();
        assertThat(relaunches).containsExactly("POST {\"stage\":\"TO_DELETE\"}",
                "POST {\"stage\":\"DELETION_TO_CHECK\"}");
    }

    @Test
    void offers_republication_for_a_waiting_listing() {
        // AT-49-03b. Étant donné une annonce « En attente avant republication »
        inventory.set("""
                {"listings": [
                   {"vintedId": 3, "title": "Pull", "price": {"amount": 12.5, "currency": "EUR"},
                    "url": "https://vinted.test/items/3", "present": false, "stage": "WAITING"}]}""");
        relaunchAnswer.set("""
                {"action": "REPUBLICATION", "dryRun": false,
                 "listing": {"vintedId": 3, "title": "Pull", "price": {"amount": 12.5, "currency": "EUR"},
                             "url": "https://vinted.test/items/3", "present": false,
                             "stage": "REPUBLISHED"}}""");
        relaunchAnswered.countDown();
        open();
        // Quand le membre la relance avec « Republier sur Vinted »
        assertThat(cell(0, ACTION).getByRole(AriaRole.BUTTON)).hasText("Republier sur Vinted");
        cell(0, ACTION).getByRole(AriaRole.BUTTON).click();
        // Alors la republication est lancée sans attendre la date
        assertThat(cell(0, ACTION)).containsText("Republication faite");
        assertThat(relaunches).containsExactly("POST {\"stage\":\"WAITING\"}");
    }

    @Test
    void shows_an_unexpected_relaunch_failure() {
        // Étant donné une relance qui échoue de façon inattendue
        inventory.set(TO_DELETE_AND_TO_VALIDATE);
        relaunchStatus.set(500);
        relaunchAnswer.set("{\"reason\": \"RELAUNCH_FAILED\"}");
        relaunchAnswered.countDown();
        open();
        // Quand la réponse arrive
        cell(0, ACTION).getByRole(AriaRole.BUTTON).click();
        // Alors la ligne demande de vérifier la fiche avant de relancer
        assertThat(cell(0, ACTION)).containsText("Échec de la relance : vérifiez la fiche avant de relancer");
    }

    @Test
    void launches_an_automatic_run_and_follows_it() {
        // Étant donné la page du dressing, sans série depuis le démarrage
        open();
        assertThat(runButton()).isVisible();
        assertThat(runSummary()).hasText("");
        // Quand le membre clique sur « Tout lancer »
        runOverview.set("""
                {"status": "RUNNING", "startedAt": "2026-11-17T10:00:00Z", "relaunchCount": 0, "workers": [{"kind": "ALL_ACTIONS",
                 "state": "WORKING", "listingId": 3, "action": "DELETION", "relaunchCount": 0}],
                 "skippedListingIds": []}""");
        runButton().click();
        // Alors la série est lancée, le bouton est masqué et la page affiche l'état
        assertThat(runSummary()).hasText("Traitement en cours : annonce 3.");
        assertThat(runButton()).isHidden();
        assertThat(launchedRuns).containsExactly("POST");
        // Puis le résultat, une fois la série terminée, et le bouton revient
        runOverview.set("""
                {"status": "FINISHED", "startedAt": "2026-11-17T10:00:00Z", "endedAt": "2026-11-17T10:07:00Z",
                 "relaunchCount": 2, "skippedListingIds": [5]}""");
        assertThat(runSummary()).hasText("Traitement terminé : 2 relances, 1 annonce écartée.");
        assertThat(runButton()).isVisible();
    }

    @Test
    void skipped_listings_link_to_failures_page() {
        // US-39 AT-39-07. Étant donné une série terminée avec 4 relances et 2 annonces écartées
        runOverview.set("""
                {"status": "FINISHED", "startedAt": "2026-11-17T10:00:00Z", "endedAt": "2026-11-17T10:07:00Z",
                 "relaunchCount": 4, "skippedListingIds": [5, 6]}""");
        // Quand le membre regarde la page du dressing
        open();
        // Alors « 2 annonces écartées » est un lien vers la page « Échecs »
        assertThat(runSummary()).hasText("Traitement terminé : 4 relances, 2 annonces écartées.");
        assertThat(runSummary().getByRole(AriaRole.LINK)).hasText("2 annonces écartées");
        assertThat(runSummary().getByRole(AriaRole.LINK)).hasAttribute("href", "#/failures");
    }

    @Test
    void no_link_without_skipped_listing() {
        runOverview.set("""
                {"status": "FINISHED", "startedAt": "2026-11-17T10:00:00Z", "endedAt": "2026-11-17T10:07:00Z",
                 "relaunchCount": 4, "skippedListingIds": []}""");
        open();
        assertThat(runSummary()).hasText("Traitement terminé : 4 relances, 0 annonce écartée.");
        assertThat(runSummary().getByRole(AriaRole.LINK)).hasCount(0);
    }

    @Test
    void shows_each_worker_of_the_run() {
        // AT-46-12
        // Étant donné une série en mode WORKERS : republication de « Bonnet » (annonce 1) en cours, suppression en
        // attente de cadence, vérification inactive
        inventory.set(TWO_LISTINGS);
        runOverview.set("""
                {"status": "RUNNING", "startedAt": "2026-11-17T10:00:00Z", "relaunchCount": 0, "workers": [
                  {"kind": "REPUBLICATION", "state": "WORKING", "listingId": 1, "action": "REPUBLICATION",
                   "relaunchCount": 0},
                  {"kind": "DELETION", "state": "WAITING", "waitingUntil": "2026-11-17T10:05:00Z",
                   "waitingReason": "CADENCE", "relaunchCount": 0},
                  {"kind": "VERIFICATION", "state": "IDLE", "relaunchCount": 0}],
                 "skippedListingIds": [], "pauseRequested": false, "stopRequested": false}""");
        // Quand le membre ouvre la page du dressing
        open();
        // Alors il voit une ligne par worker
        assertThat(runSummary()).hasText("Traitement en cours.");
        assertThat(page.locator("#run-workers li")).hasText(new String[]{
                "Republication : republie Bonnet (annonce 1)",
                "Suppression : en attente jusqu'au "
                        + page.evaluate("new Date('2026-11-17T10:05:00Z').toLocaleString('fr-FR')") + " (cadence)",
                "Vérification : rien à faire"});
    }

    @Test
    void shows_no_worker_line_for_a_sequential_run() {
        // AT-46-01
        runOverview.set(runningWith(0));
        open();
        assertThat(runSummary()).hasText("Traitement en cours : annonce 1.");
        assertThat(page.locator("#run-workers")).hasCount(0);
    }

    @Test
    void pauses_resumes_and_stops_a_run() {
        // Étant donné une série en cours
        runOverview.set(run("RUNNING", false, false, ""));
        open();
        assertThat(control("Pause")).isVisible();
        assertThat(control("Arrêter")).isVisible();
        assertThat(control("Reprendre")).isHidden();
        // Quand le membre clique sur « Pause »
        runOverview.set(run("RUNNING", true, false, ""));
        control("Pause").click();
        // Alors la demande est envoyée et la page dit qu'elle attend la fin de l'action en cours
        assertThat(runSummary()).hasText(
                "Traitement en cours : annonce 3. (pause demandée, à la fin de l'action en cours)");
        // Puis, la série en pause, « Reprendre » remplace « Pause »
        runOverview.set(run("PAUSED", false, false, ""));
        assertThat(runSummary()).hasText("Traitement en pause.");
        assertThat(control("Reprendre")).isVisible();
        assertThat(control("Pause")).isHidden();
        // Quand il clique sur « Reprendre », puis sur « Arrêter »
        runOverview.set(run("RUNNING", false, false, ""));
        control("Reprendre").click();
        assertThat(control("Pause")).isVisible();
        runOverview.set(run("RUNNING", false, true, ""));
        control("Arrêter").click();
        assertThat(runSummary()).hasText(
                "Traitement en cours : annonce 3. (arrêt demandé, à la fin de l'action en cours)");
        // Alors la série s'arrête, et seul « Tout lancer » est proposé
        runOverview.set(run("STOPPED", false, false, ", \"stopReason\": \"STOPPED_BY_MEMBER\""));
        assertThat(runSummary()).hasText("Traitement arrêté à votre demande.");
        assertThat(control("Arrêter")).isHidden();
        assertThat(runButton()).isVisible();
        assertThat(runControls).containsExactly("POST pause", "POST resume", "POST stop");
    }

    private static String run(String status, boolean pauseRequested, boolean stopRequested, String more) {
        return "{\"status\": \"" + status + "\", \"startedAt\": \"2026-11-17T10:00:00Z\","
                + " \"workers\": [{\"kind\": \"ALL_ACTIONS\","
                + " \"state\": \"WORKING\", \"listingId\": 3, \"action\": \"DELETION\", \"relaunchCount\": 1}],"
                + " \"relaunchCount\": 1, \"skippedListingIds\": [], \"pauseRequested\": " + pauseRequested
                + ", \"stopRequested\": " + stopRequested + more + "}";
    }

    private Locator control(String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    @Test
    void shows_a_waiting_and_a_stopped_run() {
        // Étant donné une série en attente
        runOverview.set("""
                {"status": "WAITING", "startedAt": "2026-11-17T10:00:00Z", "waitingUntil": "2026-11-17T10:05:00Z",
                 "workers": [{"kind": "ALL_ACTIONS", "state": "WAITING", "waitingUntil": "2026-11-17T10:05:00Z",
                   "waitingReason": "CADENCE", "relaunchCount": 1}],
                 "relaunchCount": 1, "skippedListingIds": []}""");
        open();
        // Alors la page affiche la date de fin d'attente, sans proposer de relancer la série
        assertThat(runSummary()).hasText("En attente jusqu'au "
                + page.evaluate("new Date('2026-11-17T10:05:00Z').toLocaleString('fr-FR')") + ".");
        assertThat(runButton()).isHidden();
        // Quand la série s'arrête sur une session perdue
        runOverview.set("""
                {"status": "STOPPED", "startedAt": "2026-11-17T10:00:00Z", "endedAt": "2026-11-17T10:06:00Z",
                 "relaunchCount": 1, "skippedListingIds": [], "stopReason": "SESSION_DISCONNECTED"}""");
        // Alors la page dit pourquoi, et le bouton revient
        assertThat(runSummary()).hasText(
                "Traitement arrêté : la session Vinted n'est pas connectée (connectez-vous, puis relancez).");
        assertThat(runButton()).isVisible();
    }

    @Test
    void shows_the_verification_progress_and_outcome() {
        // AT-40-12. Étant donné la page du dressing, sans vérification depuis le démarrage
        inventory.set("""
                {"listings": [
                   {"vintedId": 3, "title": "Pull", "price": {"amount": 12.5, "currency": "EUR"},
                    "url": "https://vinted.test/items/3", "present": true, "stage": "TO_VALIDATE"},
                   {"vintedId": 1, "title": "Bonnet", "price": {"amount": 1, "currency": "EUR"},
                    "url": "https://vinted.test/items/1", "present": true, "stage": "TO_VALIDATE"}]}""");
        open();
        assertThat(verifyButton()).isVisible();
        assertThat(verificationSummary()).hasText("");
        // Quand le membre clique sur « Vérifier et valider »
        verification.set("""
                {"status": "RUNNING", "verifiedCount": 1, "totalCount": 2, "validatedCount": 1, "failedCount": 0}""");
        verifyButton().click();
        // Alors la page suit l'avancement, sans proposer d'autre action
        assertThat(verificationSummary()).hasText("Vérification en cours : 1 / 2");
        assertThat(verifyButton()).isHidden();
        assertThat(button()).isHidden();
        assertThat(backupButton()).isHidden();
        assertThat(runButton()).isHidden();
        assertThat(launchedVerifications).containsExactly("POST");
        // Puis le résultat, et la liste relue montre les étapes « prête à supprimer » et « vérification en erreur »
        inventory.set("""
                {"listings": [
                   {"vintedId": 3, "title": "Pull", "price": {"amount": 12.5, "currency": "EUR"},
                    "url": "https://vinted.test/items/3", "present": true, "stage": "TO_DELETE"},
                   {"vintedId": 1, "title": "Bonnet", "price": {"amount": 1, "currency": "EUR"},
                    "url": "https://vinted.test/items/1", "present": true, "stage": "VERIFICATION_FAILED"}]}""");
        verification.set("""
                {"status": "FINISHED", "verifiedCount": 2, "totalCount": 2, "validatedCount": 1, "failedCount": 1}""");
        assertThat(verificationSummary()).hasText("Vérification terminée : 1 validée, 1 en erreur");
        assertThat(cell(1, STAGE)).hasText("Vérification en erreur");
        assertThat(cell(0, STAGE)).hasText("Prête à supprimer");
        assertThat(verifyButton()).isVisible();
        assertThat(runButton()).isVisible();
        // Et les annonces en erreur se filtrent
        page.locator("#backup-filter").selectOption("VERIFICATION_FAILED");
        page.waitForCondition(() -> listingQueries.getLast() != null
                && listingQueries.getLast().contains("backup=VERIFICATION_FAILED"));
    }

    @Test
    void shows_why_the_verification_stopped() {
        verification.set("""
                {"status": "STOPPED", "verifiedCount": 1, "totalCount": 3, "validatedCount": 1, "failedCount": 0,
                 "stopReason": "SESSION_DISCONNECTED"}""");

        open();

        assertThat(verificationSummary()).hasText(
                "Vérification arrêtée : session Vinted expirée (connectez-vous, puis relancez)");
        assertThat(verifyButton()).isVisible();
    }

    @Test
    void hides_verify_and_validate_while_a_run_is_in_progress() {
        // AT-40-10. Étant donné une série « Tout lancer » en cours
        runOverview.set(run("RUNNING", false, false, ""));
        // Quand j'ouvre la page du dressing
        open();
        // Alors le bouton « Vérifier et valider » est masqué
        assertThat(control("Pause")).isVisible();
        assertThat(verifyButton()).isHidden();
    }

    @Test
    void hides_verify_and_validate_while_a_backup_is_in_progress() {
        overview.set("{\"scanInProgress\": false, \"backupInProgress\": true}");

        open();

        assertThat(backupProgress()).isVisible();
        assertThat(verifyButton()).isHidden();
    }

    private Locator verifyButton() {
        return page.locator("#verify-and-validate");
    }

    private Locator verificationSummary() {
        return page.locator("#verification-summary");
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
        // Alors le titre de chacune mène à sa fiche
        //   et aucune ligne n'a de lien « Voir la sauvegarde »
        assertThat(rows().nth(0).getByRole(AriaRole.LINK)).hasAttribute("href", "#/listings/3");
        assertThat(rows().nth(0).getByRole(AriaRole.LINK)).hasText("Pull");
        assertThat(rows().nth(1).getByRole(AriaRole.LINK)).hasAttribute("href", "#/listings/1");
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
    void statistics_refresh_button_and_state() {
        open();
        assertThat(page.locator("#statistics-summary")).hasText("Vues et favoris jamais relevés pour l'instant.");

        page.locator("#statistics-refresh").click();

        page.waitForCondition(() -> !launchedRefreshes.isEmpty());
        assertThat(launchedRefreshes).containsExactly("POST");
    }

    @Test
    void follows_a_statistics_refresh_until_done_without_offering_another_action() {
        overview.set("""
                {"scanInProgress": false, "backupInProgress": false, "statisticsRefreshInProgress": true}""");
        open();
        assertThat(page.locator("#statistics-progress")).isVisible();
        assertThat(page.locator("#statistics-refresh")).isHidden();
        assertThat(button()).isHidden();
        assertThat(backupButton()).isHidden();

        overview.set("""
                {"scanInProgress": false, "backupInProgress": false, "statisticsRefreshInProgress": false,
                 "lastStatisticsReadAt": "2026-11-17T09:40:00Z"}""");

        assertThat(page.locator("#statistics-progress"))
                .isHidden(new LocatorAssertions.IsHiddenOptions().setTimeout(5000));
        assertThat(page.locator("#statistics-refresh")).isVisible();
        assertThat(page.locator("#statistics-summary")).containsText("Vues et favoris relevés le 17/11/2026");
    }

    @Test
    void explains_a_refresh_without_statistics_shown() {
        overview.set("""
                {"scanInProgress": false, "backupInProgress": false, "statisticsRefreshInProgress": false,
                 "statisticsRefreshProblem": "NO_STATISTICS_SHOWN"}""");

        open();

        assertThat(page.locator("#statistics-problem")).hasText("Aucune carte du dressing ne montre ses vues et "
                + "favoris : vérifiez que la session Vinted est connectée. Aucun relevé n'a été ajouté.");
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
    private static final int ADVICE = 4;
    private static final int PRESENCE = 5;
    private static final int BACKUP = 6;
    private static final int VALIDATION = 7;
    private static final int STAGE = 8;
    private static final int REPUBLICATION = 9;
    private static final int REPUBLICATIONS = 10;
    private static final int ACTION = 11;
    private static final String TO_DELETE_AND_TO_VALIDATE = """
            {"listings": [
               {"vintedId": 3, "title": "Pull", "price": {"amount": 12.5, "currency": "EUR"},
                "url": "https://vinted.test/items/3", "present": true, "stage": "TO_DELETE"},
               {"vintedId": 1, "title": "Bonnet", "price": {"amount": 1, "currency": "EUR"},
                "url": "https://vinted.test/items/1", "present": true, "stage": "TO_VALIDATE"}]}""";

    private void answer(HttpExchange exchange, String json) throws IOException {
        if (applicationDown.get()) {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        } else {
            respond(exchange, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private Locator runButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Tout lancer").setExact(true));
    }

    private Locator runSummary() {
        return page.locator("#run-summary");
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

    @Test
    void downloads_data_archive_and_shows_refusal() throws IOException {
        // US-26 AT-26-06. Étant donné la page du dressing
        open();

        // Quand le membre clique sur « Télécharger une archive »
        Download download = page.waitForDownload(() -> archiveButton().click());

        // Alors le navigateur reçoit le zip en téléchargement
        assertThat(download.suggestedFilename()).isEqualTo("vinted-republisher-archive-2026-11-17T14-05-09.zip");
        try (InputStream content = download.createReadStream()) {
            assertThat(content.readAllBytes()).isEqualTo(ARCHIVE);
        }
        assertThat(archiveProblem()).isHidden();

        // Et un refus ou un échec s'affiche à côté du bouton
        archiveStatus.set(409);
        archiveButton().click();
        assertThat(archiveProblem()).hasText("Une action navigateur est en cours : réessayez quand elle sera finie.");

        archiveStatus.set(500);
        archiveButton().click();
        assertThat(archiveProblem()).hasText("L'archive n'a pas pu être créée : rien n'a été téléchargé. Voir les logs.");

        archiveStatus.set(200);
        page.waitForDownload(() -> archiveButton().click());
        assertThat(archiveProblem()).isHidden();
    }

    private static String runningWith(int relaunchCount) {
        return """
                {"status": "RUNNING", "startedAt": "2026-11-17T10:00:00Z", "workers": [{"kind": "ALL_ACTIONS", "state": "WORKING",
                 "listingId": 1, "action": "DELETION", "relaunchCount": 0}],
                 "relaunchCount": %d, "skippedListingIds": []}""".formatted(relaunchCount);
    }

    /** AT-42-01 */
    @Test
    void run_overview_unchanged_does_not_reload_listings() {
        // Étant donné une série en cours dont l'aperçu ne change pas pendant trois périodes de suivi
        runOverview.set(runningWith(3));
        int listingReadings = openFollowing();
        // Quand le membre reste sur la page « Dressing »
        followPeriods(3, "/api/automatic-run");
        settle();
        // Alors la liste n'est pas relue pendant ces trois périodes
        assertThat(listingQueries).hasSize(listingReadings);
    }

    /** AT-42-02 */
    @Test
    void run_progress_reloads_listings() {
        // Étant donné une série en cours
        runOverview.set(runningWith(3));
        int listingReadings = openFollowing();
        // Quand son nombre d'annonces relancées passe de 3 à 4
        runOverview.set(runningWith(4));
        followPeriods(2, "/api/automatic-run");
        // Alors la liste est relue une fois
        assertListingReadings(listingReadings + 1);
    }

    /** AT-42-03 */
    @Test
    void scan_end_reloads_listings_once_more() {
        // Étant donné un scan en cours
        overview.set(SCANNING);
        int listingReadings = openFollowing();
        // Quand le scan se termine
        overview.set(SCANNED);
        followPeriods(1, "/api/wardrobe");
        // Alors la liste est relue une dernière fois et le suivi s'arrête
        assertListingReadings(listingReadings + 1);
        int wardrobeFollowUps = wardrobeReadings.size();
        page.clock().runFor(3 * PERIOD_MS);
        settle();
        assertThat(listingQueries).hasSize(listingReadings + 1);
        assertThat(wardrobeReadings).hasSize(wardrobeFollowUps);
    }

    /** AT-42-04 */
    @Test
    void run_and_scan_share_one_follow_up() {
        // Étant donné une série en cours
        runOverview.set(runningWith(0));
        // Quand le membre lance un scan
        overview.set(SCANNING);
        int listingReadings = openFollowing();
        for (int period = 1; period <= 3; period++) {
            runOverview.set(runningWith(period));
            followPeriods(1, "/api/automatic-run");
        }
        // Alors la liste n'est relue qu'une fois par période de suivi au plus
        assertListingReadings(listingReadings + 3);
    }

    /** AT-42-06 */
    @Test
    void scan_in_progress_reloads_listings_every_period() {
        // Étant donné un scan en cours
        overview.set(SCANNING);
        int listingReadings = openFollowing();
        // Quand trois périodes de suivi passent sans que l'aperçu du dressing change
        followPeriods(3, "/api/wardrobe");
        // Alors la liste est relue à chacune des trois périodes
        assertListingReadings(listingReadings + 3);
    }

    /** AT-42-07 */
    @Test
    void manual_relaunch_message_stays_while_run_unchanged() {
        // Étant donné une série en cours
        inventory.set(TO_DELETE_AND_TO_VALIDATE);
        runOverview.set(runningWith(3));
        relaunchAnswer.set("""
                {"action": "DELETION", "dryRun": false,
                 "listing": {"vintedId": 3, "title": "Pull", "price": {"amount": 12.5, "currency": "EUR"},
                             "url": "https://vinted.test/items/3", "present": false, "stage": "WAITING"}}""");
        openFollowing();
        // Quand le membre relance une annonce depuis sa ligne et que l'aperçu de la série ne change pas pendant deux
        //   périodes
        relaunchAnswered.countDown();
        cell(0, ACTION).getByRole(AriaRole.BUTTON).click();
        assertThat(cell(0, ACTION)).containsText("Suppression faite");
        followPeriods(2, "/api/automatic-run");
        settle();
        // Alors le message de la relance reste affiché sous la ligne
        assertThat(cell(0, ACTION)).containsText("Suppression faite");
    }

    /**
     * Opens the page on a fake clock and lets its first period pass, the readings asked at opening being done by then:
     * how many times the list was read.
     */
    private int openFollowing() {
        page.clock().install();
        open();
        page.waitForCondition(() -> !runReadings.isEmpty() && !wardrobeReadings.isEmpty()
                && !listingQueries.isEmpty());
        page.clock().runFor(PERIOD_MS);
        settle();
        return listingQueries.size();
    }

    /** Lets {@code count} follow-up periods pass, each until the page has the reading of {@code path} it waited for. */
    private void followPeriods(int count, String path) {
        for (int period = 0; period < count; period++) {
            page.waitForResponse(response -> response.url().endsWith(path), () -> page.clock().runFor(PERIOD_MS));
        }
    }

    /** Waits for the list to be read {@code expected} times in all, then checks that no other reading follows. */
    private void assertListingReadings(int expected) {
        page.waitForCondition(() -> listingQueries.size() >= expected);
        settle();
        assertThat(listingQueries).hasSize(expected);
    }

    /** Leaves the page the time to ask what the last answers led to: nothing is expected to come later. */
    private void settle() {
        page.waitForTimeout(SETTLE_MS);
    }

    private Locator archiveButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Télécharger une archive"));
    }

    private Locator archiveProblem() {
        return page.locator("#archive-problem");
    }

    private void open() {
        BuiltFrontend.open(page, fakeApi, "");
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
