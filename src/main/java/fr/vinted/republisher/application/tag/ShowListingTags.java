package fr.vinted.republisher.application.tag;

import fr.vinted.republisher.domain.tag.ListingTag;
import fr.vinted.republisher.domain.tag.ListingTagRepository;
import fr.vinted.republisher.domain.tag.Tag;

import java.util.List;

/** Every tag borne by at least one listing, once, in alphabetical order. */
public class ShowListingTags {

    private final ListingTagRepository tags;

    public ShowListingTags(ListingTagRepository tags) {
        this.tags = tags;
    }

    public List<Tag> show() {
        return tags.findAll().stream().map(ListingTag::tag).distinct().sorted().toList();
    }
}
