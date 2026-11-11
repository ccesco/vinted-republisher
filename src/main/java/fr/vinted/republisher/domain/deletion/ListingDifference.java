package fr.vinted.republisher.domain.deletion;

import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * How the listing Vinted shows right before its deletion differs from its backup: deleting it would lose a change,
 * or delete a listing sold or reserved in the meantime.
 */
public sealed interface ListingDifference {

    static List<ListingDifference> between(ListingContent saved, ScannedListing read) {
        List<ListingDifference> differences = new ArrayList<>();
        if (!read.active()) {
            differences.add(new NotActive());
        }
        if (!saved.title().equals(read.title())) {
            differences.add(new TitleChanged(saved.title(), read.title()));
        }
        if (!saved.price().equals(read.price())) {
            differences.add(new PriceChanged(saved.price(), read.price()));
        }
        return List.copyOf(differences);
    }

    record NotActive() implements ListingDifference {
    }

    record TitleChanged(String saved, String read) implements ListingDifference {
        public TitleChanged {
            Objects.requireNonNull(saved);
            Objects.requireNonNull(read);
        }
    }

    record PriceChanged(Price saved, Price read) implements ListingDifference {
        public PriceChanged {
            Objects.requireNonNull(saved);
            Objects.requireNonNull(read);
        }
    }
}
