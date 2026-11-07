package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.InvalidListing;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Everything the listing form holds but the photos, as read on Vinted: enough to republish the listing without
 * typing anything again. Which optional fields a listing needs depends on its category.
 */
public record ListingContent(
        VintedListingId id,
        String title,
        String description,
        Price price,
        FormOption category,
        Optional<FormOption> brand,
        FormOption condition,
        Optional<FormOption> size,
        Optional<FormOptions> colors,
        Optional<FormOptions> materials,
        FormOption packageSize,
        CategoryDetails details) {

    public ListingContent {
        Stream.of(id, description, price, category, brand, condition, size, colors, materials, packageSize, details)
                .forEach(Objects::requireNonNull);
        if (title == null || title.isBlank()) {
            throw new InvalidListing("blank title on " + id.value());
        }
        if (price.amount().signum() <= 0) {
            throw new InvalidListing("price " + price.amount() + " on " + id.value());
        }
    }
}
