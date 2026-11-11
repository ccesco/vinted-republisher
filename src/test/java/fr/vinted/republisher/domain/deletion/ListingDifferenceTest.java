package fr.vinted.republisher.domain.deletion;

import fr.vinted.republisher.domain.deletion.ListingDifference.NotActive;
import fr.vinted.republisher.domain.deletion.ListingDifference.PriceChanged;
import fr.vinted.republisher.domain.deletion.ListingDifference.TitleChanged;
import fr.vinted.republisher.domain.wardrobe.Price;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anInactiveListing;
import static org.assertj.core.api.Assertions.assertThat;

class ListingDifferenceTest {

    @Test
    void none_when_vinted_shows_the_listing_as_saved_and_active() {
        assertThat(ListingDifference.between(aContent(1), anActiveListing(1))).isEmpty();
    }

    @Test
    void a_listing_no_longer_active_differs() {
        assertThat(ListingDifference.between(aContent(1), anInactiveListing(1))).containsExactly(new NotActive());
    }

    @Test
    void title_and_price_changed_since_the_backup_differ_with_both_values() {
        assertThat(ListingDifference.between(aContent(1, "Robe bleue", "Neuve", "12.00"),
                aListing(1, "Robe bleue M", "10.00", ACTIVE)))
                .containsExactly(new TitleChanged("Robe bleue", "Robe bleue M"),
                        new PriceChanged(euros("12.00"), euros("10.00")));
    }

    private static Price euros(String amount) {
        return new Price(new BigDecimal(amount), "EUR");
    }
}
