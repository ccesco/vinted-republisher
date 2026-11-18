import { Component, DestroyRef, inject, signal } from '@angular/core';
import { LivePreview as Preview, LivePreviewService } from '../../generated/api';
import { answerOf } from '../shared/api-answer';

const POLL_INTERVAL_MS = 2000;

// US-22 : ce que montre chaque navigateur piloté, recapturé toutes les 2 secondes tant que l'écran est ouvert.
@Component({
  selector: 'app-live-preview',
  templateUrl: './live-preview.html',
  styleUrl: './live-preview.scss'
})
export class LivePreview {
  protected readonly previews = signal<Preview[] | null>(null);
  protected readonly unavailable = signal(false);
  private readonly livePreviews = inject(LivePreviewService);
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

  protected capturedAt(preview: Preview): string {
    return 'Capturé à ' + new Date(preview.capturedAt ?? '').toLocaleTimeString('fr-FR');
  }

  private async refresh(): Promise<void> {
    try {
      this.previews.set((await answerOf(this.livePreviews.showLivePreviews())).previews);
      this.unavailable.set(false);
    } catch {
      this.previews.set(null);
      this.unavailable.set(true);
    }
    if (!this.destroyed) {
      this.timer = setTimeout(() => this.refresh(), POLL_INTERVAL_MS);
    }
  }
}
