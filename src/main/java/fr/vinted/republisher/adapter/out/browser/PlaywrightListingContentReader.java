package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Page;
import fr.vinted.republisher.domain.backup.BackupPace;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingContentReader;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Visits each listing as the member would, slowly, in one tab: its edit form for the content, then its public page
 * for the photos. It only reads, with a pause before every page but the first.
 */
public class PlaywrightListingContentReader implements ListingContentReader {

    private static final String LISTING_ID = "{id}";

    private final String editFormUrl;
    private final String listingPageUrl;
    private final Duration labelTimeout;
    private final Duration photoTimeout;
    private final ChromeTabs tabs;
    private final Clock clock;

    /** Both addresses hold {@value #LISTING_ID} where the listing identifier goes. */
    public PlaywrightListingContentReader(String editFormUrl, String listingPageUrl, Duration labelTimeout,
                                          Duration photoTimeout, ChromeTabs tabs, Clock clock) {
        for (String url : List.of(editFormUrl, listingPageUrl)) {
            if (!url.contains(LISTING_ID)) {
                throw new IllegalArgumentException("Listing URL without " + LISTING_ID + ": " + url);
            }
        }
        this.editFormUrl = editFormUrl;
        this.listingPageUrl = listingPageUrl;
        this.labelTimeout = labelTimeout;
        this.photoTimeout = photoTimeout;
        this.tabs = tabs;
        this.clock = clock;
    }

    @Override
    public void read(List<VintedListingId> listings, BackupPace pace, Consumer<ListingReading> record) {
        tabs.workInNewTab(page -> {
            page.bringToFront();
            readEach(page, listings, pace, record);
        });
    }

    private void readEach(Page page, List<VintedListingId> listings, BackupPace pace, Consumer<ListingReading> record) {
        for (int index = 0; index < listings.size(); index++) {
            VintedListingId listing = listings.get(index);
            if (index > 0) {
                ChromeTabs.pause(page, pace.nextPauseBetweenPages(), clock);
            }
            ListingContent content = ListingEditForm.read(page, listing, addressOf(editFormUrl, listing), labelTimeout);
            ChromeTabs.pause(page, pace.nextPauseBetweenPages(), clock);
            record.accept(new ListingReading(content,
                    ListingGallery.read(page, listing, addressOf(listingPageUrl, listing), photoTimeout)));
        }
    }

    private static URI addressOf(String url, VintedListingId listing) {
        return URI.create(url.replace(LISTING_ID, Long.toString(listing.value())));
    }
}
