package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.VerificationResult;
import fr.vinted.republisher.domain.deletion.DeletionOutcome;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.tag.ListingTag;
import fr.vinted.republisher.domain.tag.Tag;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Every listing known to the tool, present or not, with what the member needs to choose the ones to handle. */
public record ListingInventory(List<InventoryEntry> entries) {

    /**
     * Two attempts clicked at the same instant should not happen (one deletion at a time, behind the browser lock);
     * if they do, the attempt not found "not done" is the later one, since only that outcome allows a new attempt.
     */
    private static final Comparator<ListingDeletion> LATEST_DELETION = Comparator
            .comparing(ListingDeletion::attemptedAt)
            .thenComparing(deletion -> deletion.outcome() != DeletionOutcome.NOT_DONE);

    public ListingInventory {
        entries = List.copyOf(entries);
    }

    /**
     * {@code readings} and {@code deletions} are the latest of each listing, {@code baselines} the latest of each
     * listing made at the latest {@link RepublicationAdvice#baselineAsOf(Instant) one observation period} before
     * {@code now}; a validation counts only for the backup it was given to. Stages and advice are those at
     * {@code now}.
     */
    public static ListingInventory of(List<Listing> listings, List<StatisticsReading> readings,
                                      List<StatisticsReading> baselines, List<ListingBackup> backups,
                                      List<BackupValidation> validations, List<VerificationResult> verifications,
                                      List<ListingDeletion> deletions, List<ListingRepublication> republications,
                                      RepublicationDelay delay, Instant now) {
        Map<VintedListingId, StatisticsReading> readingOf = readings.stream()
                .collect(Collectors.toMap(StatisticsReading::listingId, Function.identity()));
        Map<VintedListingId, StatisticsReading> baselineOf = baselines.stream()
                .collect(Collectors.toMap(StatisticsReading::listingId, Function.identity()));
        Map<VintedListingId, ListingBackup> backupOf = backups.stream()
                .collect(Collectors.toMap(ListingBackup::id, Function.identity()));
        Map<VintedListingId, BackupValidation> validationOf = validations.stream()
                .collect(Collectors.toMap(BackupValidation::listingId, Function.identity()));
        Map<VintedListingId, VerificationResult> verificationOf = verifications.stream()
                .collect(Collectors.toMap(VerificationResult::listingId, Function.identity()));
        Map<VintedListingId, ListingDeletion> deletionOf = deletions.stream()
                .collect(Collectors.toMap(ListingDeletion::listingId, Function.identity(),
                        BinaryOperator.maxBy(LATEST_DELETION)));
        Map<VintedListingId, ListingRepublication> republicationOf = republications.stream()
                .collect(Collectors.toMap(ListingRepublication::listingId, Function.identity()));
        return new ListingInventory(listings.stream().map(listing -> {
            Optional<ListingBackup> backup = Optional.ofNullable(backupOf.get(listing.id()));
            Optional<BackupValidation> validation = Optional.ofNullable(validationOf.get(listing.id()))
                    .filter(given -> backup.filter(given::holdsFor).isPresent());
            boolean verificationFailed = Optional.ofNullable(verificationOf.get(listing.id()))
                    .filter(result -> backup.filter(result::failsFor).isPresent()).isPresent();
            ListingStage stage = ListingStage.of(listing.present(), backup.isPresent(), validation.isPresent(),
                    verificationFailed, Optional.ofNullable(deletionOf.get(listing.id())),
                    Optional.ofNullable(republicationOf.get(listing.id())), delay, now);
            Optional<StatisticsReading> latest = Optional.ofNullable(readingOf.get(listing.id()));
            return new InventoryEntry(listing, latest, backup, validation.map(BackupValidation::validatedAt), stage,
                    RepublicationAdvice.of(stage, latest, Optional.ofNullable(baselineOf.get(listing.id())), now));
        }).toList());
    }

    /** The same inventory, each listing bearing its tags among {@code tags}. */
    public ListingInventory withTags(List<ListingTag> tags) {
        Map<VintedListingId, List<Tag>> tagsOf = tags.stream().collect(Collectors.groupingBy(ListingTag::listingId,
                Collectors.mapping(ListingTag::tag, Collectors.toList())));
        return new ListingInventory(entries.stream()
                .map(entry -> entry.withTags(tagsOf.getOrDefault(entry.listing().id(), List.of())))
                .toList());
    }

    public List<InventoryEntry> select(InventoryCriteria criteria) {
        return entries.stream()
                .filter(criteria::accepts)
                .sorted(criteria.sort().order(criteria.direction()))
                .toList();
    }
}
