import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { DuplicateDeletionRefusal, DuplicateGroup, DuplicateGroups, DuplicatesService } from '../../generated/api';
import { answerOf, refusalOf, statusOf } from '../shared/api-answer';
import {
  duplicateConfirmationOf, duplicateOutcomeMessageOf, duplicateRefusalMessageOf
} from '../shared/duplicates';
import { dateOf, listingLink, priceOf } from '../shared/format';

interface DeletionAsked {
  listingId: number;
  twinIds: number[];
}

// Doublons du dressing (US-47) : annonces présentes de même titre et même prix, et suppression d'un doublon.
@Component({
  selector: 'app-duplicates',
  imports: [MatButtonModule],
  templateUrl: './duplicates.html'
})
export class Duplicates {
  protected readonly duplicates = signal<DuplicateGroups | null>(null);
  protected readonly unavailable = signal(false);
  protected readonly message = signal('');
  protected readonly asked = signal<DeletionAsked | null>(null);
  protected readonly sending = signal(false);
  protected readonly dateOf = dateOf;
  protected readonly priceOf = priceOf;
  protected readonly listingLink = listingLink;
  private readonly service = inject(DuplicatesService);

  constructor() {
    this.load();
  }

  protected summary(): string {
    const count = this.duplicates()?.groups.length ?? 0;
    return count === 0 ? 'Aucun doublon.' : count + (count === 1 ? ' groupe' : ' groupes') + ' de doublons.';
  }

  protected ask(group: DuplicateGroup, listingId: number): void {
    this.asked.set({
      listingId,
      twinIds: group.listings.map(listing => listing.listingId).filter(id => id !== listingId)
    });
  }

  protected confirmation(): string {
    const asked = this.asked();
    return asked ? duplicateConfirmationOf(asked.listingId, asked.twinIds, this.duplicates()?.deletionDryRun ?? true) : '';
  }

  protected async confirm(): Promise<void> {
    const asked = this.asked();
    if (!asked || this.sending()) {
      return;
    }
    this.asked.set(null);
    this.sending.set(true);
    try {
      this.message.set(duplicateOutcomeMessageOf(asked.listingId,
        await answerOf(this.service.deleteDuplicate(asked.listingId))));
    } catch (failure) {
      this.message.set(duplicateRefusalMessageOf(await refusalOf<DuplicateDeletionRefusal>(failure),
        statusOf(failure)));
    } finally {
      this.sending.set(false);
    }
    await this.load();
  }

  private async load(): Promise<void> {
    try {
      this.duplicates.set(await answerOf(this.service.showDuplicates()));
      this.unavailable.set(false);
    } catch {
      this.unavailable.set(true);
    }
  }
}
