package fr.vinted.republisher.domain.deletion;

import java.util.List;

/** Vinted shows the listing differently from its backup: deleting it now would lose something. */
public class ListingChanged extends RuntimeException {

    private final transient List<ListingDifference> differences;

    public ListingChanged(List<ListingDifference> differences) {
        super("The listing on Vinted differs from its backup: " + differences);
        this.differences = List.copyOf(differences);
    }

    public List<ListingDifference> differences() {
        return differences;
    }
}
