package fr.vinted.republisher.adapter.out.browser;

import fr.vinted.republisher.domain.wardrobe.ListingStatusFlags;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.domain.wardrobe.WardrobeUnreadable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

/**
 * One slice of listings as Vinted's internal API answers it. This API is not documented: anything
 * missing or unexpected makes the wardrobe unreadable, rather than being guessed.
 */
final class WardrobeItems {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private WardrobeItems() {
    }

    static List<ScannedListing> parse(String answer) {
        try {
            return JSON.readTree(answer).required("items").valueStream().map(WardrobeItems::listing).toList();
        } catch (JacksonException | IllegalArgumentException unexpected) {
            throw new WardrobeUnreadable("unexpected answer from Vinted", unexpected);
        }
    }

    private static ScannedListing listing(JsonNode item) {
        JsonNode price = item.required("price");
        return new ScannedListing(
                new VintedListingId(item.required("id").longValue()),
                URI.create(item.required("url").stringValue()),
                item.required("title").stringValue(),
                new Price(new BigDecimal(price.required("amount").stringValue()),
                        price.required("currency_code").stringValue()),
                new ListingStatusFlags(
                        item.required("is_draft").booleanValue(),
                        item.required("is_closed").booleanValue(),
                        item.required("is_reserved").booleanValue(),
                        item.required("is_hidden").booleanValue(),
                        item.required("is_processing").booleanValue()));
    }
}
