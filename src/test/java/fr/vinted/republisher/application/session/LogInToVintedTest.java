package fr.vinted.republisher.application.session;

import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.SessionOverview;
import fr.vinted.republisher.domain.session.SessionProblem;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.fakes.FakeVintedAccount;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Executor;

import static fr.vinted.republisher.domain.session.SessionStatus.CONNECTED;
import static org.assertj.core.api.Assertions.assertThat;

class LogInToVintedTest {

    private final VintedSession session = new VintedSession();

    @Test
    void connects_the_session_once_the_member_has_logged_in() {
        new LogInToVinted(session, FakeVintedAccount.withMemberDisconnected(), Runnable::run).logIn();

        assertThat(session.overview()).isEqualTo(SessionOverview.idle(CONNECTED));
    }

    @Test
    void gives_the_member_five_minutes_to_log_in() {
        FakeVintedAccount account = FakeVintedAccount.withMemberDisconnected();

        new LogInToVinted(session, account, Runnable::run).logIn();

        assertThat(account.manualLoginTimeout()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void is_shown_as_logging_in_while_the_member_logs_in() {
        FakeVintedAccount account = FakeVintedAccount.withMemberDisconnected();
        account.duringManualLogin(() ->
                assertThat(session.overview().currentAction()).contains(BrowserAction.LOGGING_IN));

        new LogInToVinted(session, account, Runnable::run).logIn();
    }

    @Test
    void is_shown_as_logging_in_as_soon_as_requested() {
        Executor notStartedYet = task -> { };

        new LogInToVinted(session, FakeVintedAccount.withMemberDisconnected(), notStartedYet).logIn();

        assertThat(session.overview().currentAction()).contains(BrowserAction.LOGGING_IN);
    }

    @Test
    void is_ignored_while_another_browser_action_is_in_progress() {
        FakeVintedAccount account = FakeVintedAccount.withMemberDisconnected();
        session.start(BrowserAction.CHECKING);

        new LogInToVinted(session, account, Runnable::run).logIn();

        assertThat(account.openedBrowsers()).isZero();
    }

    @Test
    void reports_an_unreachable_vinted() {
        FakeVintedAccount account = FakeVintedAccount.withMemberDisconnected().whereVintedIsUnreachable();

        new LogInToVinted(session, account, Runnable::run).logIn();

        assertThat(session.overview().problem()).contains(SessionProblem.VINTED_UNREACHABLE);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void reports_an_unavailable_browser() {
        FakeVintedAccount account = FakeVintedAccount.withMemberDisconnected().whereBrowserCannotStart();

        new LogInToVinted(session, account, Runnable::run).logIn();

        assertThat(session.overview().problem()).contains(SessionProblem.BROWSER_UNAVAILABLE);
        assertThat(session.overview().currentAction()).isEmpty();
    }
}
