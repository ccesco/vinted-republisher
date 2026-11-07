package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.InvalidListing;

import java.util.List;
import java.util.Objects;

/** A multiple choice of the listing form, such as colours: the form displays it as one label. */
public record FormOptions(List<Long> ids, String label) {

    public FormOptions {
        ids = List.copyOf(ids);
        if (ids.isEmpty()) {
            throw new InvalidListing("no option identifier");
        }
        ids.forEach(FormOption::requirePositive);
        label = Objects.requireNonNull(label).strip();
    }
}
