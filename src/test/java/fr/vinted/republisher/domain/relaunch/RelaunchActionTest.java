package fr.vinted.republisher.domain.relaunch;

import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.inventory.ListingStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class RelaunchActionTest {

    @ParameterizedTest
    @CsvSource({
            "TO_DELETE, DELETION",
            "DELETION_TO_CHECK, DELETION_RECHECK",
            "TO_REPUBLISH, REPUBLICATION",
            "REPUBLICATION_TO_CHECK, REPUBLICATION_VERIFICATION"})
    void each_relaunchable_stage_has_its_action(ListingStage stage, RelaunchAction action) {
        assertThat(RelaunchAction.after(stage, WriteOrigin.RUN)).contains(action);
    }

    @ParameterizedTest
    @EnumSource(value = ListingStage.class, names = {"TO_BACK_UP", "TO_VALIDATE", "WAITING", "GONE", "REPUBLISHED"})
    void other_stages_have_none_for_the_run(ListingStage stage) {
        assertThat(RelaunchAction.after(stage, WriteOrigin.RUN)).isEmpty();
    }

    @Test
    void a_waiting_listing_is_republished_on_the_member_s_click() {
        assertThat(RelaunchAction.after(ListingStage.WAITING,
                WriteOrigin.MANUAL)).contains(RelaunchAction.REPUBLICATION);
    }

    @ParameterizedTest
    @EnumSource(value = ListingStage.class, names = {"TO_BACK_UP", "TO_VALIDATE", "GONE", "REPUBLISHED"})
    void other_stages_have_none_on_a_click(ListingStage stage) {
        assertThat(RelaunchAction.after(stage, WriteOrigin.MANUAL)).isEmpty();
    }
}
