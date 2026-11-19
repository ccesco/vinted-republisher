package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.backup.DownloadedPhoto;
import fr.vinted.republisher.domain.backup.ListingContent;

import java.util.List;

/**
 * Puts a listing on sale on Vinted as the member would, through the listing form in one tab closed at the end.
 * Never calls Vinted's API directly.
 */
public interface ListingPublisher {

    /**
     * Opens the listing form, makes sure the member is connected, sends the photos in their order then fills every
     * field of {@code content}, then asks {@code visit}. If it agrees, clicks Publish. Before that click, throws
     * {@code BrowserUnavailable}, {@code VintedUnreachable}, {@code SessionDisconnected}, {@link ListingFormUnreadable}
     * or {@link FieldNotFillable}, having published nothing.
     */
    void publish(ListingContent content, List<DownloadedPhoto> photos, RepublicationVisit visit);

    /**
     * Opens the listing form in a new tab, left open and never used again, and fills it as {@link #publish} does,
     * except that a field it cannot fill is left empty and the next ones are filled. Never clicks Publish: the member
     * completes the form and publishes it. Throws {@code BrowserUnavailable}, {@code VintedUnreachable},
     * {@code SessionDisconnected}, {@link ListingFormUnreadable}, or {@link FieldNotFillable} for a form without
     * title, the tab being then closed.
     *
     * @return the fields left empty, in the order of the form
     */
    List<UnfilledField> prepare(ListingContent content, List<DownloadedPhoto> photos);
}
