package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.application.sale.CancelNotedSale;
import fr.vinted.republisher.application.sale.NoteSale;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.inventory.InventoryCriteria;
import fr.vinted.republisher.domain.inventory.InventoryEntry;
import fr.vinted.republisher.domain.inventory.ListingStage;
import fr.vinted.republisher.domain.relaunch.RelaunchAction;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.sale.ListingAlreadySold;
import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.sale.SaleNotNoted;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryBackupVerificationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingSaleRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import fr.vinted.republisher.fakes.InMemoryListingTagRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US-51: the member notes a listing sold by hand, and may cancel that noted sale. */
class NoteSaleAcceptanceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final Instant EARLIER = NOW.minus(Duration.ofHours(1));
    private static final VintedListingId LISTING = new VintedListingId(1);

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingSaleRepository sales = new InMemoryListingSaleRepository();
    private final InMemoryListingBackupRepository backups = new InMemoryListingBackupRepository();
    private final InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
    private final InMemoryListingDeletionRepository deletions = new InMemoryListingDeletionRepository();
    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final NoteSale noteSale = new NoteSale(listings, sales, clock);
    private final CancelNotedSale cancelNotedSale = new CancelNotedSale(listings, sales);

    @Test
    void a_listing_noted_sold_is_sold_whatever_its_stage() {
        // AT-51-01. Étant donné l'annonce 1 « À republier » (supprimée par l'outil, délai écoulé)
        aListingToRepublish();
        assertThat(stageOf(LISTING)).isEqualTo(ListingStage.TO_REPUBLISH);

        // Quand le membre la note vendue
        noteSale.note(LISTING);

        // Alors elle a une vente notée maintenant, elle est « Vendue », et la série ne la prend pas
        assertThat(sales.findAll()).containsExactly(ListingSale.noted(LISTING, NOW));
        assertThat(stageOf(LISTING)).isEqualTo(ListingStage.SOLD);
        assertThat(RelaunchAction.after(ListingStage.SOLD)).isEmpty();
    }

    @Test
    void a_listing_to_delete_noted_sold_is_no_longer_to_delete() {
        // AT-51-02. Étant donné l'annonce 1 « À supprimer »
        aListingToDelete();
        assertThat(stageOf(LISTING)).isEqualTo(ListingStage.TO_DELETE);

        // Quand le membre la note vendue
        noteSale.note(LISTING);

        // Alors elle est « Vendue »
        assertThat(stageOf(LISTING)).isEqualTo(ListingStage.SOLD);
    }

    @Test
    void a_listing_already_sold_cannot_be_noted_sold() {
        // AT-51-03. Étant donné l'annonce 1 avec une vente constatée
        aListingToDelete();
        ListingSale noticed = ListingSale.noticed(LISTING, EARLIER);
        sales.addAll(List.of(noticed));

        // Quand le membre la note vendue, c'est refusé, et sa vente reste constatée
        assertThatThrownBy(() -> noteSale.note(LISTING)).isInstanceOf(ListingAlreadySold.class);
        assertThat(sales.findAll()).containsExactly(noticed);
    }

    @Test
    void cancelling_a_noted_sale_gives_back_the_previous_stage() {
        // AT-51-04. Étant donné l'annonce 1 « À republier », notée vendue
        aListingToRepublish();
        noteSale.note(LISTING);

        // Quand le membre annule la vente
        cancelNotedSale.cancel(LISTING);

        // Alors elle n'a plus de vente et redevient « À republier »
        assertThat(sales.findAll()).isEmpty();
        assertThat(stageOf(LISTING)).isEqualTo(ListingStage.TO_REPUBLISH);
    }

    @Test
    void a_noticed_sale_cannot_be_cancelled() {
        // AT-51-05. Étant donné l'annonce 1 avec une vente constatée
        aListingToDelete();
        ListingSale noticed = ListingSale.noticed(LISTING, EARLIER);
        sales.addAll(List.of(noticed));

        // Quand le membre demande d'annuler la vente, c'est refusé
        assertThatThrownBy(() -> cancelNotedSale.cancel(LISTING)).isInstanceOf(SaleNotNoted.class);
        assertThat(sales.findAll()).containsExactly(noticed);
    }

    private void aListingToDelete() {
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), EARLIER)));
        ListingBackup backup = aBackup(1, EARLIER);
        backups.save(backup);
        validations.save(BackupValidation.of(backup, EARLIER));
    }

    private void aListingToRepublish() {
        aListingToDelete();
        Instant deletedAt = NOW.minus(Duration.ofMinutes(10));
        deletions.save(ListingDeletion.attempted(aBackup(1, EARLIER), deletedAt).confirmed(deletedAt));
    }

    private ListingStage stageOf(VintedListingId id) {
        ShowListingInventory inventory = new ShowListingInventory(listings,
                new InMemoryListingStatisticsRepository(), backups, validations, deletions,
                new InMemoryListingRepublicationRepository(), new RepublicationDelay(Duration.ofMinutes(5)), clock,
                new InMemoryListingTagRepository(), new InMemoryBackupVerificationRepository(), sales);
        return inventory.show(InventoryCriteria.everyListing()).stream()
                .filter(entry -> entry.listing().id().equals(id))
                .map(InventoryEntry::stage).findFirst().orElseThrow();
    }
}
