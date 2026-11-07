package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Page;
import fr.vinted.republisher.domain.backup.BackupPace;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingContentReader;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/** Opens the edit form of each listing, as the member would, slowly, in one tab, and only reads it. */
public class PlaywrightListingContentReader implements ListingContentReader {

    private static final String LISTING_ID = "{id}";

    private final String editFormUrl;
    private final Duration labelTimeout;
    private final ChromeTabs tabs;
    private final Clock clock;

    /** {@code editFormUrl} holds {@value #LISTING_ID} where the listing identifier goes. */
    public PlaywrightListingContentReader(String editFormUrl, Duration labelTimeout, ChromeTabs tabs, Clock clock) {
        if (!editFormUrl.contains(LISTING_ID)) {
            throw new IllegalArgumentException("Edit form URL without " + LISTING_ID + ": " + editFormUrl);
        }
        this.editFormUrl = editFormUrl;
        this.labelTimeout = labelTimeout;
        this.tabs = tabs;
        this.clock = clock;
    }

    @Override
    public void read(List<VintedListingId> listings, BackupPace pace, Consumer<ListingContent> record) {
        tabs.workInNewTab(page -> {
            page.bringToFront();
            readEach(page, listings, pace, record);
        });
    }

    private void readEach(Page page, List<VintedListingId> listings, BackupPace pace, Consumer<ListingContent> record) {
        Iterator<VintedListingId> toOpen = listings.iterator();
        while (toOpen.hasNext()) {
            VintedListingId listing = toOpen.next();
            record.accept(ListingEditForm.read(page, listing, formOf(listing), labelTimeout));
            if (toOpen.hasNext()) {
                ChromeTabs.pause(page, pace.nextPauseBetweenListings(), clock);
            }
        }
    }

    private URI formOf(VintedListingId listing) {
        return URI.create(editFormUrl.replace(LISTING_ID, Long.toString(listing.value())));
    }
}
