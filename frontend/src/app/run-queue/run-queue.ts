import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { AutomaticRunService, RunQueue as Queue } from '../../generated/api';
import { answerOf } from '../shared/api-answer';
import { listingLink } from '../shared/format';
import { queuedActionOf, whenOf } from './run-queue-labels';

// File d'attente de la série (US-41) : ce que « Tout lancer » ferait, dans l'ordre, au plus tôt.
@Component({
  selector: 'app-run-queue',
  imports: [MatButtonModule],
  templateUrl: './run-queue.html'
})
export class RunQueue {
  protected readonly queue = signal<Queue | null>(null);
  protected readonly unavailable = signal(false);
  protected readonly listingLink = listingLink;
  protected readonly queuedActionOf = queuedActionOf;
  protected readonly whenOf = whenOf;
  private readonly automaticRun = inject(AutomaticRunService);

  constructor() {
    this.load();
  }

  protected async load(): Promise<void> {
    try {
      this.queue.set(await answerOf(this.automaticRun.showRunQueue()));
      this.unavailable.set(false);
    } catch {
      this.unavailable.set(true);
    }
  }
}
