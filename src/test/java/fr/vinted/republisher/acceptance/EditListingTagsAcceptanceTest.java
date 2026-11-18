package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.application.tag.EditListingTags;
import fr.vinted.republisher.application.tag.ShowListingTags;
import fr.vinted.republisher.domain.inventory.BackupFilter;
import fr.vinted.republisher.domain.inventory.InventoryCriteria;
import fr.vinted.republisher.domain.inventory.InventoryEntry;
import fr.vinted.republisher.domain.inventory.InventorySort;
import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.inventory.PresenceFilter;
import fr.vinted.republisher.domain.inventory.SortDirection;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.tag.Tag;
import fr.vinted.republisher.domain.tag.TagEdit;
import fr.vinted.republisher.domain.tag.TagOperation;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.InMemoryBackupVerificationRepository;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import fr.vinted.republisher.fakes.InMemoryListingTagRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US-34: the member puts tags on, or takes them off, a selection of listings, and filters the list on a tag. */
class EditListingTagsAcceptanceTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-17T09:00:00Z");

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingTagRepository tags = new InMemoryListingTagRepository();
    private final EditListingTags editListingTags = new EditListingTags(listings, tags);
    private final ShowListingTags showListingTags = new ShowListingTags(tags);
    private final ShowListingInventory showListingInventory = new ShowListingInventory(listings,
            new InMemoryListingStatisticsRepository(), new InMemoryListingBackupRepository(),
            new InMemoryBackupValidationRepository(), new InMemoryListingDeletionRepository(),
            new InMemoryListingRepublicationRepository(), new RepublicationDelay(Duration.ofMinutes(5)),
            Clock.systemUTC(), tags, new InMemoryBackupVerificationRepository());

    @Test
    void adds_a_tag_to_every_selected_listing() {
        // Étant donné les annonces 101, 102 et 103, et 102 portant déjà le tag « été »
        known(101, 102, 103);
        tags.add(new Tag("été"), ids(102));

        // Quand le membre ajoute le tag "  Été " aux annonces 101 et 102
        editListingTags.edit(new TagEdit(ids(101, 102), new Tag("  Été "), TagOperation.ADD));

        // Alors 101 et 102 portent le tag « été », une seule fois chacune
        //   et 103 ne porte aucun tag
        assertThat(tagsShown()).isEqualTo(Map.of(101L, List.of("été"), 102L, List.of("été"), 103L, List.of()));
    }

    @Test
    void removes_a_tag_from_every_selected_listing() {
        // Étant donné 101 portant « été » et « lot », 102 portant « lot », 103 portant « été »
        known(101, 102, 103);
        tags.add(new Tag("été"), ids(101, 103));
        tags.add(new Tag("lot"), ids(101, 102));

        // Quand le membre retire le tag « ÉTÉ » des annonces 101 et 102
        editListingTags.edit(new TagEdit(ids(101, 102), new Tag("ÉTÉ"), TagOperation.REMOVE));

        // Alors 101 porte seulement « lot », 102 porte seulement « lot », 103 porte toujours « été »
        assertThat(tagsShown())
                .isEqualTo(Map.of(101L, List.of("lot"), 102L, List.of("lot"), 103L, List.of("été")));
    }

    @Test
    void refuses_the_whole_edit_when_a_listing_is_unknown() {
        // Étant donné l'annonce 101 sans tag, et aucune annonce 999
        known(101);

        // Quand le membre ajoute le tag « été » aux annonces 101 et 999
        // Alors l'édition est refusée : annonce 999 inconnue
        assertThatThrownBy(() -> editListingTags.edit(new TagEdit(ids(101, 999), new Tag("été"), TagOperation.ADD)))
                .isInstanceOf(ListingNotFound.class).hasMessage("Unknown listing 999");
        //   et 101 ne porte aucun tag
        assertThat(tags.findAll()).isEmpty();
    }

    @Test
    void inventory_shows_and_filters_on_tags() {
        // Étant donné 101 portant « lot » et « été », 102 portant « lot », 103 sans tag
        known(101, 102, 103);
        tags.add(new Tag("lot"), ids(101, 102));
        tags.add(new Tag("été"), ids(101));

        // Quand le membre affiche la liste
        // Alors 101 montre « été, lot », 102 « lot », 103 aucun tag
        assertThat(tagsShown())
                .isEqualTo(Map.of(101L, List.of("été", "lot"), 102L, List.of("lot"), 103L, List.of()));
        // Quand il filtre sur le tag "Été"
        // Alors il voit seulement 101
        assertThat(idsTagged("Été")).containsExactly(101L);
        // Quand il filtre sur le tag « hiver »
        // Alors la liste est vide
        assertThat(idsTagged("hiver")).isEmpty();
    }

    @Test
    void lists_every_tag_once_in_alphabetical_order() {
        // Étant donné 101 portant « lot » et « zèbre », 102 portant « lot » et « été »
        known(101, 102);
        tags.add(new Tag("lot"), ids(101, 102));
        tags.add(new Tag("zèbre"), ids(101));
        tags.add(new Tag("été"), ids(102));

        // Quand la page demande les tags
        // Alors elle reçoit « été », « lot » puis « zèbre », chacun une fois
        assertThat(showListingTags.show()).extracting(Tag::name).containsExactly("été", "lot", "zèbre");
    }

    private void known(long... ids) {
        listings.saveAll(LongStream.of(ids)
                .mapToObj(id -> Listing.seen(aListing(id, "Annonce " + id, "1", ACTIVE), SCANNED_AT)).toList());
    }

    private static List<VintedListingId> ids(long... ids) {
        return LongStream.of(ids).mapToObj(VintedListingId::new).toList();
    }

    private Map<Long, List<String>> tagsShown() {
        return showListingInventory.show(InventoryCriteria.defaults()).stream().collect(Collectors.toMap(
                entry -> entry.listing().id().value(), entry -> entry.tags().stream().map(Tag::name).toList()));
    }

    private List<Long> idsTagged(String tag) {
        InventoryCriteria criteria = new InventoryCriteria(PresenceFilter.PRESENT, BackupFilter.ALL, "",
                InventorySort.RECENCY, SortDirection.DESC, Optional.of(new Tag(tag)));
        return showListingInventory.show(criteria).stream().map(InventoryEntry::listing)
                .map(listing -> listing.id().value()).toList();
    }
}
