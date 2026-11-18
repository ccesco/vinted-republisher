package fr.vinted.republisher.adapter.out.browser;

import fr.vinted.republisher.domain.preview.LivePreview;
import fr.vinted.republisher.domain.preview.Screenshot;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US-22: the working tab of the installed Chrome, captured over its own debugging connection, on local pages only. */
@Tag("browser")
class ChromeLivePreviewsTest {

    @RegisterExtension
    static final SharedChrome CHROME = new SharedChrome();

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    private static final String RED_PAGE = "<body style='margin:0;background:#ff0000'></body>";

    private static final String WATCHFUL_PAGE = """
            <body style='margin:0;height:3000px'>
            <input id='field'>
            <script>
              window.untouched = true;
              window.counted = {click: 0, keydown: 0, scroll: 0, wheel: 0, pointerdown: 0, input: 0};
              for (const type of Object.keys(window.counted)) {
                window.addEventListener(type, () => window.counted[type]++, true);
              }
            </script>
            </body>""";

    private final ChromeLivePreviews previews = new ChromeLivePreviews(Clock.fixed(NOW, ZoneOffset.UTC));
    private final ChromeTabs tabs = new ChromeTabs(CHROME.chrome(), previews);

    @Test
    void shows_the_page_of_the_tab_at_work() {
        // Given l'outil qui travaille dans un onglet du Chrome piloté, sur une page entièrement rouge
        // When je demande les aperçus live
        List<LivePreview> captured = tabs.inNewTab(page -> {
            page.setContent(RED_PAGE);
            return previews.captureAll();
        });

        // Then je reçois un aperçu pour ce navigateur, nommé « Chrome (port <port de débogage>) »
        assertThat(captured).singleElement().satisfies(preview -> {
            assertThat(preview.browser()).isEqualTo("Chrome (port " + URI.create(CHROME.endpoint()).getPort() + ")");
            // And son image est une capture de la page rouge, datée du moment de la demande
            Screenshot screenshot = preview.screenshot().orElseThrow();
            assertThat(screenshot.capturedAt()).isEqualTo(NOW);
            assertThat(isRed(centreOf(screenshot))).isTrue();
        });
    }

    @Test
    void forgets_the_tab_once_closed() {
        // Given l'outil qui a fini son travail dans un onglet
        tabs.workInNewTab(page -> page.setContent(RED_PAGE));

        // When je demande les aperçus live
        // Then je ne reçois aucun aperçu
        assertThat(previews.captureAll()).isEmpty();
    }

    @Test
    void forgets_the_tab_closed_on_a_failure() {
        // Given l'outil qui a fini son travail dans un onglet, en échec
        assertThatThrownBy(() -> tabs.workInNewTab(page -> {
            throw new IllegalStateException("work failed");
        })).hasMessage("work failed");

        // When je demande les aperçus live
        // Then je ne reçois aucun aperçu
        assertThat(previews.captureAll()).isEmpty();
    }

    @Test
    void capturing_does_not_act_on_the_page() {
        // Given l'outil qui travaille dans un onglet, sur une page qui compte les clics, frappes, défilements et navigations
        tabs.workInNewTab(page -> {
            page.setContent(WATCHFUL_PAGE);

            // When je demande les aperçus live plusieurs fois
            for (int request = 0; request < 3; request++) {
                assertThat(previews.captureAll()).singleElement()
                        .satisfies(preview -> assertThat(preview.screenshot()).isPresent());
            }

            // Then la page n'a compté aucun clic, frappe, défilement ni navigation
            assertThat(page.evaluate("window.counted")).isEqualTo(Map.of("click", 0, "keydown", 0, "scroll", 0,
                    "wheel", 0, "pointerdown", 0, "input", 0));
            assertThat(page.evaluate("window.untouched")).isEqualTo(true);
            // And l'outil continue son travail dans l'onglet
            page.fill("#field", "suite du travail");
            assertThat(page.inputValue("#field")).isEqualTo("suite du travail");
        });
    }

    @Test
    void a_failed_capture_gives_a_preview_without_image() {
        // Given un onglet noté que Chrome ne connaît plus
        List<LivePreview> captured = tabs.inNewTab(page -> {
            page.close();

            // When je demande les aperçus live
            return previews.captureAll();
        });

        // Then je reçois l'aperçu de ce navigateur sans image ni date
        assertThat(captured).singleElement()
                .satisfies(preview -> assertThat(preview.screenshot()).isEmpty());
    }

    private static int centreOf(Screenshot screenshot) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(screenshot.image()));
            return image.getRGB(image.getWidth() / 2, image.getHeight() / 2);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static boolean isRed(int rgb) {
        Color colour = new Color(rgb);
        return colour.getRed() > 230 && colour.getGreen() < 30 && colour.getBlue() < 30;
    }
}
