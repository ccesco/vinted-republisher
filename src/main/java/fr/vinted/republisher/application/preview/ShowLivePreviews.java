package fr.vinted.republisher.application.preview;

import fr.vinted.republisher.domain.preview.LivePreview;
import fr.vinted.republisher.domain.preview.LivePreviews;

import java.util.List;

/** Read only: captures what the piloted browsers show, never acts on their pages nor asks Vinted anything. */
public class ShowLivePreviews {

    private final LivePreviews previews;

    public ShowLivePreviews(LivePreviews previews) {
        this.previews = previews;
    }

    public List<LivePreview> show() {
        return previews.captureAll();
    }
}
