package fr.vinted.republisher.adapter.out.browser;

import fr.vinted.republisher.domain.session.BrowserUnavailable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * The installed Chrome, started as a normal browser: a Chrome launched by Playwright carries
 * automation marks that Vinted's anti-bot blocks. Chrome refuses remote debugging on the default
 * profile, hence a dedicated one. It stays open between actions and is reused if already running.
 */
public class Chrome {

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private final Path executable;
    private final Path profileDirectory;
    private final int debugPort;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

    public Chrome(Path executable, Path profileDirectory, int debugPort) {
        this.executable = executable;
        this.profileDirectory = profileDirectory;
        this.debugPort = debugPort;
    }

    /** Returns the endpoint Playwright attaches to. */
    URI ensureRunning() {
        if (!responds()) {
            start();
            awaitStartup();
        }
        return endpoint();
    }

    private void start() {
        try {
            new ProcessBuilder(executable.toString(),
                    "--remote-debugging-port=" + debugPort,
                    "--user-data-dir=" + profileDirectory.toAbsolutePath(),
                    "--no-first-run",
                    "--no-default-browser-check")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException notFound) {
            throw new BrowserUnavailable("Chrome cannot be started from " + executable, notFound);
        }
    }

    private void awaitStartup() {
        Instant deadline = Instant.now().plus(STARTUP_TIMEOUT);
        while (!responds()) {
            if (Instant.now().isAfter(deadline)) {
                throw new BrowserUnavailable("Chrome does not answer on debug port " + debugPort);
            }
            pause();
        }
    }

    private boolean responds() {
        HttpRequest request = HttpRequest.newBuilder(endpoint().resolve("/json/version"))
                .timeout(Duration.ofSeconds(1))
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException notRunning) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BrowserUnavailable("interrupted while reaching Chrome", interrupted);
        }
    }

    private static void pause() {
        try {
            Thread.sleep(POLL_INTERVAL);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BrowserUnavailable("interrupted while waiting for Chrome", interrupted);
        }
    }

    private URI endpoint() {
        return URI.create("http://127.0.0.1:" + debugPort);
    }
}
