import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  FailedListing, FailedListingsService, ListingBackupService, ListingRelaunchService, ListingRepublicationService,
  RelaunchRefusal, RelaunchReport
} from '../../generated/api';
import { REPUBLICATION_REFUSALS } from '../listing/listing-messages';
import { answerOf, refusalOf, statusOf } from '../shared/api-answer';
import { dateOf, listingLink } from '../shared/format';
import { actionOf, causeOf, outcomeOf } from '../shared/journal';
import { manualRepublicationMessageOf } from '../shared/manual-republication';
import { MessagePart, refusalMessageOf, relaunchMessageOf } from '../shared/relaunch';
import { RELAUNCH_LABELS, RELAUNCH_FAILED_MESSAGE, STAGE_LABELS } from '../wardrobe/wardrobe-messages';
import { FailureGroup, failureGroupsOf } from './failure-groups';

const MANUAL_REPUBLICATION_FAILED = 'Échec de la republication manuelle : voir la fiche';

// Annonces en échec (US-39) : la dernière action de chacune, sa cause, et la relance comme dans la liste (US-20),
// groupées selon ce que le membre doit faire, avec la republication manuelle d'une annonce refusée (US-45).
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
  private readonly backups = inject(ListingBackupService);
  private readonly republications = inject(ListingRepublicationService);

  constructor() {
    this.load();
  }

  protected groups(): FailureGroup[] {
    return failureGroupsOf(this.listings() ?? []);
  }

  // Supprimée, pas encore republiée : la republication manuelle n'attend pas la date de republication.
  protected manuallyRepublishable(failed: FailedListing): boolean {
    return failed.kind === 'REFUSED' && (failed.stage === 'WAITING' || failed.stage === 'TO_REPUBLISH');
  }

  // Le formulaire est préparé pour la sauvegarde actuelle, lue juste avant ; l'annonce passe ensuite « Dans le doute ».
  protected async republishManually(failed: FailedListing): Promise<void> {
    this.relaunching.set(failed.listingId);
    this.message.set([{ text: `${failed.listingTitle} : ${await this.manualOutcomeOf(failed)}` }]);
    this.relaunching.set(null);
    await this.load();
  }

  private async manualOutcomeOf(failed: FailedListing): Promise<string> {
    try {
      const backup = await answerOf(this.backups.showListingBackup(failed.listingId));
      const report = await answerOf(this.republications.prepareManualRepublication(failed.listingId,
        { savedAt: backup.savedAt }));
      return manualRepublicationMessageOf(report.unfilledFields, 'sur la fiche');
    } catch (failure) {
      const reason = statusOf(failure) === 0 ? undefined : (await refusalOf<{ reason: string }>(failure))?.reason;
      const refusal = REPUBLICATION_REFUSALS[reason ?? ''];
      return refusal ? 'Republication manuelle refusée : ' + refusal : MANUAL_REPUBLICATION_FAILED;
    }
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
