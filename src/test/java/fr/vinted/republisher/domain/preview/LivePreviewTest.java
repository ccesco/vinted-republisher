package fr.vinted.republisher.domain.preview;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LivePreviewTest {

    private static final Screenshot RED_PAGE = new Screenshot(new byte[]{1, 2, 3}, Instant.parse("2026-11-18T10:00:00Z"));

    @Test
    void previews_are_sorted_by_browser() {
        LivePreview second = LivePreview.captured("Chrome (port 9333)", RED_PAGE);
        LivePreview first = LivePreview.withoutScreenshot("Chrome (port 9222)");

        assertThat(LivePreview.sortedByBrowser(List.of(second, first))).containsExactly(first, second);
    }

    @Test
    void a_preview_without_screenshot_has_none() {
        assertThat(LivePreview.withoutScreenshot("Chrome (port 9222)").screenshot()).isEmpty();
    }

    @Test
    void screenshots_with_the_same_image_and_date_are_equal() {
        Screenshot same = new Screenshot(new byte[]{1, 2, 3}, Instant.parse("2026-11-18T10:00:00Z"));

        assertThat(same).isEqualTo(RED_PAGE).hasSameHashCodeAs(RED_PAGE);
        assertThat(LivePreview.captured("Chrome (port 9222)", same).screenshot()).isEqualTo(Optional.of(RED_PAGE));
    }

    @Test
    void a_screenshot_keeps_its_own_copy_of_the_image() {
        byte[] image = {1, 2, 3};
        Screenshot screenshot = new Screenshot(image, Instant.parse("2026-11-18T10:00:00Z"));

        image[0] = 9;
        screenshot.image()[1] = 9;

        assertThat(screenshot.image()).containsExactly(1, 2, 3);
    }
}
