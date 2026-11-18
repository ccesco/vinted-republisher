package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.ListingTagApi;
import fr.vinted.republisher.adapter.in.web.api.model.ListingTagsDto;
import fr.vinted.republisher.adapter.in.web.api.model.TagEditDto;
import fr.vinted.republisher.application.tag.EditListingTags;
import fr.vinted.republisher.application.tag.ShowListingTags;
import fr.vinted.republisher.domain.tag.Tag;
import fr.vinted.republisher.domain.tag.TagEdit;
import fr.vinted.republisher.domain.tag.TagOperation;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the contract-first API: tags put on, or taken off, a selection of listings, kept by the tool only. */
@RestController
public class ListingTagApiController implements ListingTagApi {

    private final EditListingTags editListingTags;
    private final ShowListingTags showListingTags;

    public ListingTagApiController(EditListingTags editListingTags, ShowListingTags showListingTags) {
        this.editListingTags = editListingTags;
        this.showListingTags = showListingTags;
    }

    @Override
    public ResponseEntity<Void> editListingTags(TagEditDto edit) {
        if (edit.getListingIds().contains(null)) {
            return ResponseEntity.badRequest().build();
        }
        editListingTags.edit(new TagEdit(edit.getListingIds().stream().map(VintedListingId::new).toList(),
                new Tag(edit.getTag()), TagOperation.valueOf(edit.getOperation().name())));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ListingTagsDto> showListingTags() {
        return ResponseEntity.ok(new ListingTagsDto().tags(showListingTags.show().stream().map(Tag::name).toList()));
    }
}
