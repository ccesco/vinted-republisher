package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static org.assertj.core.api.Assertions.assertThat;

class ListingStageTest {

    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant DELETED_AT = Instant.parse("2026-11-12T10:00:00Z");
    private static final RepublicationDelay DELAY = new RepublicationDelay(Duration.ofMinutes(5));
    private static final ListingBackup BACKUP = aBackup(1, SAVED_AT);

    private static ListingStage stage(boolean present, boolean backedUp, boolean validated,
                                      Optional<ListingDeletion> deletion,
                                      Optional<ListingRepublication> republication, Instant now) {
        return ListingStage.of(present, backedUp, validated, deletion, republication, DELAY, now);
    }

    private static ListingStage stage(boolean present, boolean backedUp, boolean validated) {
        return stage(present, backedUp, validated, Optional.empty(), Optional.empty(), DELETED_AT);
    }

    private static Optional<ListingDeletion> unconfirmed() {
        return Optional.of(ListingDeletion.attempted(BACKUP, DELETED_AT.minusSeconds(2)));
    }

    private static Optional<ListingDeletion> confirmed() {
        return unconfirmed().map(deletion -> deletion.confirmed(DELETED_AT));
    }

    private static Optional<ListingRepublication> failed() {
        return Optional.of(ListingRepublication.attempted(BACKUP, DELETED_AT.plusSeconds(600)));
    }

    private static Optional<ListingRepublication> published() {
        return failed().map(republication -> republication.published(new VintedListingId(2),
                DELETED_AT.plusSeconds(610)));
    }

    @Test
    void present_listing_without_backup_is_to_back_up() {
        assertThat(stage(true, false, false)).isEqualTo(ListingStage.TO_BACK_UP);
    }

    @Test
    void unvalidated_backup_is_to_validate() {
        assertThat(stage(true, true, false)).isEqualTo(ListingStage.TO_VALIDATE);
    }

    @Test
    void validated_backup_is_to_delete() {
        assertThat(stage(true, true, true)).isEqualTo(ListingStage.TO_DELETE);
    }

    @Test
    void absent_listing_without_deletion_is_gone_whatever_its_backup() {
        assertThat(stage(false, false, false)).isEqualTo(ListingStage.GONE);
        assertThat(stage(false, true, true)).isEqualTo(ListingStage.GONE);
    }

    @Test
    void unconfirmed_deletion_is_to_check() {
        assertThat(stage(true, true, true, unconfirmed(), Optional.empty(), DELETED_AT))
                .isEqualTo(ListingStage.DELETION_TO_CHECK);
    }

    @Test
    void deletion_not_done_is_ignored() {
        Optional<ListingDeletion> notDone = unconfirmed().map(deletion -> deletion.notDone(DELETED_AT));

        assertThat(stage(true, true, true, notDone, Optional.empty(), DELETED_AT))
                .isEqualTo(ListingStage.TO_DELETE);
        assertThat(stage(false, true, true, notDone, Optional.empty(), DELETED_AT))
                .isEqualTo(ListingStage.GONE);
    }

    @Test
    void confirmed_deletion_waits_until_the_republication_date() {
        assertThat(stage(false, true, true, confirmed(), Optional.empty(), DELETED_AT.plusSeconds(299)))
                .isEqualTo(ListingStage.WAITING);
        assertThat(stage(false, true, true, confirmed(), Optional.empty(), DELETED_AT.plusSeconds(300)))
                .isEqualTo(ListingStage.TO_REPUBLISH);
    }

    @Test
    void confirmed_deletion_wins_over_presence() {
        assertThat(stage(true, true, true, confirmed(), Optional.empty(), DELETED_AT.plusSeconds(300)))
                .isEqualTo(ListingStage.TO_REPUBLISH);
    }

    @Test
    void failed_republication_is_to_check() {
        assertThat(stage(false, true, true, confirmed(), failed(), DELETED_AT.plusSeconds(900)))
                .isEqualTo(ListingStage.REPUBLICATION_TO_CHECK);
    }

    @Test
    void published_republication_wins_over_everything() {
        assertThat(stage(true, true, true, confirmed(), published(), DELETED_AT.plusSeconds(900)))
                .isEqualTo(ListingStage.REPUBLISHED);
    }
}
