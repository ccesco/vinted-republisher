package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.model.TagOperationDto;
import fr.vinted.republisher.application.tag.EditListingTags;
import fr.vinted.republisher.application.tag.ShowListingTags;
import fr.vinted.republisher.domain.tag.ListingTag;
import fr.vinted.republisher.domain.tag.Tag;
import fr.vinted.republisher.domain.tag.TagOperation;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

/** US-34: the API answers as described by the OpenAPI contract. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(ListingTagApiController.class)
class ListingTagsApiControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private InMemoryListingRepository listings;

    @Autowired
    private InMemoryListingTagRepository tags;

    @BeforeEach
    void knowTwoListings() {
        Instant seenAt = Instant.parse("2026-11-17T09:00:00Z");
        listings.saveAll(List.of(Listing.seen(anActiveListing(17), seenAt), Listing.seen(anActiveListing(18), seenAt)));
    }

    @Test
    void edits_tags_and_refuses_invalid_edits() {
        // Quand la page envoie POST /api/listings/tag-edits avec une sélection connue, le tag "été" et ADD
        // Alors la réponse est 204
        assertThat(edit("[17, 18]", "\"Été\"", "ADD")).hasStatus(204);
        assertThat(tags.findAll()).containsExactly(tag(17, "été"), tag(18, "été"));
        // Quand la sélection contient une annonce inconnue
        // Alors la réponse est 404
        assertThat(edit("[17, 999]", "\"lot\"", "ADD")).hasStatus(404);
        // Quand le tag est vide, fait de 31 caractères mis en forme, la sélection vide ou l'opération inconnue
        // Alors la réponse est 400
        assertThat(edit("[17]", "\"   \"", "ADD")).hasStatus(400);
        assertThat(edit("[17]", "\"" + "a".repeat(31) + "\"", "ADD")).hasStatus(400);
        assertThat(edit("[]", "\"lot\"", "ADD")).hasStatus(400);
        assertThat(edit("[17]", "\"lot\"", "RENAME")).hasStatus(400);
        assertThat(edit("[17, null]", "\"lot\"", "ADD")).hasStatus(400);
        assertThat(edit("[0]", "\"lot\"", "ADD")).hasStatus(400);
        assertThat(tags.findAll()).containsExactly(tag(17, "été"), tag(18, "été"));
    }

    @Test
    void removes_a_tag_from_the_selection() {
        tags.add(new Tag("été"), List.of(new VintedListingId(17), new VintedListingId(18)));

        assertThat(edit("[18]", "\"été\"", "REMOVE")).hasStatus(204);

        assertThat(tags.findAll()).containsExactly(tag(17, "été"));
    }

    @Test
    void lists_every_tag_once_in_alphabetical_order() {
        tags.add(new Tag("lot"), List.of(new VintedListingId(17), new VintedListingId(18)));
        tags.add(new Tag("été"), List.of(new VintedListingId(18)));

        assertThat(mvc.get().uri("/api/listing-tags"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("{\"tags\": [\"été\", \"lot\"]}");
    }

    @Test
    void contract_knows_every_operation() {
        assertThat(Arrays.stream(TagOperationDto.values()).map(Enum::name))
                .containsExactly(Arrays.stream(TagOperation.values()).map(Enum::name).toArray(String[]::new));
    }

    private MockMvcTester.MockMvcRequestBuilder edit(String listingIds, String tag, String operation) {
        return mvc.post().uri("/api/listings/tag-edits").contentType(MediaType.APPLICATION_JSON)
                .content("{\"listingIds\": " + listingIds + ", \"tag\": " + tag + ", \"operation\": \""
                        + operation + "\"}");
    }

    private static ListingTag tag(long listing, String tag) {
        return new ListingTag(new VintedListingId(listing), new Tag(tag));
    }

    @TestConfiguration
    static class TagUseCases {

        @Bean
        InMemoryListingRepository listings() {
            return new InMemoryListingRepository();
        }

        @Bean
        InMemoryListingTagRepository tags() {
            return new InMemoryListingTagRepository();
        }

        @Bean
        EditListingTags editListingTags(InMemoryListingRepository listings, InMemoryListingTagRepository tags) {
            return new EditListingTags(listings, tags);
        }

        @Bean
        ShowListingTags showListingTags(InMemoryListingTagRepository tags) {
            return new ShowListingTags(tags);
        }
    }
}
