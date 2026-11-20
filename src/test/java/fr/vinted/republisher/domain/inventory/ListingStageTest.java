package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.backup.BackupVerification;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.VerificationFailure;
import fr.vinted.republisher.domain.backup.VerificationResult;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
        return ListingStage.of(present, false, backedUp, validated, false, deletion, republication, DELAY, now);
    }

    private static ListingStage stage(boolean present, boolean backedUp, boolean validated) {
        return stage(present, backedUp, validated, Optional.empty(), Optional.empty(), DELETED_AT);
    }

    private static ListingStage stageAfterVerification(boolean present, boolean validated,
                                                       boolean verificationFailed) {
        return ListingStage.of(present, false, true, validated, verificationFailed, Optional.empty(),
                Optional.empty(), DELAY, DELETED_AT);
    }

    private static ListingStage soldStage(boolean present, Optional<ListingDeletion> deletion,
                                          Optional<ListingRepublication> republication) {
        return ListingStage.of(present, true, true, true, false, deletion, republication, DELAY, DELETED_AT);
    }

    @Test
    void sold_listing_is_sold_even_present_with_a_validated_backup() {
        assertThat(soldStage(true, Optional.empty(), Optional.empty())).isEqualTo(ListingStage.SOLD);
    }

    @Test
    void sold_listing_stays_sold_once_gone_from_the_wardrobe() {
        assertThat(soldStage(false, Optional.empty(), Optional.empty())).isEqualTo(ListingStage.SOLD);
    }

    @Test
    void republication_and_deletion_win_over_the_sale() {
        assertThat(soldStage(true, unconfirmed(), Optional.empty())).isEqualTo(ListingStage.DELETION_TO_CHECK);
        assertThat(soldStage(false, confirmed(), published())).isEqualTo(ListingStage.REPUBLISHED);
    }

    @Test
    void failed_verification_of_the_current_backup_is_verification_failed() {
        assertThat(stageAfterVerification(true, false, true)).isEqualTo(ListingStage.VERIFICATION_FAILED);
    }

    @Test
    void validation_and_absence_win_over_a_failed_verification() {
        assertThat(stageAfterVerification(true, true, true)).isEqualTo(ListingStage.TO_DELETE);
        assertThat(stageAfterVerification(false, false, true)).isEqualTo(ListingStage.GONE);
    }

    @Test
    void new_backup_clears_the_verification_failure() {
        VerificationResult failed = VerificationResult.of(BACKUP,
                new BackupVerification(List.of(new VerificationFailure.ListingAbsent())), DELETED_AT);
        ListingBackup newBackup = aBackup(1, DELETED_AT.plusSeconds(60));

        assertThat(stageAfterVerification(true, false, failed.failsFor(newBackup.summary())))
                .isEqualTo(ListingStage.TO_VALIDATE);
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
    void republication_checked_not_published_is_to_republish_again() {
        Optional<ListingRepublication> notPublished =
                failed().map(republication -> republication.notPublished(DELETED_AT.plusSeconds(900)));

        assertThat(stage(false, true, true, confirmed(), notPublished, DELETED_AT.plusSeconds(900)))
                .isEqualTo(ListingStage.TO_REPUBLISH);
    }

    @Test
    void published_republication_wins_over_everything() {
        assertThat(stage(true, true, true, confirmed(), published(), DELETED_AT.plusSeconds(900)))
                .isEqualTo(ListingStage.REPUBLISHED);
    }
}
