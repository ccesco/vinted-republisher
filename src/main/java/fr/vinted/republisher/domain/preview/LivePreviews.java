package fr.vinted.republisher.domain.preview;

import java.util.List;

/** Outbound port: the working tabs of the piloted browsers, captured when asked, read only. */
public interface LivePreviews {

    /** One preview per working tab, sorted by browser; empty when no browser is at work. */
    List<LivePreview> captureAll();
}
