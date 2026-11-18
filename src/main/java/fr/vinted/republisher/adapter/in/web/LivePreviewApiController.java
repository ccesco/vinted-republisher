package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.LivePreviewApi;
import fr.vinted.republisher.adapter.in.web.api.model.LivePreviewDto;
import fr.vinted.republisher.adapter.in.web.api.model.LivePreviewsDto;
import fr.vinted.republisher.application.preview.ShowLivePreviews;
import fr.vinted.republisher.domain.preview.LivePreview;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;

@RestController
public class LivePreviewApiController implements LivePreviewApi {

    private final ShowLivePreviews showLivePreviews;

    public LivePreviewApiController(ShowLivePreviews showLivePreviews) {
        this.showLivePreviews = showLivePreviews;
    }

    @Override
    public ResponseEntity<LivePreviewsDto> showLivePreviews() {
        return ResponseEntity.ok(new LivePreviewsDto()
                .previews(showLivePreviews.show().stream().map(LivePreviewApiController::toDto).toList()));
    }

    private static LivePreviewDto toDto(LivePreview preview) {
        LivePreviewDto dto = new LivePreviewDto().browser(preview.browser());
        preview.screenshot().ifPresent(screenshot -> dto
                .image(screenshot.image())
                .capturedAt(screenshot.capturedAt().atOffset(ZoneOffset.UTC)));
        return dto;
    }
}
