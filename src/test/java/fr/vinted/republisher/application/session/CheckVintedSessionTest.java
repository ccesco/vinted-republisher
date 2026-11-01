package fr.vinted.republisher.application.session;

import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.SessionOverview;
import fr.vinted.republisher.domain.session.SessionProblem;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.fakes.FakeVintedAccount;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;

import static fr.vinted.republisher.domain.session.SessionStatus.CONNECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class CheckVintedSessionTest {

    private final VintedSession session = new VintedSession();

    @Test
    void records_the_status_found_on_vinted() {
        new CheckVintedSession(session, FakeVintedAccount.withMemberConnected(), Runnable::run).check();

        assertThat(session.overview()).isEqualTo(SessionOverview.idle(CONNECTED));
    }

    @Test
    void is_ignored_while_another_browser_action_is_in_progress() {
        FakeVintedAccount account = FakeVintedAccount.withMemberConnected();
        session.start(BrowserAction.LOGGING_IN);

        new CheckVintedSession(session, account, Runnable::run).check();

        assertThat(account.openedBrowsers()).isZero();
        assertThat(session.overview().currentAction()).contains(BrowserAction.LOGGING_IN);
    }

    @Test
    void reports_an_unreachable_vinted() {
        FakeVintedAccount account = FakeVintedAccount.withMemberConnected().whereVintedIsUnreachable();

        new CheckVintedSession(session, account, Runnable::run).check();

        assertThat(session.overview().problem()).contains(SessionProblem.VINTED_UNREACHABLE);
    }

    @Test
    void reports_an_unavailable_browser() {
        FakeVintedAccount account = FakeVintedAccount.withMemberConnected().whereBrowserCannotStart();

        new CheckVintedSession(session, account, Runnable::run).check();

        assertThat(session.overview().problem()).contains(SessionProblem.BROWSER_UNAVAILABLE);
    }

    @Test
    void is_shown_as_checking_as_soon_as_requested() {
        Executor notStartedYet = task -> { };

        new CheckVintedSession(session, FakeVintedAccount.withMemberConnected(), notStartedYet).check();

        assertThat(session.overview().currentAction()).contains(BrowserAction.CHECKING);
    }

    @Test
    void ends_on_an_unavailable_browser_after_an_unexpected_failure() {
        FakeVintedAccount account = FakeVintedAccount.withMemberConnected().whereBrowserFailsUnexpectedly();

        Throwable failure = catchThrowable(new CheckVintedSession(session, account, Runnable::run)::check);

        assertThat(failure).isInstanceOf(IllegalStateException.class);
        assertThat(session.overview().currentAction()).isEmpty();
        assertThat(session.overview().problem()).contains(SessionProblem.BROWSER_UNAVAILABLE);
    }
}
