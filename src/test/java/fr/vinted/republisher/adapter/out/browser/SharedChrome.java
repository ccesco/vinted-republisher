package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * One installed Chrome, started as the tool starts it, for the test class that registers it and its nested classes:
 * starting then closing a Chrome with a fresh profile for every test cost 6 to 8 seconds each. After each test, it
 * is given back as new: a single blank tab, no cookie, no cache. Its debug port is free and never fixed.
 */
final class SharedChrome implements BeforeAllCallback, AfterEachCallback, AfterAllCallback {

    static final Path INSTALLED_CHROME = Path.of(Objects.requireNonNullElse(
            System.getenv("CHROME_PATH"), "C:/Program Files/Google/Chrome/Application/chrome.exe"));

    private String startedFor;
    private Path profileDirectory;
    private int debugPort;
    private Playwright playwright;
    private Browser connected;

    @Override
    public void beforeAll(ExtensionContext context) throws IOException {
        if (startedFor != null) {
            return;
        }
        startedFor = context.getUniqueId();
        profileDirectory = Files.createTempDirectory("chrome-profile-");
        debugPort = freePort();
        chrome().ensureRunning();
        // One connection for the whole class: creating a Playwright costs a driver process start.
        playwright = Playwright.create();
        connected = playwright.chromium().connectOverCDP(endpoint());
    }

    /** The Chrome the code under test is given: already running, so reused. */
    Chrome chrome() {
        return new Chrome(INSTALLED_CHROME, profileDirectory, debugPort);
    }

    String endpoint() {
        return "http://127.0.0.1:" + debugPort;
    }

    @Override
    public void afterEach(ExtensionContext context) {
        BrowserContext defaultContext = connected.contexts().getFirst();
        Page blank = defaultContext.newPage();
        defaultContext.pages().stream().filter(page -> page != blank).forEach(Page::close);
        defaultContext.clearCookies();
        defaultContext.newCDPSession(blank).send("Network.clearBrowserCache");
    }

    @Override
    public void afterAll(ExtensionContext context) throws IOException {
        if (!context.getUniqueId().equals(startedFor)) {
            return;
        }
        // Chrome must be gone before its profile is deleted: a killed Chrome still writes to it.
        List<ProcessHandle> chromeProcesses = ProcessHandle.current().descendants().toList();
        try {
            connected.newBrowserCDPSession().send("Browser.close");
        } catch (PlaywrightException alreadyClosed) {
            // Nothing left to close.
        }
        playwright.close();
        chromeProcesses.forEach(process -> process.onExit().orTimeout(10, TimeUnit.SECONDS).join());
        delete(profileDirectory);
    }

    static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void delete(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder()).forEach(file -> {
                try {
                    Files.delete(file);
                } catch (IOException locked) {
                    throw new UncheckedIOException(locked);
                }
            });
        }
    }
}
