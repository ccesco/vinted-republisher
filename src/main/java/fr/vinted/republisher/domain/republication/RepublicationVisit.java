package fr.vinted.republisher.domain.republication;

/** What the republication decides once the listing form is filled, before Publish is clicked. */
public interface RepublicationVisit {

    /**
     * Once every field is filled, right before clicking Publish: whether to click it. Agreeing records the attempt
     * first, so that a crash right after the click leaves an error rather than no trace.
     */
    boolean readyToPublish();
}
