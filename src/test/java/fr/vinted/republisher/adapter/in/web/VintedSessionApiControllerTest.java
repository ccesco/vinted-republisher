package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.session.CheckVintedSession;
import fr.vinted.republisher.application.session.LogInToVinted;
import fr.vinted.republisher.application.session.ShowVintedSession;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.SessionProblem;
import fr.vinted.republisher.domain.session.SessionStatus;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.fakes.FakeVintedAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/** AT-03-13: the API answers as described by the OpenAPI contract. */
// Each test starts from a fresh, unchecked Vinted session.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(VintedSessionApiController.class)
class VintedSessionApiControllerTest {

    @Autowired
    private Background background;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private VintedSession session;

    @Autowired
    private FakeVintedAccount account;

    @Test
    void unchecked_session_has_no_status_no_action_and_no_problem() {
        assertThat(mvc.get().uri("/api/vinted-session"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("{}");
    }

    @Test
    void shows_the_status_found_by_the_last_check() {
        session.start(BrowserAction.CHECKING);
        session.complete(SessionStatus.CONNECTED);

        assertThat(mvc.get().uri("/api/vinted-session"))
                .bodyJson().isStrictlyEqualTo("{\"status\": \"CONNECTED\"}");
    }

    @Test
    void shows_the_browser_action_in_progress() {
        session.start(BrowserAction.LOGGING_IN);

        assertThat(mvc.get().uri("/api/vinted-session"))
                .bodyJson().isStrictlyEqualTo("{\"currentAction\": \"LOGGING_IN\"}");
    }

    @Test
    void shows_the_problem_of_the_last_browser_action() {
        session.start(BrowserAction.CHECKING);
        session.fail(SessionProblem.VINTED_UNREACHABLE);

        assertThat(mvc.get().uri("/api/vinted-session"))
                .bodyJson().isStrictlyEqualTo("{\"status\": \"DISCONNECTED\", \"problem\": \"VINTED_UNREACHABLE\"}");
    }

    @Test
    void check_answers_with_the_check_in_progress_and_runs_it_in_the_background() {
        assertThat(mvc.post().uri("/api/vinted-session/check"))
                .hasStatus(202)
                .bodyJson().isStrictlyEqualTo("{\"currentAction\": \"CHECKING\"}");
        assertThat(account.openedBrowsers()).isZero();

        background.runPendingTasks();

        assertThat(account.openedBrowsers()).isEqualTo(1);
        assertThat(session.overview().status()).contains(SessionStatus.DISCONNECTED);
    }

    @Test
    void login_answers_with_the_login_in_progress_and_runs_it_in_the_background() {
        assertThat(mvc.post().uri("/api/vinted-session/login"))
                .hasStatus(202)
                .bodyJson().isStrictlyEqualTo("{\"currentAction\": \"LOGGING_IN\"}");

        background.runPendingTasks();

        assertThat(session.overview().status()).contains(SessionStatus.CONNECTED);
    }

    @Test
    void ignored_request_answers_with_the_action_already_in_progress() {
        session.start(BrowserAction.LOGGING_IN);

        assertThat(mvc.post().uri("/api/vinted-session/check"))
                .hasStatus(202)
                .bodyJson().isStrictlyEqualTo("{\"currentAction\": \"LOGGING_IN\"}");
    }

    /** Holds browser actions until the test runs them, as a background thread would later. */
    static final class Background implements Executor {

        private final List<Runnable> pendingTasks = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            pendingTasks.add(task);
        }

        void runPendingTasks() {
            pendingTasks.forEach(Runnable::run);
            pendingTasks.clear();
        }
    }

    @TestConfiguration
    static class SessionUseCases {

        @Bean
        VintedSession session() {
            return new VintedSession();
        }

        @Bean
        FakeVintedAccount account() {
            return FakeVintedAccount.withMemberDisconnected();
        }

        @Bean
        ShowVintedSession showVintedSession(VintedSession session) {
            return new ShowVintedSession(session);
        }

        @Bean
        Background background() {
            return new Background();
        }

        @Bean
        CheckVintedSession checkVintedSession(VintedSession session, FakeVintedAccount account, Background background) {
            return new CheckVintedSession(session, account, background);
        }

        @Bean
        LogInToVinted logInToVinted(VintedSession session, FakeVintedAccount account, Background background) {
            return new LogInToVinted(session, account, background);
        }
    }
}
