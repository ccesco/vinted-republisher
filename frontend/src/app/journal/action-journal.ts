import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ActionJournalService } from '../../generated/api';
import { answerOf } from '../shared/api-answer';
import { listingLink } from '../shared/format';
import { JournalLine, journalLineOf } from '../shared/journal';

@Component({
  selector: 'app-action-journal',
  imports: [MatButtonModule],
  templateUrl: './action-journal.html'
})
export class ActionJournal {
  protected readonly lines = signal<JournalLine[]>([]);
  protected readonly hasMore = signal(false);
  protected readonly loading = signal(false);
  protected readonly unavailable = signal(false);
  protected readonly listingLink = listingLink;
  private readonly journal = inject(ActionJournalService);
  private lastEntryId?: number;

  constructor() {
    this.loadPage();
  }

  // Les lignes les plus récentes d'abord ; « Plus ancien » ajoute les 50 suivantes.
  protected async loadPage(): Promise<void> {
    this.loading.set(true);
    try {
      const page = await answerOf(this.journal.showActionJournal(this.lastEntryId));
      this.lines.update(lines => [...lines, ...page.entries.map(journalLineOf)]);
      if (page.entries.length > 0) {
        this.lastEntryId = page.entries[page.entries.length - 1].entryId;
      }
      this.hasMore.set(page.hasMore);
      this.unavailable.set(false);
    } catch {
      this.unavailable.set(true);
    } finally {
      this.loading.set(false);
    }
  }
}
