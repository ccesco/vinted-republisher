package fr.vinted.republisher.domain.preview;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** What a piloted browser shows in its working tab: no screenshot when the capture failed. */
public record LivePreview(String browser, Optional<Screenshot> screenshot) {

    public LivePreview {
        Objects.requireNonNull(browser, "browser");
        Objects.requireNonNull(screenshot, "screenshot");
    }

    public static LivePreview captured(String browser, Screenshot screenshot) {
        return new LivePreview(browser, Optional.of(screenshot));
    }

    public static LivePreview withoutScreenshot(String browser) {
        return new LivePreview(browser, Optional.empty());
    }

    public static List<LivePreview> sortedByBrowser(Collection<LivePreview> previews) {
        return previews.stream().sorted(Comparator.comparing(LivePreview::browser)).toList();
    }
}
