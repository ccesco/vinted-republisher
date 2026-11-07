package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.List;
import java.util.function.Consumer;

/**
 * Opens one by one the edit form of the given listings on Vinted, in one tab closed at the end, and hands over
 * each content as soon as it is read, without clicking, typing or submitting anything.
 * Throws {@code BrowserUnavailable}, {@code VintedUnreachable}, {@code SessionDisconnected} or
 * {@link ListingUnreadable}, and lets through what {@code record} throws.
 */
public interface ListingContentReader {

    void read(List<VintedListingId> listings, BackupPace pace, Consumer<ListingContent> record);
}
