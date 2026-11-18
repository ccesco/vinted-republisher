package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.preview.LivePreview;
import fr.vinted.republisher.domain.preview.LivePreviews;

import java.util.ArrayList;
import java.util.List;

/** Browsers at work, as the test sets them. */
public class FakeLivePreviews implements LivePreviews {

    private final List<LivePreview> previews = new ArrayList<>();

    public void atWork(LivePreview... browsers) {
        previews.addAll(List.of(browsers));
    }

    @Override
    public List<LivePreview> captureAll() {
        return LivePreview.sortedByBrowser(previews);
    }
}
