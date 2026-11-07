package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Page;
import fr.vinted.republisher.domain.wardrobe.ListingLink;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.WardrobeUnreadable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

/**
 * The page of a listing, read through its structured data (JSON-LD), far steadier than its layout. Anything
 * missing or unexpected makes the wardrobe unreadable, rather than being guessed.
 */
final class ListingPage {

    private static final String STRUCTURED_DATA = "script[type='application/ld+json']";
    private static final String IN_STOCK = "InStock";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ListingPage() {
    }

    static ScannedListing open(Page page, ListingLink link) {
        ChromeTabs.open(page, link.url());
        return parse(link, page.locator(STRUCTURED_DATA).allTextContents());
    }

    static ScannedListing parse(ListingLink link, List<String> structuredData) {
        try {
            List<JsonNode> products = structuredData.stream().flatMap(ListingPage::entries)
                    .filter(entry -> "Product".equals(entry.path("@type").asString())).toList();
            if (products.size() != 1) {
                throw new WardrobeUnreadable(describe(products.size()) + " on " + link.url());
            }
            return listing(link, products.getFirst());
        } catch (JacksonException | IllegalArgumentException unexpected) {
            throw new WardrobeUnreadable("unexpected data on " + link.url(), unexpected);
        }
    }

    private static String describe(int products) {
        return products == 0 ? "no product" : products + " products";
    }

    private static Stream<JsonNode> entries(String script) {
        JsonNode data = JSON.readTree(script);
        return data.isArray() ? data.valueStream() : Stream.of(data);
    }

    private static ScannedListing listing(ListingLink link, JsonNode product) {
        JsonNode offers = product.required("offers");
        return new ScannedListing(
                link,
                text(product, "name"),
                new Price(new BigDecimal(offers.required("price").asString()), text(offers, "priceCurrency")),
                isInStock(text(offers, "availability")));
    }

    private static String text(JsonNode parent, String field) {
        JsonNode value = parent.required(field);
        if (!value.isString()) {
            throw new IllegalArgumentException(field + " is not a text");
        }
        return value.stringValue();
    }

    /** Schema.org gives it as an address, https://schema.org/InStock, or by its short name. */
    private static boolean isInStock(String availability) {
        return IN_STOCK.equals(availability.substring(availability.lastIndexOf('/') + 1));
    }
}
