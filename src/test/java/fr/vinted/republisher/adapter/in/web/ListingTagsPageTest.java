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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-34 on the static wardrobe page in the installed Chrome, against a simulated API answering as the contract says:
 * never the real application nor Vinted.
 */
@Tag("browser")
class ListingTagsPageTest {

    private static final String IDLE = "{\"scanInProgress\": false, \"backupInProgress\": false}";
    private static final String IDLE_RUN = "{\"status\": \"IDLE\", \"relaunchCount\": 0, \"skippedListingIds\": []}";
    private static final String TWO_LISTINGS = """
            {"listings": [
              {"vintedId": 2, "title": "Pull", "price": {"amount": 12.5, "currency": "EUR"},
               "url": "https://vinted.test/items/2", "present": true, "stage": "TO_BACK_UP",
               "tags": ["<b>lot</b>", "été"]},
              {"vintedId": 1, "title": "Bonnet", "price": {"amount": 1, "currency": "EUR"},
               "url": "https://vinted.test/items/1", "present": true, "stage": "TO_BACK_UP", "tags": []}]}""";
    private static final String TAGS = "{\"tags\": [\"<b>lot</b>\", \"été\"]}";

    private static Playwright playwright;
    private static Browser chrome;

    private HttpServer fakeApi;
    private final List<String> listingQueries = new CopyOnWriteArrayList<>();
    private final List<String> tagEdits = new CopyOnWriteArrayList<>();
    private final AtomicInteger tagReads = new AtomicInteger();
    private final AtomicInteger tagEditStatus = new AtomicInteger(204);
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
        fakeApi.createContext("/api/wardrobe", exchange -> respond(exchange, 200, IDLE));
        fakeApi.createContext("/api/automatic-run", exchange -> respond(exchange, 200, IDLE_RUN));
        fakeApi.createContext("/api/listings", exchange -> {
            listingQueries.add(URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            respond(exchange, 200, TWO_LISTINGS);
        });
        fakeApi.createContext("/api/listing-tags", exchange -> {
            tagReads.incrementAndGet();
            respond(exchange, 200, TAGS);
        });
        fakeApi.createContext("/api/listings/tag-edits", exchange -> {
            tagEdits.add(exchange.getRequestMethod() + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(tagEditStatus.get(), -1);
            exchange.close();
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
    void selects_listings_edits_their_tags_and_filters_on_a_tag() {
        // Étant donné une API qui renvoie deux annonces, dont une portant le tag "<b>lot</b>",
        //   et les tags proposés « <b>lot</b> »
        // Quand le membre ouvre wardrobe.html
        open();

        // Alors la colonne Tags montre "<b>lot</b>" tel quel et le filtre Tag propose « Tous » et "<b>lot</b>"
        //   et les boutons d'édition sont inactifs
        assertThat(rows()).hasCount(2);
        assertThat(rows().nth(0)).containsText("<b>lot</b>, été");
        assertThat(page.locator("#tag-filter option")).hasText(new String[]{"Tous", "<b>lot</b>", "été"});
        assertThat(addButton()).isDisabled();
        assertThat(removeButton()).isDisabled();

        // Quand il coche toutes les lignes, saisit « été » et clique « Ajouter le tag »
        page.locator("#select-all").check();
        assertThat(page.locator("#selection-count")).hasText("2 annonces sélectionnées");
        assertThat(addButton()).isDisabled();
        page.locator("#tag-name").fill("été");
        int readsBefore = tagReads.get();
        int queriesBefore = listingQueries.size();
        addButton().click();

        // Alors la page envoie POST /api/listings/tag-edits avec les deux identifiants, "été" et ADD
        //   et affiche « Tag « été » ajouté à 2 annonces », puis relit les tags et la liste
        assertThat(page.locator("#tag-edit-message")).hasText("Tag « été » ajouté à 2 annonces");
        assertThat(tagEdits).containsExactly(
                "POST {\"listingIds\":[2,1],\"tag\":\"été\",\"operation\":\"ADD\"}");
        page.waitForCondition(() -> tagReads.get() > readsBefore && listingQueries.size() > queriesBefore);
        assertThat(page.locator("#selection-count")).hasText("0 annonce sélectionnée");

        // Quand il choisit le tag "<b>lot</b>" dans le filtre
        page.locator("#tag-filter").selectOption("<b>lot</b>");

        // Alors la page demande GET /api/listings avec tag=<b>lot</b>
        page.waitForCondition(() -> listingQueries.stream().anyMatch(query -> query.endsWith("&tag=<b>lot</b>")));
    }

    @Test
    void says_when_a_selected_listing_is_no_longer_known() {
        tagEditStatus.set(404);
        open();
        rows().nth(1).locator("input[type=checkbox]").check();
        page.locator("#tag-name").fill("lot");

        // Quand l'API refuse une édition par 404
        removeButton().click();

        // Alors la page affiche « Une annonce sélectionnée n'est plus connue : aucun tag modifié »
        assertThat(page.locator("#tag-edit-message"))
                .hasText("Une annonce sélectionnée n'est plus connue : aucun tag modifié");
        assertThat(tagEdits).containsExactly("POST {\"listingIds\":[1],\"tag\":\"lot\",\"operation\":\"REMOVE\"}");
    }

    @Test
    void says_when_the_tag_is_invalid() {
        tagEditStatus.set(400);
        open();
        rows().nth(0).locator("input[type=checkbox]").check();
        page.locator("#tag-name").fill("lot");

        addButton().click();

        assertThat(page.locator("#tag-edit-message")).hasText("Tag invalide (1 à 30 caractères) : aucun tag modifié");
    }

    private void open() {
        page.navigate("http://127.0.0.1:" + fakeApi.getAddress().getPort() + "/wardrobe.html");
    }

    private Locator rows() {
        return page.locator("#listings tbody tr");
    }

    private Locator addButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Ajouter le tag"));
    }

    private Locator removeButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Retirer le tag"));
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        respond(exchange, status, "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
