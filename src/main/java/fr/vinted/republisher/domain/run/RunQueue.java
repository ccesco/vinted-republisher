package fr.vinted.republisher.domain.run;

import fr.vinted.republisher.domain.cadence.CadenceForecast;
import fr.vinted.republisher.domain.cadence.VintedWrite;
import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.inventory.ListingStage;
import fr.vinted.republisher.domain.relaunch.RelaunchAction;
import fr.vinted.republisher.domain.republication.RepublicationDelay;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The actions the automatic run would launch, in its order, each at the earliest (US-41): foreseen by playing the run
 * on a clock that starts at {@code computedAt}, every action taken as instantaneous.
 */
public record RunQueue(Instant computedAt, List<QueuedAction> actions) {

    /** Stages the run takes, in its order (US-19). */
    private static final List<ListingStage> ORDER = List.of(ListingStage.REPUBLICATION_TO_CHECK,
            ListingStage.DELETION_TO_CHECK, ListingStage.TO_REPUBLISH, ListingStage.TO_DELETE, ListingStage.WAITING);

    public RunQueue {
        Objects.requireNonNull(computedAt);
        actions = List.copyOf(actions);
    }

    /**
     * Each turn takes the first listing in the run's order. A check goes at once; a real write waits for the cadence,
     * the turn starting again once it allows it (a listing republishable meanwhile then goes first), and a deletion
     * leads to the republication of its listing after the delay. A dry-run write goes at once, without follow-up: the
     * run sets the listing aside. A waiting listing without republication date is left out.
     */
    public static RunQueue forecast(List<QueueCandidate> candidates, CadenceForecast cadence,
                                    RepublicationDelay delay, WriteModes modes, Instant now) {
        List<QueueCandidate> pending = new ArrayList<>(candidates.stream()
                .filter(candidate -> ORDER.contains(candidate.stage()))
                .filter(candidate -> candidate.stage() != ListingStage.WAITING
                        || candidate.republishableAt().isPresent())
                .toList());
        List<QueuedAction> actions = new ArrayList<>();
        Instant at = now;
        CadenceForecast writes = cadence;
        while (!pending.isEmpty()) {
            Instant turn = at;
            QueueCandidate next = pending.stream()
                    .min(Comparator.comparingInt((QueueCandidate candidate) -> ORDER.indexOf(candidate.stageAt(turn)))
                            .thenComparingLong(candidate -> candidate.listingId().value()))
                    .orElseThrow();
            Optional<RelaunchAction> action = RelaunchAction.after(next.stageAt(turn), WriteOrigin.RUN);
            if (action.isEmpty()) {
                at = nearestRepublication(pending);
                continue;
            }
            Optional<VintedWrite> write = modes.realWriteOf(action.get());
            if (write.isPresent()) {
                Instant allowedAt = writes.allowedAt(write.get(), turn);
                if (turn.isBefore(allowedAt)) {
                    at = allowedAt;
                    continue;
                }
                writes = writes.withWriteAt(write.get(), turn);
            }
            pending.remove(next);
            actions.add(new QueuedAction(next.listingId(), next.title(), action.get(), turn,
                    modes.dryRun(action.get())));
            if (write.filter(VintedWrite.DELETION::equals).isPresent()) {
                pending.add(next.waitingUntil(turn.plus(delay.minimum())));
            }
        }
        return new RunQueue(now, actions);
    }

    /** Only waiting listings are left, none republishable yet. */
    private static Instant nearestRepublication(List<QueueCandidate> waiting) {
        return waiting.stream().map(QueueCandidate::republishableAt).flatMap(Optional::stream)
                .min(Comparator.naturalOrder()).orElseThrow();
    }
}
