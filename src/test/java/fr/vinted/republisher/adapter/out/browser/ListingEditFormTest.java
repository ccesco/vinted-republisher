package fr.vinted.republisher.adapter.out.browser;

import fr.vinted.republisher.domain.backup.CategoryDetails;
import fr.vinted.republisher.domain.backup.FormOption;
import fr.vinted.republisher.domain.backup.FormOptions;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingUnreadable;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.adapter.out.browser.EditFormFixtures.CATALOG_ROW;
import static fr.vinted.republisher.adapter.out.browser.EditFormFixtures.anItem;
import static fr.vinted.republisher.adapter.out.browser.EditFormFixtures.itemRow;
import static fr.vinted.republisher.adapter.out.browser.EditFormFixtures.scriptsWith;
import static fr.vinted.republisher.adapter.out.browser.EditFormFixtures.textRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingEditFormTest {

    private static final VintedListingId ID = new VintedListingId(10);
    private static final ListingEditForm.DisplayedLabels LABELS = new ListingEditForm.DisplayedLabels(
            "Très bon état", "36", "Blanc", "Sélectionne un matériau", "Petit");

    @Test
    void reads_identifiers_from_the_scripts_and_labels_from_the_form() {
        assertThat(parse(anItem(10), LABELS)).isEqualTo(new ListingContent(ID,
                "Paire chaussure 9-12 mois", "2 paires", new Price(new BigDecimal("1"), "EUR"),
                new FormOption(1653, "Chaussures bébé"), Optional.of(new FormOption(60, "Kiabi")),
                new FormOption(2, "Très bon état"), Optional.of(new FormOption(588, "36")),
                Optional.of(new FormOptions(List.of(12L), "Blanc")), Optional.empty(),
                new FormOption(1, "Petit"), CategoryDetails.none()));
    }

    @Test
    void label_still_on_its_placeholder_is_saved_empty() {
        ListingEditForm.DisplayedLabels sizeNotShown = new ListingEditForm.DisplayedLabels(
                "Très bon état", "Sélectionne une taille", "Blanc", null, "Petit");

        assertThat(parse(anItem(10), sizeNotShown).size()).contains(new FormOption(588, ""));
    }

    @Test
    void tells_which_labels_it_waits_for() {
        assertThat(ListingEditForm.awaitedLabels(ID, scriptsWith(itemRow(anItem(10)), CATALOG_ROW)))
                .containsExactlyInAnyOrder("condition", "size", "color", "packageSize");
    }

    @Test
    void reads_materials_and_details_of_the_category() {
        String book = anItem(10)
                .replace("\"isbn\":null", "\"isbn\":\"9782070612758\"")
                .replace("\"measurementWidth\":null", "\"measurementWidth\":21.5")
                .replace("{\"ids\":[588],\"code\":\"size\"}", "{\"ids\":[44,45],\"code\":\"material\"}");
        ListingEditForm.DisplayedLabels labels = new ListingEditForm.DisplayedLabels(
                "Très bon état", null, "Blanc", "Coton, Lin", "Petit");

        ListingContent content = parse(book, labels);

        assertThat(content.materials()).contains(new FormOptions(List.of(44L, 45L), "Coton, Lin"));
        assertThat(content.size()).isEmpty();
        assertThat(content.details().isbn()).contains("9782070612758");
        assertThat(content.details().measurementWidth()).contains(new BigDecimal("21.5"));
    }

    @Test
    void listing_without_brand_nor_colour_has_none() {
        String plain = anItem(10).replace("\"brand\":{\"id\":60,\"title\":\"Kiabi\"},\"brandId\":60",
                "\"brand\":null,\"brandId\":null").replace("\"colorIds\":[12]", "\"colorIds\":[]");

        ListingContent content = parse(plain, LABELS);

        assertThat(content.brand()).isEmpty();
        assertThat(content.colors()).isEmpty();
    }

    @Test
    void reading_without_condition_is_incomplete() {
        String noCondition = anItem(10).replace("{\"ids\":[2],\"code\":\"condition\"},", "");

        assertThatThrownBy(() -> parse(noCondition, LABELS))
                .isInstanceOf(ListingUnreadable.class).hasMessageContaining("no condition on 10");
    }

    @Test
    void unknown_attribute_code_makes_the_reading_incomplete() {
        String unknown = anItem(10).replace("\"code\":\"size\"", "\"code\":\"video_game_rating\"");

        assertThatThrownBy(() -> parse(unknown, LABELS))
                .isInstanceOf(ListingUnreadable.class)
                .hasMessageContaining("unknown attribute code video_game_rating on 10");
    }

    @Test
    void reading_without_package_size_is_incomplete() {
        String noPackage = anItem(10).replace("\"packageSizeId\":1,", "");

        assertThatThrownBy(() -> parse(noPackage, LABELS)).isInstanceOf(ListingUnreadable.class);
    }

    @Test
    void reading_with_a_value_of_an_unexpected_type_is_incomplete() {
        String modelObject = anItem(10).replace("\"model\":null", "\"model\":{\"id\":3}");

        assertThatThrownBy(() -> parse(modelObject, LABELS)).isInstanceOf(ListingUnreadable.class);
    }

    @Test
    void free_listing_is_incomplete() {
        String free = anItem(10).replace("\"price\":1", "\"price\":0");

        assertThatThrownBy(() -> parse(free, LABELS)).isInstanceOf(ListingUnreadable.class);
    }

    @Test
    void data_of_another_listing_only_is_unreadable() {
        assertThatThrownBy(() -> parse(anItem(11), LABELS))
                .isInstanceOf(ListingUnreadable.class).hasMessageContaining("no listing data on 10");
    }

    @Test
    void two_different_data_of_the_listing_are_unreadable() {
        List<String> scripts = scriptsWith(itemRow(anItem(10)),
                "8:{\"item\":" + anItem(10).replace("2 paires", "3 paires") + "}", CATALOG_ROW);

        assertThatThrownBy(() -> ListingEditForm.parse(ID, scripts, LABELS))
                .isInstanceOf(ListingUnreadable.class).hasMessageContaining("2 listing data on 10");
    }

    @Test
    void same_data_given_twice_is_read_once() {
        List<String> scripts = scriptsWith(itemRow(anItem(10)), "8:{\"item\":" + anItem(10) + "}", CATALOG_ROW);

        assertThat(ListingEditForm.parse(ID, scripts, LABELS).title()).isEqualTo("Paire chaussure 9-12 mois");
    }

    @Test
    void long_description_sent_apart_is_read_from_its_text_row() {
        String longDescription = "Très bon état, portée deux fois.\nTaille grand : prendre une taille en dessous. "
                .repeat(30);
        List<String> scripts = scriptsWith(textRow("1a", longDescription),
                itemRow(anItem(10).replace("\"2 paires\"", "\"$1a\"")), CATALOG_ROW);

        assertThat(ListingEditForm.parse(ID, scripts, LABELS).description()).isEqualTo(longDescription);
    }

    @Test
    void text_starting_with_a_dollar_is_unescaped() {
        String dollar = anItem(10).replace("\"Paire chaussure 9-12 mois\"", "\"$$5 la paire\"");

        assertThat(parse(dollar, LABELS).title()).isEqualTo("$5 la paire");
    }

    @Test
    void reference_to_a_missing_text_is_unreadable() {
        String missing = anItem(10).replace("\"2 paires\"", "\"$2b\"");

        assertThatThrownBy(() -> parse(missing, LABELS))
                .isInstanceOf(ListingUnreadable.class).hasMessageContaining("unexpected data on 10");
    }

    @Test
    void any_other_reference_is_unreadable() {
        String date = anItem(10).replace("\"2 paires\"", "\"$D2026-11-07T10:00:00.000Z\"");

        assertThatThrownBy(() -> parse(date, LABELS)).isInstanceOf(ListingUnreadable.class);
    }

    @Test
    void attribute_without_code_is_unreadable_even_before_reading_the_labels() {
        String noCode = anItem(10).replace("\"code\":\"size\"", "\"name\":\"size\"");
        List<String> scripts = scriptsWith(itemRow(noCode), CATALOG_ROW);

        assertThatThrownBy(() -> ListingEditForm.awaitedLabels(ID, scripts))
                .isInstanceOf(ListingUnreadable.class).hasMessageContaining("unexpected data on 10");
    }

    @Test
    void chunk_that_is_not_a_text_is_unreadable() {
        List<String> scripts = List.of("self.__next_f.push([1,{\"not\":\"a text\"}])");

        assertThatThrownBy(() -> ListingEditForm.parse(ID, scripts, LABELS)).isInstanceOf(ListingUnreadable.class);
    }

    @Test
    void unknown_category_is_unreadable() {
        List<String> scripts = scriptsWith(itemRow(anItem(10)));

        assertThatThrownBy(() -> ListingEditForm.parse(ID, scripts, LABELS))
                .isInstanceOf(ListingUnreadable.class).hasMessageContaining("no category 1653 on 10");
    }

    private static ListingContent parse(String item, ListingEditForm.DisplayedLabels labels) {
        return ListingEditForm.parse(ID, scriptsWith(itemRow(item), CATALOG_ROW), labels);
    }
}
