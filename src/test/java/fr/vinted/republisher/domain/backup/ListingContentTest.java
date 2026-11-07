package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.InvalidListing;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ListingContentTest {

    @Test
    void rejects_a_blank_title() {
        Throwable attempt = catchThrowable(() -> aContent(7, " ", "Porté deux fois"));

        assertThat(attempt).isInstanceOf(InvalidListing.class).hasMessage("Invalid listing: blank title on 7");
    }

    @Test
    void may_have_an_empty_description() {
        assertThat(aContent(7, "Pull rayé", "").description()).isEmpty();
    }

    @Test
    void rejects_a_free_listing() {
        Throwable attempt = catchThrowable(() -> withPrice("0.00"));

        assertThat(attempt).isInstanceOf(InvalidListing.class).hasMessage("Invalid listing: price 0.00 on 7");
    }

    private static ListingContent withPrice(String amount) {
        ListingContent content = aContent(7);
        return new ListingContent(new VintedListingId(7), content.title(), content.description(),
                new Price(new BigDecimal(amount), "EUR"), content.category(), content.brand(), content.condition(),
                content.size(), content.colors(), content.materials(), content.packageSize(), content.details());
    }

    @Test
    void category_details_are_all_optional() {
        assertThat(CategoryDetails.none().isbn()).isEqualTo(Optional.empty());
    }
}
