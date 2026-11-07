package fr.vinted.republisher.domain.wardrobe;

/**
 * Goes through the member's wardrobe on Vinted, then opens one by one the listings the visit asks for, in one
 * tab closed at the end, without changing anything there. Gives what it found on the wardrobe.
 * Throws {@code BrowserUnavailable}, {@code VintedUnreachable} or {@link WardrobeUnreadable}, and lets through
 * what the visit throws.
 */
public interface WardrobeReader {

    WardrobeListing read(ScanPace pace, WardrobeVisit visit);
}
