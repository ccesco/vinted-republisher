package fr.vinted.republisher.adapter.out.browser;

import fr.vinted.republisher.domain.backup.DownloadedPhoto;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.republication.ListingFormUnreadable;
import fr.vinted.republisher.domain.republication.ListingPublisher;
import fr.vinted.republisher.domain.republication.RepublicationVisit;

import java.util.List;

/**
 * Stands in for the browser adapter until the listing form is observed with the member (AT-12-18): it never opens
 * Chrome, so nothing can be published on the real account.
 */
public class UnobservedListingPublisher implements ListingPublisher {

    @Override
    public void publish(ListingContent content, List<DownloadedPhoto> photos, RepublicationVisit visit) {
        throw new ListingFormUnreadable(
                "republishing on Vinted is not available until its listing form is observed with the member (AT-12-18)");
    }
}
