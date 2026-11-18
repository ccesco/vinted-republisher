import { Component, DestroyRef, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Observable } from 'rxjs';
import { SessionOverview, VintedSessionService } from '../../generated/api';
import { answerOf } from '../shared/api-answer';

// Codes du contrat OpenAPI (SessionOverview) -> libellés affichés.
const STATUS_LABELS: Record<string, string> = { CONNECTED: 'connecté', DISCONNECTED: 'non connecté' };
const ACTION_LABELS: Record<string, string> = {
  CHECKING: 'vérification en cours', LOGGING_IN: 'connexion en cours', SCANNING: 'scan en cours',
  BACKING_UP: 'sauvegarde en cours'
};
const PROBLEM_MESSAGES: Record<string, string> = {
  MANUAL_LOGIN_NOT_COMPLETED: "La connexion n'a pas abouti : délai de 5 minutes dépassé, onglet ou Chrome fermé.",
  BROWSER_UNAVAILABLE: 'Le navigateur est indisponible (Chrome introuvable ou qui ne répond pas) : voir les logs.',
  VINTED_UNREACHABLE: 'Vinted est inaccessible (réseau ou blocage anti-robot) : réessayez plus tard.'
};
const APPLICATION_DOWN_MESSAGE = "L'application ne répond pas : nouvel essai dans 2 secondes.";
const POLL_INTERVAL_MS = 2000;

@Component({
  selector: 'app-vinted-session',
  imports: [MatButtonModule],
  templateUrl: './vinted-session.html'
})
export class VintedSession {
  protected readonly status = signal('');
  protected readonly problem = signal('');
  protected readonly actionsShown = signal(false);
  protected readonly logInShown = signal(true);
  private readonly sessions = inject(VintedSessionService);
  private timer?: ReturnType<typeof setTimeout>;
  private destroyed = false;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      // Une réponse arrivée après la sortie de l'écran ne relance plus rien.
      this.destroyed = true;
      clearTimeout(this.timer);
    });
    this.refresh();
  }

  protected logIn(): Promise<void> {
    return this.launch(this.sessions.logInToVinted());
  }

  protected check(): Promise<void> {
    return this.launch(this.sessions.checkVintedSession());
  }

  // Application arrêtée ou en erreur : on réessaie jusqu'à ce qu'elle réponde.
  private async refresh(): Promise<void> {
    try {
      this.follow(await answerOf(this.sessions.showVintedSession()));
    } catch {
      this.problem.set(APPLICATION_DOWN_MESSAGE);
      this.poll();
    }
  }

  private show(overview: SessionOverview): void {
    this.status.set(ACTION_LABELS[overview.currentAction ?? '']
      ?? STATUS_LABELS[overview.status ?? '']
      ?? 'non vérifié');
    this.problem.set(PROBLEM_MESSAGES[overview.problem ?? ''] ?? '');
    this.actionsShown.set(!overview.currentAction);
    this.logInShown.set(overview.status !== 'CONNECTED');
  }

  private follow(overview: SessionOverview): void {
    this.show(overview);
    if (overview.currentAction) {
      this.poll();
    }
  }

  // La réponse donne l'action lancée (ou celle déjà en cours) ; en cas d'échec, refresh affiche le problème.
  private async launch(action: Observable<SessionOverview>): Promise<void> {
    this.actionsShown.set(false);
    try {
      this.follow(await answerOf(action));
    } catch {
      await this.refresh();
    }
  }

  private poll(): void {
    if (this.destroyed) {
      return;
    }
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.refresh(), POLL_INTERVAL_MS);
  }
}
