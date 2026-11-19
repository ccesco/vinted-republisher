import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  FailedListing, FailedListingsService, ListingRelaunchService, RelaunchRefusal, RelaunchReport
} from '../../generated/api';
import { answerOf, refusalOf, statusOf } from '../shared/api-answer';
import { dateOf, listingLink } from '../shared/format';
import { actionOf, causeOf, outcomeOf } from '../shared/journal';
import { MessagePart, refusalMessageOf, relaunchMessageOf } from '../shared/relaunch';
import { RELAUNCH_LABELS, RELAUNCH_FAILED_MESSAGE, STAGE_LABELS } from '../wardrobe/wardrobe-messages';

// Annonces en échec (US-39) : la dernière action de chacune, sa cause, et la relance comme dans la liste (US-20).
@Component({
  selector: 'app-failed-listings',
  imports: [MatButtonModule],
  templateUrl: './failed-listings.html'
})
export class FailedListings {
  protected readonly listings = signal<FailedListing[] | null>(null);
  protected readonly unavailable = signal(false);
  protected readonly relaunching = signal<number | null>(null);
  protected readonly message = signal<MessagePart[]>([]);
  protected readonly stageLabels = STAGE_LABELS;
  protected readonly relaunchLabels = RELAUNCH_LABELS;
  protected readonly listingLink = listingLink;
  protected readonly dateOf = dateOf;
  protected readonly actionOf = actionOf;
  protected readonly outcomeOf = outcomeOf;
  protected readonly causeOf = causeOf;
  private readonly failedListings = inject(FailedListingsService);
  private readonly relaunches = inject(ListingRelaunchService);

  constructor() {
    this.load();
  }

  // L'annonce relancée avec succès quitte la liste relue ; le message reste affiché au-dessus.
  protected async relaunch(failed: FailedListing): Promise<void> {
    this.relaunching.set(failed.listingId);
    const outcome = await this.relaunchOutcomeOf(failed);
    this.message.set([{ text: `${failed.listingTitle} : ` }, ...outcome]);
    this.relaunching.set(null);
    await this.load();
  }

  private async relaunchOutcomeOf(failed: FailedListing): Promise<MessagePart[]> {
    try {
      const report: RelaunchReport | null = await answerOf(
        this.relaunches.relaunchListing(failed.listingId, { stage: failed.stage }));
      return [{ text: report?.listing ? relaunchMessageOf(report) : RELAUNCH_FAILED_MESSAGE }];
    } catch (failure) {
      const refusal = await refusalOf<RelaunchRefusal>(failure);
      return statusOf(failure) === 0 || !refusal
        ? [{ text: RELAUNCH_FAILED_MESSAGE }] : refusalMessageOf(failed.listingId, refusal);
    }
  }

  private async load(): Promise<void> {
    try {
      this.listings.set((await answerOf(this.failedListings.showFailedListings())).listings);
      this.unavailable.set(false);
    } catch {
      this.unavailable.set(true);
    }
  }
}
