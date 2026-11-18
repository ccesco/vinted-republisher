package fr.vinted.republisher.domain.tag;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TagEditTest {

    @Test
    void edits_at_least_one_listing() {
        assertThatThrownBy(() -> new TagEdit(List.of(), new Tag("lot"), TagOperation.ADD))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("No listing selected");
    }

    @Test
    void keeps_each_selected_listing_once() {
        VintedListingId listing = new VintedListingId(101);

        assertThat(new TagEdit(List.of(listing, listing), new Tag("lot"), TagOperation.REMOVE).listings())
                .containsExactly(listing);
    }
}
