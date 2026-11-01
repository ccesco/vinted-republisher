package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.application.session.CheckVintedSession;
import fr.vinted.republisher.application.session.LogInToVinted;
import fr.vinted.republisher.application.session.ShowVintedSession;
import fr.vinted.republisher.domain.session.SessionOverview;
import fr.vinted.republisher.domain.session.SessionProblem;
import fr.vinted.republisher.domain.session.SessionStatus;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.fakes.FakeVintedAccount;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VintedSessionAcceptanceTest {

    private final VintedSession session = new VintedSession();

    @Test
    void session_is_unchecked_until_asked() {
        // Étant donné l'application qui vient de démarrer
        FakeVintedAccount account = FakeVintedAccount.withMemberConnected();
        new CheckVintedSession(session, account, Runnable::run);
        new LogInToVinted(session, account, Runnable::run);

        // Quand j'ouvre la page de la session Vinted
        SessionOverview overview = overview();

        // Alors je vois "non vérifié"
        // Et aucun Chrome n'a été démarré
        assertThat(overview.status()).isEmpty();
        assertThat(account.openedBrowsers()).isZero();
    }

    @Test
    void open_session_is_reported_as_connected() {
        // Étant donné une session Vinted conservée dans laquelle le membre est connecté
        FakeVintedAccount account = FakeVintedAccount.withMemberConnected();

        // Quand je vérifie l'état de la session
        new CheckVintedSession(session, account, Runnable::run).check();

        // Alors l'état est "connecté"
        assertThat(overview().status()).contains(SessionStatus.CONNECTED);
    }

    @Test
    void closed_session_is_reported_as_disconnected() {
        // Étant donné aucune session Vinted conservée, ou une session expirée
        FakeVintedAccount account = FakeVintedAccount.withMemberDisconnected();

        // Quand je vérifie l'état de la session
        new CheckVintedSession(session, account, Runnable::run).check();

        // Alors l'état est "non connecté"
        assertThat(overview().status()).contains(SessionStatus.DISCONNECTED);
    }

    @Test
    void manual_login_opens_the_session() {
        // Étant donné une session fermée
        FakeVintedAccount account = FakeVintedAccount.withMemberDisconnected();
        new CheckVintedSession(session, account, Runnable::run).check();

        // Quand je lance la connexion
        // Et que je me connecte moi-même dans le navigateur ouvert
        new LogInToVinted(session, account, Runnable::run).logIn();

        // Alors la session est ouverte
        // Et l'état est "connecté"
        assertThat(overview().status()).contains(SessionStatus.CONNECTED);
        assertThat(overview().problem()).isEmpty();
    }

    @Test
    void abandoned_login_leaves_the_session_closed() {
        // Étant donné une session fermée
        FakeVintedAccount account = FakeVintedAccount.withMemberDisconnected().whereMemberAbandonsManualLogin();
        new CheckVintedSession(session, account, Runnable::run).check();

        // Quand je lance la connexion
        // Et que je ne me connecte pas avant la fin du délai, ou que je ferme le navigateur
        new LogInToVinted(session, account, Runnable::run).logIn();

        // Alors l'état reste "non connecté"
        // Et un message indique que la connexion n'a pas abouti
        assertThat(overview().status()).contains(SessionStatus.DISCONNECTED);
        assertThat(overview().problem()).contains(SessionProblem.MANUAL_LOGIN_NOT_COMPLETED);
    }

    @Test
    void request_during_a_browser_action_is_ignored() {
        // Étant donné une connexion manuelle en cours
        FakeVintedAccount account = FakeVintedAccount.withMemberDisconnected();
        CheckVintedSession checkVintedSession = new CheckVintedSession(session, account, Runnable::run);
        LogInToVinted logInToVinted = new LogInToVinted(session, account, Runnable::run);

        // Quand je demande une vérification ou une nouvelle connexion
        account.duringManualLogin(() -> {
            checkVintedSession.check();
            logInToVinted.logIn();
        });
        logInToVinted.logIn();

        // Alors la demande est ignorée
        // Et aucun second navigateur n'est ouvert
        assertThat(account.openedBrowsers()).isEqualTo(1);
    }

    @Test
    void unavailable_browser_leaves_the_session_disconnected() {
        // Étant donné un navigateur qui ne peut pas démarrer (Chrome absent ou profil verrouillé)
        FakeVintedAccount account = FakeVintedAccount.withMemberConnected().whereBrowserCannotStart();

        // Quand je vérifie l'état de la session
        new CheckVintedSession(session, account, Runnable::run).check();

        // Alors l'état est "non connecté"
        // Et un message indique que le navigateur est indisponible
        assertThat(overview().status()).contains(SessionStatus.DISCONNECTED);
        assertThat(overview().problem()).contains(SessionProblem.BROWSER_UNAVAILABLE);
    }

    @Test
    void unreachable_vinted_leaves_the_session_disconnected() {
        // Étant donné Vinted qui ne répond pas, ou qui répond par une erreur HTTP (page de blocage anti-robot)
        FakeVintedAccount account = FakeVintedAccount.withMemberConnected().whereVintedIsUnreachable();

        // Quand je vérifie l'état de la session
        new CheckVintedSession(session, account, Runnable::run).check();

        // Alors l'état est "non connecté"
        // Et un message indique que Vinted est inaccessible
        assertThat(overview().status()).contains(SessionStatus.DISCONNECTED);
        assertThat(overview().problem()).contains(SessionProblem.VINTED_UNREACHABLE);
    }

    private SessionOverview overview() {
        return new ShowVintedSession(session).show();
    }
}
