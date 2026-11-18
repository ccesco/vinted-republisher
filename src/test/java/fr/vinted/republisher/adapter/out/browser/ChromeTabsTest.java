package fr.vinted.republisher.adapter.out.browser;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** US-15: several actions work at once, each in its own tab of the one Chrome, against local pages only. */
@Tag("browser")
class ChromeTabsTest {

    @RegisterExtension
    static final SharedChrome CHROME = new SharedChrome();

    private HttpServer fakeVinted;

    @BeforeEach
    void startFakeVinted() throws IOException {
        fakeVinted = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeVinted.createContext("/items/", exchange -> page(exchange, exchange.getRequestURI().getPath()));
        fakeVinted.start();
    }

    @AfterEach
    void stopFakeVinted() {
        fakeVinted.stop(0);
    }

    @Test
    void two_actions_work_in_their_own_tab_at_the_same_time() {
        ChromeTabs tabs = new ChromeTabs(CHROME.chrome());
        // Both tabs are open before either reads its page: the actions run together
        CyclicBarrier bothOpen = new CyclicBarrier(2);
        try (ExecutorService twoActions = Executors.newFixedThreadPool(2)) {
            List<CompletableFuture<String>> titles = List.of("/items/1", "/items/2").stream()
                    .map(path -> CompletableFuture.supplyAsync(() -> tabs.inNewTab(page -> {
                        await(bothOpen);
                        ChromeTabs.open(page, address(path));
                        return page.title();
                    }), twoActions))
                    .toList();

            assertThat(titles.stream().map(title -> title.orTimeout(60, TimeUnit.SECONDS).join()).toList())
                    .containsExactly("/items/1", "/items/2");
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(30, TimeUnit.SECONDS);
        } catch (Exception notTogether) {
            throw new IllegalStateException("the tabs were not open at the same time", notTogether);
        }
    }

    private URI address(String path) {
        return URI.create("http://127.0.0.1:" + fakeVinted.getAddress().getPort() + path);
    }

    private static void page(HttpExchange exchange, String title) throws IOException {
        byte[] body = ("<html><head><title>" + title + "</title></head><body>" + title + "</body></html>")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
