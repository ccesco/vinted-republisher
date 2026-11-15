package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.backup.CategoryDetails;
import fr.vinted.republisher.domain.backup.FormOption;
import fr.vinted.republisher.domain.backup.FormOptions;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static org.assertj.core.api.Assertions.assertThat;

class ListingFieldDifferenceTest {

    private static final VintedListingId NEW_LISTING = new VintedListingId(2);

    private final ListingContent saved = aContent(1, "Robe bleue", "Portée une fois", "12.00");

    @Test
    void a_new_listing_with_the_saved_content_and_as_many_photos_differs_in_nothing() {
        assertThat(ListingFieldDifference.between(saved, 2, readAs(sameAsSaved(), 2))).isEmpty();
    }

    @Test
    void options_are_compared_by_their_vinted_identifier_not_their_label() {
        ListingContent relabelled = new ListingContent(NEW_LISTING, saved.title(), saved.description(), saved.price(),
                new FormOption(1653, "Chaussons"), Optional.of(new FormOption(60, "KIABI")),
                new FormOption(2, "Très bon"), Optional.of(new FormOption(588, "56 cm")),
                Optional.of(new FormOptions(List.of(12L), "White")), Optional.empty(), new FormOption(1, "S"),
                CategoryDetails.none());

        assertThat(ListingFieldDifference.between(saved, 1, readAs(relabelled, 1))).isEmpty();
    }

    @Test
    void every_changed_field_is_listed_with_its_saved_and_read_values() {
        ListingContent changed = new ListingContent(NEW_LISTING, "Robe bleu", "Portée deux fois",
                new Price(new BigDecimal("10.00"), "EUR"), new FormOption(1654, "Bottes"), Optional.empty(),
                new FormOption(6, "Neuf avec étiquette"), Optional.of(new FormOption(614, "3-6 mois")),
                Optional.of(new FormOptions(List.of(12L, 1L), "Blanc, Noir")),
                Optional.of(new FormOptions(List.of(44L), "Coton")), new FormOption(2, "Moyen"),
                CategoryDetails.none());

        assertThat(ListingFieldDifference.between(saved, 3, readAs(changed, 2))).containsExactly(
                new ListingFieldDifference(ListingFormField.PHOTOS, "3", "2"),
                new ListingFieldDifference(ListingFormField.TITLE, "Robe bleue", "Robe bleu"),
                new ListingFieldDifference(ListingFormField.DESCRIPTION, "Portée une fois", "Portée deux fois"),
                new ListingFieldDifference(ListingFormField.PRICE, "12.00 EUR", "10.00 EUR"),
                new ListingFieldDifference(ListingFormField.CATEGORY, "Chaussures bébé (1653)", "Bottes (1654)"),
                new ListingFieldDifference(ListingFormField.BRAND, "Kiabi (60)", ""),
                new ListingFieldDifference(ListingFormField.CONDITION, "Très bon état (2)", "Neuf avec étiquette (6)"),
                new ListingFieldDifference(ListingFormField.SIZE, "588", "3-6 mois (614)"),
                new ListingFieldDifference(ListingFormField.COLORS, "Blanc (12)", "Blanc, Noir (1, 12)"),
                new ListingFieldDifference(ListingFormField.MATERIALS, "", "Coton (44)"),
                new ListingFieldDifference(ListingFormField.PACKAGE_SIZE, "Petit (1)", "Moyen (2)"));
    }

    @Test
    void colours_ticked_in_another_order_are_the_same() {
        ListingContent twoColours = withColours(saved, List.of(12L, 1L));
        ListingContent reordered = withColours(sameAsSaved(), List.of(1L, 12L));

        assertThat(ListingFieldDifference.between(twoColours, 1, readAs(reordered, 1))).isEmpty();
    }

    @Test
    void category_details_are_compared_field_by_field() {
        CategoryDetails book = new CategoryDetails(Optional.of("9782070612758"), Optional.of("Le Petit Prince"),
                Optional.of("Saint-Exupéry"), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(new BigDecimal("20")), Optional.of(new BigDecimal("30.0")), Optional.of("cm"));
        CategoryDetails read = new CategoryDetails(Optional.of("9782070612759"), Optional.of("Le Petit Prince"),
                Optional.of("Saint-Exupéry"), Optional.of("Gallimard"), Optional.empty(), Optional.empty(),
                Optional.of(new BigDecimal("20.0")), Optional.of(new BigDecimal("30")), Optional.of("cm"));

        assertThat(ListingFieldDifference.between(withDetails(saved, book), 1,
                readAs(withDetails(sameAsSaved(), read), 1))).containsExactly(
                new ListingFieldDifference(ListingFormField.ISBN, "9782070612758", "9782070612759"),
                new ListingFieldDifference(ListingFormField.MANUFACTURER, "", "Gallimard"));
    }

    private ListingContent sameAsSaved() {
        return new ListingContent(NEW_LISTING, saved.title(), saved.description(), saved.price(), saved.category(),
                saved.brand(), saved.condition(), saved.size(), saved.colors(), saved.materials(), saved.packageSize(),
                saved.details());
    }

    private static ListingContent withColours(ListingContent content, List<Long> colours) {
        return new ListingContent(content.id(), content.title(), content.description(), content.price(),
                content.category(), content.brand(), content.condition(), content.size(),
                Optional.of(new FormOptions(colours, "Couleurs")), content.materials(), content.packageSize(),
                content.details());
    }

    private static ListingContent withDetails(ListingContent content, CategoryDetails details) {
        return new ListingContent(content.id(), content.title(), content.description(), content.price(),
                content.category(), content.brand(), content.condition(), content.size(), content.colors(),
                content.materials(), content.packageSize(), details);
    }

    private static ListingReading readAs(ListingContent content, int photos) {
        return new ListingReading(content, IntStream.range(0, photos)
                .mapToObj(rank -> aPhoto("read " + rank)).toList());
    }
}
