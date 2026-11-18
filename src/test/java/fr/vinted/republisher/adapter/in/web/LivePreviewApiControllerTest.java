package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.preview.ShowLivePreviews;
import fr.vinted.republisher.domain.preview.LivePreview;
import fr.vinted.republisher.domain.preview.Screenshot;
import fr.vinted.republisher.fakes.FakeLivePreviews;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** US-22: the API answers as described by the OpenAPI contract. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(LivePreviewApiController.class)
class LivePreviewApiControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private FakeLivePreviews browsers;

    @Test
    void shows_one_preview_per_browser_sorted_by_browser() {
        // AT-22-05
        // Given « Chrome (port 9333) » et « Chrome (port 9222) » au travail, le premier capturé, le second sans image
        browsers.atWork(LivePreview.captured("Chrome (port 9333)",
                        new Screenshot(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, Instant.parse("2026-11-18T10:00:00Z"))),
                LivePreview.withoutScreenshot("Chrome (port 9222)"));

        // When je demande GET /api/live-previews
        // Then je reçois « Chrome (port 9222) » sans image puis « Chrome (port 9333) » avec son image en base64 et sa date
        assertThat(mvc.get().uri("/api/live-previews"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"previews": [
                           {"browser": "Chrome (port 9222)"},
                           {"browser": "Chrome (port 9333)", "image": "/9j/", "capturedAt": "2026-11-18T10:00:00Z"}]}""");
    }

    @Test
    void no_browser_at_work() {
        // AT-22-05: sans navigateur au travail, une liste vide
        assertThat(mvc.get().uri("/api/live-previews")).hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"previews": []}""");
    }

    @TestConfiguration
    static class LivePreviewUseCase {

        @Bean
        FakeLivePreviews browsers() {
            return new FakeLivePreviews();
        }

        @Bean
        ShowLivePreviews showLivePreviews(FakeLivePreviews browsers) {
            return new ShowLivePreviews(browsers);
        }
    }
}
