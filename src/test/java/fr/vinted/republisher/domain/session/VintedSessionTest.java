package fr.vinted.republisher.domain.session;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static fr.vinted.republisher.domain.session.BrowserAction.CHECKING;
import static fr.vinted.republisher.domain.session.BrowserAction.LOGGING_IN;
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
        void cannot_be_reported_when_no_action_is_in_progress() {
            Throwable attempt = catchThrowable(() -> session.fail(BROWSER_UNAVAILABLE));

            assertThat(attempt).isInstanceOf(NoBrowserActionInProgress.class);
        }
    }
}
