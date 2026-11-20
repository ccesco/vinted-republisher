package fr.vinted.republisher.domain.republication;

/** A search for the new listing of a listing to link that settled nothing (US-48): nothing is recorded. */
public enum NewListingSearchResult {
    /** No listing shown on opening the wardrobe has the saved title and price. */
    NOT_FOUND("NEW_LISTING_NOT_FOUND"),
    /** Several do: the member pastes the link of the right one. */
    SEVERAL("SEVERAL_NEW_LISTINGS");

    private final String journalReason;

    NewListingSearchResult(String journalReason) {
        this.journalReason = journalReason;
    }

    /** The reason written in the journal, one of {@code ActionRecord.NO_NEW_LISTING_REASONS}. */
    public String journalReason() {
        return journalReason;
    }

    public static NewListingSearchResult of(int matching) {
        if (matching == 1) {
            throw new IllegalArgumentException("A single matching listing is the new listing");
        }
        return matching == 0 ? NOT_FOUND : SEVERAL;
    }
}
