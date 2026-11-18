package fr.vinted.republisher.adapter.in.web;

import com.microsoft.playwright.Page;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Serves the built Angular interface ({@code frontend/dist/browser}, made by {@code npm run build}) from a test
 * server that also simulates the API: the pages under test are the ones the interface ships.
 */
final class BuiltFrontend {

    private static final Path BUILT_INTERFACE = Path.of("frontend", "dist", "browser").toAbsolutePath().normalize();
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "woff2", "font/woff2",
            "woff", "font/woff",
            "ico", "image/x-icon");

    private BuiltFrontend() {
    }

    /** Answers every path no other context of the server handles: the interface files, or 404. */
    static void serveFrom(HttpServer server) {
        if (!Files.isRegularFile(BUILT_INTERFACE.resolve("index.html"))) {
            throw new IllegalStateException("Interface non construite dans " + BUILT_INTERFACE
                    + " : lancer « npm run build » dans frontend/ avant les tests de pages.");
        }
        server.createContext("/", BuiltFrontend::serve);
    }

    /**
     * Opens a screen as a fresh page load, like typing its address: going from one screen address to another inside
     * the same page would only change the fragment and keep what the previous screen loaded.
     */
    static void open(Page page, HttpServer server, String route, Page.NavigateOptions options) {
        page.navigate("about:blank");
        page.navigate("http://127.0.0.1:" + server.getAddress().getPort() + "/#/" + route, options);
    }

    static void open(Page page, HttpServer server, String route) {
        open(page, server, route, new Page.NavigateOptions());
    }

    private static void serve(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        Path file = BUILT_INTERFACE.resolve(("/".equals(path) ? "index.html" : path.substring(1))).normalize();
        try (exchange) {
            if (path.startsWith("/api/") || !file.startsWith(BUILT_INTERFACE) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] body = Files.readAllBytes(file);
            String name = file.getFileName().toString();
            String extension = name.substring(name.lastIndexOf('.') + 1);
            exchange.getResponseHeaders().add("Content-Type", CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"));
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }
    }
}
