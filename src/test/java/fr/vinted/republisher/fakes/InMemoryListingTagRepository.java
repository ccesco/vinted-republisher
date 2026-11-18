package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.tag.ListingTag;
import fr.vinted.republisher.domain.tag.ListingTagRepository;
import fr.vinted.republisher.domain.tag.Tag;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class InMemoryListingTagRepository implements ListingTagRepository {

    private final Set<ListingTag> tags = new LinkedHashSet<>();

    @Override
    public List<ListingTag> findAll() {
        return List.copyOf(tags);
    }

    @Override
    public void add(Tag tag, List<VintedListingId> listings) {
        listings.forEach(listing -> tags.add(new ListingTag(listing, tag)));
    }

    @Override
    public void remove(Tag tag, List<VintedListingId> listings) {
        listings.forEach(listing -> tags.remove(new ListingTag(listing, tag)));
    }
}
