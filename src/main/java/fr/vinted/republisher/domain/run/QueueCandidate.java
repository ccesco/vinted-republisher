package fr.vinted.republisher.domain.run;

import fr.vinted.republisher.domain.inventory.ListingStage;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** A listing the run queue may take: where it stands and, while it waits, from when it is republishable. */
public record QueueCandidate(VintedListingId listingId, String title, ListingStage stage,
                             Optional<Instant> republishableAt) {

    public QueueCandidate {
        Objects.requireNonNull(listingId);
        Objects.requireNonNull(title);
        Objects.requireNonNull(stage);
        Objects.requireNonNull(republishableAt);
    }

    /** Its stage at {@code at}: a waiting listing whose republication date has come is to republish. */
    ListingStage stageAt(Instant at) {
        boolean republishable = republishableAt.filter(date -> !at.isBefore(date)).isPresent();
        return stage == ListingStage.WAITING && republishable ? ListingStage.TO_REPUBLISH : stage;
    }

    QueueCandidate waitingUntil(Instant date) {
        return new QueueCandidate(listingId, title, ListingStage.WAITING, Optional.of(date));
    }
}
