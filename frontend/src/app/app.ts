import { Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { filter } from 'rxjs';
import { SessionOverview, VintedSessionService } from '../generated/api';
import { ThemeService } from './shared/theme.service';

const SESSION_LABELS: Record<string, string> = {
  CONNECTED: 'Session Vinted connectée',
  DISCONNECTED: 'Session Vinted non connectée'
};

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatButtonModule],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly theme = inject(ThemeService);
  protected readonly session = signal<SessionOverview | null>(null);
  private readonly sessions = inject(VintedSessionService);

  constructor() {
    // Relue à chaque écran : la connexion se fait depuis l'écran de la session.
    inject(Router).events.pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => this.readSession());
  }

  protected connected(): boolean {
    return this.session()?.status === 'CONNECTED';
  }

  protected sessionLabel(): string {
    return SESSION_LABELS[this.session()?.status ?? ''] ?? 'Session Vinted non vérifiée';
  }

  private readSession(): void {
    this.sessions.showVintedSession().subscribe({
      next: overview => this.session.set(overview),
      error: () => this.session.set(null)
    });
  }
}
