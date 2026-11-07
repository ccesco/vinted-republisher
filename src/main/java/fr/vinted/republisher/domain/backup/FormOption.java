package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.InvalidListing;

import java.util.Objects;

/** A single choice of the listing form: what Vinted needs to republish, and the label the member reads. */
public record FormOption(long id, String label) {

    public FormOption {
        requirePositive(id);
        label = Objects.requireNonNull(label).strip();
    }

    static void requirePositive(long id) {
        if (id <= 0) {
            throw new InvalidListing("option identifier " + id);
        }
    }
}
