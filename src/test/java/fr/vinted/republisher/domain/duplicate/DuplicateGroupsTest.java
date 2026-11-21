package fr.vinted.republisher.domain.duplicate;

import fr.vinted.republisher.domain.wardrobe.ArticleId;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.seenSince;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class DuplicateGroupsTest {

    private static final Instant SEEN = Instant.parse("2026-11-18T10:00:00Z");

    @Test
    void groups_present_listings_with_the_same_title_and_price() {
        Listing first = present(2, "Robe bleue", "12.00", SEEN);
        Listing second = present(1, "Robe bleue", "12.0", SEEN);
        Listing otherPrice = present(3, "Robe bleue", "15.00", SEEN);
        Listing otherTitle = present(4, "Robe rouge", "12.00", SEEN);
        Listing absent = present(5, "Robe bleue", "12.00", SEEN).absent();

        DuplicateGroups groups = DuplicateGroups.among(List.of(first, second, otherPrice, otherTitle, absent));

        assertThat(groups.groups()).containsExactly(new DuplicateGroup("Robe bleue", euros("12.00"),
                List.of(second, first)));
    }

    @Test
    void a_currency_makes_another_price() {
        Listing euro = present(1, "Robe bleue", "12.00", SEEN);
        Listing pound = new Listing(new VintedListingId(2), ArticleId.newArticle(), euro.url(), "Robe bleue",
                new Price(new BigDecimal("12.00"), "GBP"), true, SEEN, SEEN);

        assertThat(DuplicateGroups.among(List.of(euro, pound)).groups()).isEmpty();
    }

    @Test
    void the_group_seen_first_most_recently_comes_first() {
        Listing oldOne = present(1, "Pull", "5.00", SEEN);
        Listing oldTwin = present(2, "Pull", "5.00", SEEN);
        Listing newOne = present(3, "Robe", "5.00", SEEN.minusSeconds(60));
        Listing newTwin = present(4, "Robe", "5.00", SEEN.plusSeconds(60));

        assertThat(DuplicateGroups.among(List.of(oldOne, oldTwin, newOne, newTwin)).groups())
                .extracting(DuplicateGroup::title).containsExactly("Robe", "Pull");
    }

    @Test
    void the_twins_of_a_listing_are_the_other_listings_of_its_group() {
        Listing first = present(1, "Robe bleue", "12.00", SEEN);
        Listing second = present(2, "Robe bleue", "12.00", SEEN);
        Listing third = present(3, "Robe bleue", "12.00", SEEN);
        Listing alone = present(4, "Pull", "12.00", SEEN);
        DuplicateGroups groups = DuplicateGroups.among(List.of(first, second, third, alone));

        assertThat(groups.twinsOf(second.id())).containsExactly(first, third);
        assertThat(groups.twinsOf(alone.id())).isEmpty();
    }

    @Test
    void a_listing_of_a_group_without_history_is_deletable() {
        DuplicateGroups groups = DuplicateGroups.among(List.of(present(1, "Robe", "1.00", SEEN),
                present(2, "Robe", "1.00", SEEN)));

        assertThat(groups.deletable(new VintedListingId(2), Set.of(new VintedListingId(1)))).isTrue();
        assertThat(groups.requireDeletable(new VintedListingId(2), Set.of(new VintedListingId(1))))
                .extracting(Listing::id).containsExactly(new VintedListingId(1));
    }

    @Test
    void a_listing_without_twin_is_not_a_duplicate() {
        DuplicateGroups groups = DuplicateGroups.among(List.of(present(1, "Robe", "1.00", SEEN)));

        assertThat(groups.deletable(new VintedListingId(1), Set.of())).isFalse();
        assertThat(catchThrowable(() -> groups.requireDeletable(new VintedListingId(1), Set.of())))
                .isInstanceOf(NotADuplicate.class);
    }

    @Test
    void a_listing_with_history_cannot_be_deleted_as_a_duplicate() {
        DuplicateGroups groups = DuplicateGroups.among(List.of(present(1, "Robe", "1.00", SEEN),
                present(2, "Robe", "1.00", SEEN)));

        assertThat(groups.deletable(new VintedListingId(1), Set.of(new VintedListingId(1)))).isFalse();
        assertThat(catchThrowable(() -> groups.requireDeletable(new VintedListingId(1),
                Set.of(new VintedListingId(1))))).isInstanceOf(ListingHasHistory.class);
    }

    private static Listing present(long id, String title, String price, Instant firstSeenAt) {
        return seenSince(aListing(id, title, price, ACTIVE), firstSeenAt, SEEN);
    }

    private static Price euros(String amount) {
        return new Price(new BigDecimal(amount), "EUR");
    }
}
