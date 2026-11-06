package fr.vinted.republisher.domain.wardrobe;

/**
 * Reads the member's wardrobe on Vinted, without changing anything there.
 * Throws {@code BrowserUnavailable}, {@code VintedUnreachable} or {@link WardrobeUnreadable}.
 */
public interface WardrobeReader {

    WardrobeSnapshot read(ScanPace pace);
}
