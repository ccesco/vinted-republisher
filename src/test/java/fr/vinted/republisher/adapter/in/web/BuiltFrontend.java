package fr.vinted.republisher.adapter.in.web;

import com.microsoft.playwright.Page;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Serves the built Angular interface ({@code static/} on the classpath, filled by the Maven build) from a test
 * server that also simulates the API: the pages under test are the ones the application ships.
 */
final class BuiltFrontend {

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
        String file = "/".equals(path) ? "/index.html" : path;
        boolean servable = !path.contains("..") && !path.startsWith("/api/");
        try (exchange; InputStream content = servable ? BuiltFrontend.class.getResourceAsStream("/static" + file) : null) {
            if (content == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] body = content.readAllBytes();
            String extension = file.substring(file.lastIndexOf('.') + 1);
            exchange.getResponseHeaders().add("Content-Type", CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"));
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }
    }
}
