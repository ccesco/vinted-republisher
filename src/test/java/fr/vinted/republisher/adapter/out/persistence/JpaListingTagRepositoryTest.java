package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.ApplicationWithDatabaseTest;
import fr.vinted.republisher.domain.tag.ListingTag;
import fr.vinted.republisher.domain.tag.ListingTagRepository;
import fr.vinted.republisher.domain.tag.Tag;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** On a disposable PostgreSQL with the real changelog, never the real data. */
class JpaListingTagRepositoryTest extends ApplicationWithDatabaseTest {

    private static final VintedListingId FIRST = new VintedListingId(101);
    private static final VintedListingId SECOND = new VintedListingId(102);
    private static final Tag SUMMER = new Tag("été");

    @Autowired
    private ListingTagRepository tags;

    @Autowired
    private ListingRepository listings;

    @BeforeEach
    void startWithTwoListings() {
        Instant seenAt = Instant.parse("2026-11-17T09:00:00Z");
        listings.saveAll(List.of(Listing.seen(anActiveListing(FIRST.value()), seenAt),
                Listing.seen(anActiveListing(SECOND.value()), seenAt)));
    }

    @Test
    void adds_and_removes_tags() {
        // Étant donné les annonces 101 et 102 en base
        // Quand le tag « été » est posé sur les deux, puis retiré de 102
        tags.add(SUMMER, List.of(FIRST, SECOND));
        tags.remove(SUMMER, List.of(SECOND));

        // Alors relus, les tags sont : 101 « été », 102 aucun
        assertThat(tags.findAll()).containsExactly(new ListingTag(FIRST, SUMMER));
        //   et poser deux fois le même tag ne crée pas de doublon
        tags.add(SUMMER, List.of(FIRST));
        assertThat(tags.findAll()).containsExactly(new ListingTag(FIRST, SUMMER));
    }

    @Test
    void removing_a_tag_not_borne_changes_nothing() {
        tags.add(new Tag("lot"), List.of(FIRST));

        tags.remove(SUMMER, List.of(FIRST, SECOND));

        assertThat(tags.findAll()).containsExactly(new ListingTag(FIRST, new Tag("lot")));
    }

    @Test
    void adds_to_every_listing_or_none() {
        assertThatThrownBy(() -> tags.add(SUMMER, List.of(FIRST, new VintedListingId(999))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(tags.findAll()).isEmpty();
    }
}
