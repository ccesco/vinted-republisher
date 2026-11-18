package fr.vinted.republisher.adapter.out.browser;

import com.google.gson.JsonObject;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;
import fr.vinted.republisher.domain.preview.LivePreview;
import fr.vinted.republisher.domain.preview.LivePreviews;
import fr.vinted.republisher.domain.preview.Screenshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The working tabs of the piloted Chromes, noted by {@link ChromeTabs} while it works in them, and captured on demand
 * over a debugging connection of their own: Playwright only serves the thread at work, and a second Playwright
 * connection would act on every tab (dialogs dismissed, scripts added). That connection does nothing but
 * {@code Page.captureScreenshot}: no domain enabled, no navigation, no input. The tabs are captured at the same time,
 * each within one deadline, connection included.
 */
public class ChromeLivePreviews implements LivePreviews {

    private static final Logger LOG = LoggerFactory.getLogger(ChromeLivePreviews.class);
    private static final Duration CAPTURE_TIMEOUT = Duration.ofSeconds(3);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    // One client, hence one selector thread, for every registry.
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String CAPTURE = """
            {"id": 1, "method": "Page.captureScreenshot", "params": {"format": "jpeg", "quality": 60}}""";

    private final Map<String, WorkingTab> workingTabs = new ConcurrentHashMap<>();
    private final Clock clock;

    public ChromeLivePreviews(Clock clock) {
        this.clock = clock;
    }

    /** A noted tab, forgotten when closed. */
    interface Watch extends AutoCloseable {
        @Override
        void close();
    }

    /** Notes the tab while the work goes on; a tab that cannot be noted is simply not shown. */
    Watch watch(Chrome chrome, Page page) {
        Optional<String> targetId = targetIdOf(page);
        if (targetId.isEmpty()) {
            return () -> { };
        }
        String tab = targetId.get();
        workingTabs.put(tab, new WorkingTab(chrome.name(), chrome.tabDebugger(tab)));
        return () -> workingTabs.remove(tab);
    }

    @Override
    public List<LivePreview> captureAll() {
        List<CompletableFuture<LivePreview>> captures = workingTabs.values().stream().map(this::capture).toList();
        return LivePreview.sortedByBrowser(captures.stream().map(CompletableFuture::join).toList());
    }

    /** Never fails: a capture that fails or comes too late gives a preview without screenshot. */
    private CompletableFuture<LivePreview> capture(WorkingTab tab) {
        CompletableFuture<String> answer = new CompletableFuture<>();
        CompletableFuture<WebSocket> connection = HTTP.newWebSocketBuilder()
                .connectTimeout(CAPTURE_TIMEOUT)
                .buildAsync(tab.debugger(), new Answer(answer));
        return connection
                .thenCompose(socket -> socket.sendText(CAPTURE, true))
                .thenCompose(sent -> answer)
                .orTimeout(CAPTURE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((response, failure) -> connection.thenAccept(WebSocket::abort))
                .thenApply(response -> LivePreview.captured(tab.browser(),
                        new Screenshot(screenshotIn(response), clock.instant())))
                .exceptionally(failure -> {
                    LOG.info("No live preview of {}: {}", tab.browser(), failure.toString());
                    return LivePreview.withoutScreenshot(tab.browser());
                });
    }

    private static byte[] screenshotIn(String response) {
        JsonNode answer = JSON.readTree(response);
        JsonNode data = answer.path("result").path("data");
        if (!data.isString()) {
            throw new IllegalArgumentException("no screenshot in " + answer.path("error"));
        }
        return Base64.getDecoder().decode(data.asString());
    }

    private static Optional<String> targetIdOf(Page page) {
        try {
            CDPSession session = page.context().newCDPSession(page);
            try {
                JsonObject info = session.send("Target.getTargetInfo");
                return Optional.of(info.getAsJsonObject("targetInfo").get("targetId").getAsString());
            } finally {
                session.detach();
            }
        } catch (RuntimeException unknown) {
            LOG.info("Working tab not shown in the live preview: {}", unknown.toString());
            return Optional.empty();
        }
    }

    private record WorkingTab(String browser, URI debugger) {
    }

    /** Collects the one answer awaited; Chrome sends nothing else, no domain being enabled. */
    private static final class Answer implements WebSocket.Listener {

        private final CompletableFuture<String> answer;
        private final StringBuilder received = new StringBuilder();

        Answer(CompletableFuture<String> answer) {
            this.answer = answer;
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence part, boolean last) {
            received.append(part);
            if (last) {
                answer.complete(received.toString());
            } else {
                socket.request(1);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            answer.completeExceptionally(new IllegalStateException("closed by Chrome: " + statusCode + " " + reason));
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            answer.completeExceptionally(error);
        }
    }
}
