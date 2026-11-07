package fr.vinted.republisher.adapter.out.browser;

import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.domain.wardrobe.WardrobeUnreadable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.INACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aLink;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingPageTest {

    private static final String BREADCRUMB = "{\"@type\": \"BreadcrumbList\", \"itemListElement\": []}";

    @Test
    void reads_the_listing_out_of_its_structured_data() {
        assertThat(parse(product("Pull rayé", "\"12.50\"", "InStock")))
                .isEqualTo(aListing(1, "Pull rayé", "12.50", ACTIVE));
    }

    @Test
    void reads_a_price_given_as_a_number() {
        assertThat(parse(product("Pull rayé", "12.5", "InStock")).price())
                .isEqualTo(aListing(1, "Pull rayé", "12.50", ACTIVE).price());
    }

    @ParameterizedTest
    @ValueSource(strings = {"InStock", "https://schema.org/InStock", "http://schema.org/InStock"})
    void listing_in_stock_is_active(String availability) {
        assertThat(parse(product("Pull rayé", "\"12.50\"", availability)).active()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://schema.org/OutOfStock", "https://schema.org/SoldOut", "NotInStock"})
    void any_other_availability_is_not_active(String availability) {
        assertThat(parse(product("Pull rayé", "\"12.50\"", availability)).active()).isEqualTo(INACTIVE);
    }

    @Test
    void finds_the_product_among_other_structured_data() {
        assertThat(ListingPage.parse(aLink(1), List.of(BREADCRUMB, product("Pull rayé", "\"12.50\"", "InStock"))).title())
                .isEqualTo("Pull rayé");
    }

    @Test
    void finds_the_product_in_a_list_of_structured_data() {
        String list = "[" + BREADCRUMB + ", " + product("Pull rayé", "\"12.50\"", "InStock") + "]";

        assertThat(parse(list).title()).isEqualTo("Pull rayé");
    }

    @Test
    void page_without_a_product_is_unreadable() {
        assertThatThrownBy(() -> parse(BREADCRUMB))
                .isInstanceOf(WardrobeUnreadable.class).hasMessageContaining("no product");
    }

    @Test
    void page_without_structured_data_is_unreadable() {
        assertThatThrownBy(() -> ListingPage.parse(aLink(1), List.of()))
                .isInstanceOf(WardrobeUnreadable.class).hasMessageContaining("no product");
    }

    @Test
    void two_products_on_a_page_are_unreadable() {
        String product = product("Pull rayé", "\"12.50\"", "InStock");

        assertThatThrownBy(() -> ListingPage.parse(aLink(1), List.of(product, product)))
                .isInstanceOf(WardrobeUnreadable.class).hasMessageContaining("2 products");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"@type\": \"Product\", \"offers\": {\"price\": \"1\", \"priceCurrency\": \"EUR\", \"availability\": \"InStock\"}}",
            "{\"@type\": \"Product\", \"name\": \"Pull\"}",
            "{\"@type\": \"Product\", \"name\": \"Pull\", \"offers\": {\"priceCurrency\": \"EUR\", \"availability\": \"InStock\"}}",
            "{\"@type\": \"Product\", \"name\": \"Pull\", \"offers\": {\"price\": \"1\", \"availability\": \"InStock\"}}",
            "{\"@type\": \"Product\", \"name\": \"Pull\", \"offers\": {\"price\": \"1\", \"priceCurrency\": \"EUR\"}}",
            "{\"@type\": \"Product\", \"name\": null, \"offers\": {\"price\": \"1\", \"priceCurrency\": \"EUR\", \"availability\": \"InStock\"}}",
            "{\"@type\": \"Product\", \"name\": \"Pull\", \"offers\": {\"price\": \"1\", \"priceCurrency\": null, \"availability\": \"InStock\"}}",
            "{\"@type\": \"Product\", \"name\": \"Pull\", \"offers\": {\"price\": \"1\", \"priceCurrency\": \"EUR\", \"availability\": null}}",
            "{\"@type\": \"Product\", \"name\": \" \", \"offers\": {\"price\": \"1\", \"priceCurrency\": \"EUR\", \"availability\": \"InStock\"}}",
            "{\"@type\": \"Product\", \"name\": \"Pull\", \"offers\": {\"price\": \"gratuit\", \"priceCurrency\": \"EUR\", \"availability\": \"InStock\"}}",
            "{\"@type\": \"Product\", \"name\": \"Pull\", \"offers\": {\"price\": \"1\", \"priceCurrency\": \"euros\", \"availability\": \"InStock\"}}",
            "not json"})
    void incomplete_data_is_unreadable(String data) {
        assertThatThrownBy(() -> parse(data))
                .isInstanceOf(WardrobeUnreadable.class).hasMessageContaining("https://vinted.test/items/1");
    }

    private static ScannedListing parse(String data) {
        return ListingPage.parse(aLink(1), List.of(data));
    }

    private static String product(String name, String price, String availability) {
        return """
                {"@context": "https://schema.org", "@type": "Product", "name": "%s",
                 "offers": {"@type": "Offer", "price": %s, "priceCurrency": "EUR",
                            "url": "https://vinted.test/items/1", "availability": "%s"}}"""
                .formatted(name, price, availability);
    }
}
