package fr.vinted.republisher.domain.wardrobe;

/** What Vinted says of a listing at the time of a scan; never kept. */
public record ListingStatusFlags(boolean draft, boolean closed, boolean reserved, boolean hidden, boolean processing) {

    public boolean isActive() {
        return !(draft || closed || reserved || hidden || processing);
    }
}
