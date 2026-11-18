package fr.vinted.republisher.adapter.in.web;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.ColorScheme;
import com.microsoft.playwright.options.WaitUntilState;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the six screens of the interface share (US-37): navigation, theme, phone layout and staying inside the
 * application, in the installed Chrome against a simulated API answering as the contract says.
 */
@Tag("browser")
class NavigationPageTest {

    private static final List<String> SCREENS = List.of("", "listings/1", "journal", "sales", "live-preview", "vinted-session");
    private static final String IDLE_WARDROBE =
            "{\"scanInProgress\": false, \"backupInProgress\": false, \"statisticsRefreshInProgress\": false}";
    private static final String LISTING = """
            {"vintedId": 1, "title": "Pull rayé", "price": {"amount": 12.5, "currency": "EUR"},
             "url": "https://vinted.test/items/1", "present": true, "stage": "TO_BACK_UP"}""";
    private static final String DETAIL = """
            {"listing": %s, "firstSeenAt": "2026-11-01T10:00:00Z", "lastSeenAt": "2026-11-17T10:00:00Z",
             "lifecycle": [{"type": "FIRST_SEEN", "listingId": 1, "at": "2026-11-01T10:00:00Z"}],
             "statisticsHistory": [], "journal": [], "priceHistory": []}""".formatted(LISTING);

    private static Playwright playwright;
    private static Browser chrome;

    private HttpServer fakeApi;
    private final AtomicReference<String> wardrobe = new AtomicReference<>(IDLE_WARDROBE);
    private final AtomicInteger wardrobeReads = new AtomicInteger();
    // Ouvert par défaut : seul le test qui le ferme fait attendre le deuxième relevé.
    private CountDownLatch slowWardrobeAnswer = new CountDownLatch(0);
    private BrowserContext context;
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
            if (wardrobeReads.incrementAndGet() == 2) {
                await(slowWardrobeAnswer);
            }
            respond(exchange, 200, wardrobe.get());
        });
        answer("/api/automatic-run", "{\"status\": \"IDLE\", \"relaunchCount\": 0, \"skippedListingIds\": []}");
        answer("/api/listing-tags", "{\"tags\": []}");
        answer("/api/journal", "{\"entries\": [], \"hasMore\": false}");
        answer("/api/sales", "{\"sales\": [], \"totals\": []}");
        answer("/api/live-previews", "{\"previews\": []}");
        answer("/api/vinted-session", "{\"status\": \"CONNECTED\"}");
        fakeApi.createContext("/api/listings", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/api/listings")) {
                respond(exchange, 200, "{\"listings\": [" + LISTING + "]}");
            } else if (path.equals("/api/listings/1")) {
                respond(exchange, 200, DETAIL);
            } else {
                respond(exchange, 404, "");
            }
        });
        fakeApi.setExecutor(Executors.newCachedThreadPool());
        fakeApi.start();
        context = chrome.newContext();
        page = context.newPage();
    }

    @AfterEach
    void stopFakeApi() {
        slowWardrobeAnswer.countDown();
        context.close();
        fakeApi.stop(0);
    }

    @Test
    void navigatesBetweenScreens() {
        // AT-37-02. Étant donné que je suis sur le Dressing
        BuiltFrontend.open(page, fakeApi, "");

        // Quand je clique sur une annonce
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Pull rayé")).click();

        // Alors la fiche de cette annonce s'ouvre
        assertThat(page.locator("#listing-title")).hasText("Pull rayé");
        assertThat(page.locator("#stage")).hasText("Étape : À sauvegarder");

        // Et la barre de navigation me ramène au Dressing, au Journal, aux Ventes, à l'Aperçu live ou à la Session Vinted
        navigateTo("Journal");
        assertThat(page.locator("h1")).hasText("Journal des actions");
        navigateTo("Ventes");
        assertThat(page.locator("h1")).hasText("Ventes");
        navigateTo("Aperçu live");
        assertThat(page.locator("h1")).hasText("Aperçu live");
        navigateTo("Session Vinted");
        assertThat(page.locator("h1")).hasText("Session Vinted");
        navigateTo("Dressing");
        assertThat(page.locator("h1")).hasText("Dressing");
        assertThat(page).hasURL(Pattern.compile("#/$"));
    }

    @Test
    void loadsNothingOutsideTheApplication() {
        // AT-37-03. Étant donné que j'ouvre chacun des six écrans
        String application = "http://127.0.0.1:" + fakeApi.getAddress().getPort() + "/";
        List<String> requested = new CopyOnWriteArrayList<>();
        page.onRequest(request -> requested.add(request.url()));
        for (String screen : SCREENS) {
            // Quand la page a fini de charger
            BuiltFrontend.open(page, fakeApi, screen, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
        }

        // Alors aucune requête n'est partie vers un autre hôte que l'application
        assertThat(requested).filteredOn(url -> !url.equals("about:blank")).isNotEmpty()
                .allSatisfy(url -> assertThat(url).startsWith(application));
    }

    @Test
    void followsSystemThemeAndRemembersTheChoice() {
        // AT-37-04. Étant donné que mon système est en thème sombre
        context.close();
        context = chrome.newContext(new Browser.NewContextOptions().setColorScheme(ColorScheme.DARK));
        page = context.newPage();

        // Quand j'ouvre l'interface
        BuiltFrontend.open(page, fakeApi, "");

        // Alors elle s'affiche en sombre
        assertThat(page.locator("h1")).hasText("Dressing");
        assertThat(colorScheme()).isEqualTo("dark");

        // Et quand je clique sur le bouton de thème, elle passe en clair et le reste après rechargement
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Passer en thème clair")).click();
        assertThat(colorScheme()).isEqualTo("light");
        page.reload();
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Passer en thème sombre")))
                .isVisible();
        assertThat(colorScheme()).isEqualTo("light");
    }

    @Test
    void fitsAPhoneScreen() {
        // AT-37-05. Étant donné une fenêtre de 375 px de large
        page.setViewportSize(375, 812);
        for (String screen : SCREENS) {
            // Quand j'ouvre chacun des six écrans
            BuiltFrontend.open(page, fakeApi, screen, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));

            // Alors la page ne défile pas horizontalement
            assertThat(page.evaluate("document.documentElement.scrollWidth"))
                    .as("largeur de l'écran « %s »", screen).isEqualTo(375);
            // Et la barre de navigation est en bas
            BoundingBox navigation = page.getByRole(AriaRole.NAVIGATION).boundingBox();
            assertThat(navigation.y + navigation.height).isEqualTo(812.0);
            assertThat(navigation.width).isEqualTo(375.0);
        }
    }

    @Test
    void stopsFollowingTheWardrobeOnceLeft() {
        // Étant donné un scan en cours suivi par le Dressing, dont un relevé tarde à répondre
        slowWardrobeAnswer = new CountDownLatch(1);
        wardrobe.set(IDLE_WARDROBE.replace("\"scanInProgress\": false", "\"scanInProgress\": true"));
        page.clock().install();
        BuiltFrontend.open(page, fakeApi, "");
        page.clock().runFor(2000);
        page.waitForCondition(() -> wardrobeReads.get() == 2);

        // Quand je passe au Journal avant la réponse
        navigateTo("Journal");
        assertThat(page.locator("h1")).hasText("Journal des actions");
        page.waitForResponse(response -> response.url().endsWith("/api/wardrobe"), slowWardrobeAnswer::countDown);

        // Alors le Dressing ne relit plus l'aperçu
        page.clock().runFor(10_000);
        assertThat(wardrobeReads).hasValue(2);
    }

    private void navigateTo(String screen) {
        Locator navigation = page.getByRole(AriaRole.NAVIGATION);
        navigation.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(screen).setExact(true)).click();
    }

    private Object colorScheme() {
        return page.evaluate("getComputedStyle(document.documentElement).colorScheme");
    }

    private void answer(String path, String json) {
        fakeApi.createContext(path, exchange -> respond(exchange, 200, json));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
