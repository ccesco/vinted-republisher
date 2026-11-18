package fr.vinted.republisher.application.tag;

import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.tag.ListingTagRepository;
import fr.vinted.republisher.domain.tag.TagEdit;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.Set;
import java.util.stream.Collectors;

/** Puts a tag on, or takes it off, every selected listing, or none if one is unknown. Never touches Vinted. */
public class EditListingTags {

    private final ListingRepository listings;
    private final ListingTagRepository tags;

    public EditListingTags(ListingRepository listings, ListingTagRepository tags) {
        this.listings = listings;
        this.tags = tags;
    }

    public void edit(TagEdit edit) {
        Set<VintedListingId> known = listings.findAll().stream().map(listing -> listing.id())
                .collect(Collectors.toSet());
        edit.listings().stream().filter(listing -> !known.contains(listing)).findFirst().ifPresent(unknown -> {
            throw new ListingNotFound(unknown);
        });
        switch (edit.operation()) {
            case ADD -> tags.add(edit.tag(), edit.listings());
            case REMOVE -> tags.remove(edit.tag(), edit.listings());
        }
    }
}
