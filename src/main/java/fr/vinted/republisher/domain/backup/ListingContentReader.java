package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.List;
import java.util.function.Consumer;

/**
 * Visits one by one the given listings on Vinted, in one tab closed at the end: reads the content of each from its
 * edit form and downloads its photos from its public page, then hands the reading over at once. It never clicks,
 * types or submits anything.
 * Throws {@code BrowserUnavailable}, {@code VintedUnreachable}, {@code SessionDisconnected} or
 * {@link ListingUnreadable}, and lets through what {@code record} throws.
 */
public interface ListingContentReader {

    void read(List<VintedListingId> listings, BackupPace pace, Consumer<ListingReading> record);

    /**
     * Reads again the edit form of each given listing, in the same way, without its photos (US-40): a form answering
     * 404 is handed over without content, and the reading goes on. Throws as {@link #read}.
     */
    void readForms(List<VintedListingId> listings, BackupPace pace, Consumer<ListingFormReading> record);
}
