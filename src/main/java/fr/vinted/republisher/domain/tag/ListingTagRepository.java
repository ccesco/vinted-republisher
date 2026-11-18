package fr.vinted.republisher.domain.tag;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.List;

/** The tags every listing bears. */
public interface ListingTagRepository {

    List<ListingTag> findAll();

    /** All or nothing; a listing that already bears the tag keeps it once. */
    void add(Tag tag, List<VintedListingId> listings);

    /** All or nothing; a listing that does not bear the tag is left as it is. */
    void remove(Tag tag, List<VintedListingId> listings);
}
