package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.tag.ListingTag;
import fr.vinted.republisher.domain.tag.Tag;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;

/** Read only: rows are added and removed by the queries of {@link ListingTagJpaRepository}. */
@Entity
@Table(name = "listing_tag")
class ListingTagJpaEntity {

    @EmbeddedId
    private Key key;

    protected ListingTagJpaEntity() {
    }

    ListingTag toListingTag() {
        return new ListingTag(new VintedListingId(key.listingId), new Tag(key.tag));
    }

    @Embeddable
    record Key(@Column(name = "listing_id", nullable = false) Long listingId,
               @Column(name = "tag", nullable = false) String tag) implements Serializable {
    }
}
