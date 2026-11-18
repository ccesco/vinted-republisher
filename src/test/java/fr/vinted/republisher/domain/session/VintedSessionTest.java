package fr.vinted.republisher.domain.session;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static fr.vinted.republisher.domain.session.BrowserAction.CHECKING;
import static fr.vinted.republisher.domain.session.BrowserAction.DELETING;
import static fr.vinted.republisher.domain.session.BrowserAction.LOGGING_IN;
import static fr.vinted.republisher.domain.session.BrowserAction.REPUBLISHING;
import static fr.vinted.republisher.domain.session.BrowserAction.SCANNING;
import static fr.vinted.republisher.domain.session.SessionProblem.BROWSER_UNAVAILABLE;
import static fr.vinted.republisher.domain.session.SessionProblem.MANUAL_LOGIN_NOT_COMPLETED;
import static fr.vinted.republisher.domain.session.SessionStatus.CONNECTED;
import static fr.vinted.republisher.domain.session.SessionStatus.DISCONNECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class VintedSessionTest {

    private final VintedSession session = new VintedSession();

    @Test
    void is_unchecked_and_idle_until_asked() {
        assertThat(session.overview()).isEqualTo(SessionOverview.unchecked());
    }

    @Nested
    class Browser_action {

        @Test
        void is_shown_while_in_progress() {
            session.start(CHECKING);

            assertThat(session.overview().currentAction()).contains(CHECKING);
        }

        @Test
        void cannot_start_while_another_is_in_progress() {
            session.start(LOGGING_IN);

            boolean started = session.start(CHECKING);

            assertThat(started).isFalse();
            assertThat(session.overview().currentAction()).contains(LOGGING_IN);
        }

        @Test
        void records_the_status_found_when_complete() {
            session.start(CHECKING);

            session.complete(CONNECTED);

            assertThat(session.overview()).isEqualTo(SessionOverview.idle(CONNECTED));
        }

        @Test
        void cannot_complete_when_none_is_in_progress() {
            Throwable attempt = catchThrowable(() -> session.complete(CONNECTED));

            assertThat(attempt).isInstanceOf(NoBrowserActionInProgress.class);
        }

        @Test
        void starting_a_new_one_clears_the_previous_problem() {
            session.start(CHECKING);
            session.fail(BROWSER_UNAVAILABLE);

            session.start(CHECKING);

            assertThat(session.overview().problem()).isEmpty();
        }
    }

    /** US-15: up to N listing actions at once, never two on the same listing; other actions stay alone. */
    @Nested
    class Parallelism {

        private static final VintedListingId ROBE = new VintedListingId(1);
        private static final VintedListingId PULL = new VintedListingId(2);
        private static final VintedListingId JUPE = new VintedListingId(3);

        private final VintedSession twoAtOnce = new VintedSession(2);

        @Test
        void admits_up_to_the_parallelism() {
            assertThat(twoAtOnce.start(DELETING, ROBE)).isTrue();
            assertThat(twoAtOnce.start(REPUBLISHING, PULL)).isTrue();

            assertThat(twoAtOnce.start(DELETING, JUPE)).isFalse();

            twoAtOnce.finish(ROBE);
            assertThat(twoAtOnce.start(DELETING, JUPE)).isTrue();
            assertThat(twoAtOnce.overview().currentAction()).contains(REPUBLISHING);
        }

        @Test
        void never_two_actions_on_the_same_listing() {
            twoAtOnce.start(DELETING, ROBE);

            assertThat(twoAtOnce.start(REPUBLISHING, ROBE)).isFalse();
        }

        @Test
        void other_actions_stay_alone() {
            twoAtOnce.start(DELETING, ROBE);
            assertThat(twoAtOnce.start(SCANNING)).isFalse();
            twoAtOnce.finish(ROBE);

            twoAtOnce.start(SCANNING);
            assertThat(twoAtOnce.start(DELETING, ROBE)).isFalse();
            twoAtOnce.finish();
            assertThat(twoAtOnce.overview().currentAction()).isEmpty();
        }

        @Test
        void one_by_default() {
            session.start(DELETING, ROBE);

            assertThat(session.start(DELETING, PULL)).isFalse();
        }

        @Test
        void at_least_one() {
            assertThat(catchThrowable(() -> new VintedSession(0))).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void an_action_on_a_listing_never_ends_as_an_action_alone() {
            twoAtOnce.start(DELETING, ROBE);

            assertThat(catchThrowable(twoAtOnce::finish)).isInstanceOf(NoBrowserActionInProgress.class);
            assertThat(twoAtOnce.overview().currentAction()).contains(DELETING);
        }

        @Test
        void only_an_action_on_a_listing_shares_the_browser() {
            assertThat(catchThrowable(() -> twoAtOnce.start(SCANNING, ROBE)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void cannot_finish_a_listing_without_action() {
            assertThat(catchThrowable(() -> twoAtOnce.finish(ROBE))).isInstanceOf(NoBrowserActionInProgress.class);
        }
    }

    @Nested
    class Manual_login {

        @Test
        void not_completed_is_reported_as_a_problem() {
            session.start(LOGGING_IN);

            session.complete(DISCONNECTED);

            assertThat(session.overview().status()).contains(DISCONNECTED);
            assertThat(session.overview().problem()).contains(MANUAL_LOGIN_NOT_COMPLETED);
        }

        @Test
        void check_finding_a_closed_session_is_not_a_problem() {
            session.start(CHECKING);

            session.complete(DISCONNECTED);

            assertThat(session.overview().problem()).isEmpty();
        }
    }

    @Nested
    class Unavailable_browser {

        @Test
        void leaves_the_session_disconnected_with_a_problem() {
            session.start(CHECKING);
            session.complete(CONNECTED);
            session.start(CHECKING);

            session.fail(BROWSER_UNAVAILABLE);

            assertThat(session.overview().status()).contains(DISCONNECTED);
            assertThat(session.overview().problem()).contains(BROWSER_UNAVAILABLE);
            assertThat(session.overview().currentAction()).isEmpty();
        }

        @Test
        void can_end_without_changing_what_is_known_of_the_session() {
            session.start(CHECKING);
            session.complete(CONNECTED);
            session.start(BrowserAction.SCANNING);

            session.finish();

            assertThat(session.overview()).isEqualTo(SessionOverview.idle(CONNECTED));
        }

        @Test
        void scan_keeps_the_problem_of_the_session() {
            session.start(CHECKING);
            session.fail(BROWSER_UNAVAILABLE);

            session.start(BrowserAction.SCANNING);

            assertThat(session.overview().problem()).contains(BROWSER_UNAVAILABLE);
        }

        @Test
        void backup_keeps_the_problem_of_the_session() {
            session.start(CHECKING);
            session.fail(BROWSER_UNAVAILABLE);

            session.start(BrowserAction.BACKING_UP);

            assertThat(session.overview().problem()).contains(BROWSER_UNAVAILABLE);
        }

        @Test
        void cannot_be_reported_when_no_action_is_in_progress() {
            Throwable attempt = catchThrowable(() -> session.fail(BROWSER_UNAVAILABLE));

            assertThat(attempt).isInstanceOf(NoBrowserActionInProgress.class);
        }
    }
}
