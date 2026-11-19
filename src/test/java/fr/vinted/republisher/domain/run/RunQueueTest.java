package fr.vinted.republisher.domain.run;

import fr.vinted.republisher.domain.cadence.CadenceForecast;
import fr.vinted.republisher.domain.cadence.VintedWrite;
import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.inventory.ListingStage;
import fr.vinted.republisher.domain.relaunch.RelaunchAction;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RunQueueTest {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
    private static final RepublicationDelay DELAY = new RepublicationDelay(Duration.ofMinutes(5));
    private static final WriteCadence CADENCE = new WriteCadence(Duration.ofSeconds(30), Duration.ofMinutes(2), 140, 140);
    private static final CadenceForecast NO_WRITE_YET = new CadenceForecast(CADENCE, List.of(), List.of());
    private static final WriteModes REAL = new WriteModes(DeletionMode.REAL, RepublicationMode.REAL);
    private static final WriteModes DRY_RUN = new WriteModes(DeletionMode.DRY_RUN, RepublicationMode.DRY_RUN);

    private static QueueCandidate at(long id, ListingStage stage) {
        return new QueueCandidate(new VintedListingId(id), "Annonce " + id, stage, Optional.empty());
    }

    private static QueueCandidate waitingUntil(long id, Instant republishableAt) {
        return new QueueCandidate(new VintedListingId(id), "Annonce " + id, ListingStage.WAITING,
                Optional.of(republishableAt));
    }

    private static QueuedAction action(long id, RelaunchAction action, Instant at, boolean dryRun) {
        return new QueuedAction(new VintedListingId(id), "Annonce " + id, action, at, dryRun);
    }

    private static List<QueuedAction> forecast(List<QueueCandidate> candidates, CadenceForecast cadence,
                                               WriteModes modes) {
        RunQueue queue = RunQueue.forecast(candidates, cadence, DELAY, modes, NOW);
        assertThat(queue.computedAt()).isEqualTo(NOW);
        return queue.actions();
    }

    @Test
    void follows_the_run_order_then_the_identifiers_and_leaves_out_the_stages_the_run_does_not_take() {
        List<QueuedAction> actions = forecast(List.of(at(30, ListingStage.TO_DELETE),
                at(20, ListingStage.TO_REPUBLISH), at(11, ListingStage.DELETION_TO_CHECK),
                at(10, ListingStage.DELETION_TO_CHECK), at(12, ListingStage.REPUBLICATION_TO_CHECK),
                at(40, ListingStage.TO_VALIDATE), at(41, ListingStage.TO_BACK_UP), at(42, ListingStage.GONE),
                at(43, ListingStage.REPUBLISHED)), NO_WRITE_YET, DRY_RUN);

        assertThat(actions).containsExactly(
                action(12, RelaunchAction.REPUBLICATION_VERIFICATION, NOW, false),
                action(10, RelaunchAction.DELETION_RECHECK, NOW, false),
                action(11, RelaunchAction.DELETION_RECHECK, NOW, false),
                action(20, RelaunchAction.REPUBLICATION, NOW, true),
                action(30, RelaunchAction.DELETION, NOW, true));
    }

    @Test
    void real_writes_follow_the_cadence_and_each_deletion_leads_to_the_republication_of_its_listing() {
        CadenceForecast lastDeletionTenSecondsAgo = NO_WRITE_YET.withWriteAt(
                VintedWrite.DELETION, NOW.minusSeconds(10));

        List<QueuedAction> actions = forecast(List.of(at(1, ListingStage.TO_DELETE), at(2, ListingStage.TO_DELETE)),
                lastDeletionTenSecondsAgo, REAL);

        assertThat(actions).containsExactly(
                action(1, RelaunchAction.DELETION, NOW.plusSeconds(20), false),
                action(2, RelaunchAction.DELETION, NOW.plusSeconds(50), false),
                action(1, RelaunchAction.REPUBLICATION, NOW.plusSeconds(320), false),
                action(2, RelaunchAction.REPUBLICATION, NOW.plusSeconds(440), false));
    }

    @Test
    void a_waiting_listing_goes_before_the_deletions_once_republishable() {
        List<QueuedAction> actions = forecast(List.of(waitingUntil(5, NOW.plusSeconds(60)),
                at(1, ListingStage.TO_DELETE), at(2, ListingStage.TO_DELETE), at(3, ListingStage.TO_DELETE)),
                NO_WRITE_YET, REAL);

        assertThat(actions).startsWith(
                action(1, RelaunchAction.DELETION, NOW, false),
                action(2, RelaunchAction.DELETION, NOW.plusSeconds(30), false),
                action(5, RelaunchAction.REPUBLICATION, NOW.plusSeconds(60), false),
                action(3, RelaunchAction.DELETION, NOW.plusSeconds(60), false));
    }

    @Test
    void with_only_waiting_listings_left_the_nearest_republication_comes_first() {
        List<QueuedAction> actions = forecast(List.of(waitingUntil(1, NOW.plusSeconds(600)),
                waitingUntil(2, NOW.plusSeconds(60))), NO_WRITE_YET, REAL);

        assertThat(actions).containsExactly(
                action(2, RelaunchAction.REPUBLICATION, NOW.plusSeconds(60), false),
                action(1, RelaunchAction.REPUBLICATION, NOW.plusSeconds(600), false));
    }

    @Test
    void a_dry_run_write_takes_no_cadence_and_has_no_follow_up() {
        WriteModes dryRunDeletions = new WriteModes(DeletionMode.DRY_RUN, RepublicationMode.REAL);
        CadenceForecast justWritten = NO_WRITE_YET.withWriteAt(
                VintedWrite.REPUBLICATION, NOW);

        List<QueuedAction> actions = forecast(List.of(at(1, ListingStage.TO_DELETE), at(2, ListingStage.TO_REPUBLISH)),
                justWritten, dryRunDeletions);

        assertThat(actions).containsExactly(
                action(2, RelaunchAction.REPUBLICATION, NOW.plusSeconds(120), false),
                action(1, RelaunchAction.DELETION, NOW.plusSeconds(120), true));
    }

    @Test
    void without_candidate_the_queue_is_empty() {
        assertThat(forecast(List.of(), NO_WRITE_YET, REAL)).isEmpty();
    }

    @Test
    void a_waiting_listing_without_republication_date_is_left_out() {
        List<QueuedAction> actions = forecast(List.of(at(1, ListingStage.WAITING)), NO_WRITE_YET, REAL);

        assertThat(actions).isEmpty();
    }
}
