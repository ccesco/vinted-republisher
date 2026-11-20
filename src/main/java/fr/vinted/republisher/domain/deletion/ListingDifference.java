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
        return between(saved.title(), saved.price(), read);
    }

    /** How the listing Vinted shows differs from the title and price the tool knows it by. */
    static List<ListingDifference> between(String title, Price price, ScannedListing read) {
        List<ListingDifference> differences = new ArrayList<>();
        if (!read.active()) {
            differences.add(new NotActive());
        }
        if (!title.equals(read.title())) {
            differences.add(new TitleChanged(title, read.title()));
        }
        if (!price.equals(read.price())) {
            differences.add(new PriceChanged(price, read.price()));
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
