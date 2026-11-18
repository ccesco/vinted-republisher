import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Observable } from 'rxjs';
import {
  AutomaticRunOverview, AutomaticRunService, DataArchiveService, Listing, ListingInventoryService,
  ListingRelaunchService, ListingTagService, RelaunchRefusal, RelaunchReport, TagOperation, WardrobeOverview,
  WardrobeService
} from '../../generated/api';
import { answerOf, refusalOf, statusOf } from '../shared/api-answer';
import { dateOf, dayOf, listingLink, plural, priceOf } from '../shared/format';
import { inventoryCriteriaOf } from './inventory-criteria';
import { keyFiguresOf } from './key-figures';
import { MessagePart, refusalMessageOf, relaunchMessageOf } from '../shared/relaunch';
import { runningSummaryOf } from './run-summary';
import {
  APPLICATION_DOWN_MESSAGE, ARCHIVE_PROBLEM_MESSAGES, ARCHIVE_UNREACHABLE_MESSAGE, BACKUP_PROBLEM_MESSAGES,
  PROBLEM_MESSAGES, RELAUNCHABLE_STAGES, RELAUNCH_FAILED_MESSAGE, RUN_STOP_MESSAGES, STAGES_TO_CHECK, STAGE_LABELS,
  STATISTICS_PROBLEM_MESSAGES, TAG_EDIT_FAILED_MESSAGE, TAG_EDIT_REFUSALS
} from './wardrobe-messages';

const POLL_INTERVAL_MS = 2000;
const SEARCH_DELAY_MS = 300;

interface Row {
  listing: Listing;
  message?: MessagePart[];
  relaunching: boolean;
}

@Component({
  selector: 'app-wardrobe',
  imports: [MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './wardrobe.html',
  styleUrl: './wardrobe.scss'
})
export class Wardrobe {
  protected readonly dateOf = dateOf;
  protected readonly priceOf = priceOf;
  protected readonly listingLink = listingLink;
  protected readonly stageLabels = STAGE_LABELS;
  protected readonly stagesToCheck = STAGES_TO_CHECK;
  protected readonly relaunchableStages = RELAUNCHABLE_STAGES;

  protected readonly overview = signal<WardrobeOverview | null>(null);
  protected readonly wardrobeProblem = signal('');
  protected readonly launchesShown = signal(false);
  protected readonly archiveProblem = signal('');
  protected readonly archiving = signal(false);

  private readonly run = signal<AutomaticRunOverview | null>(null);
  protected readonly runSummary = computed(() => {
    const run = this.run();
    return run ? [...runSummaryOf(run, (this.rows() ?? []).map(row => row.listing)), { text: requestOf(run) }] : [];
  });
  protected readonly runButtonShown = signal(false);
  protected readonly pauseShown = signal(false);
  protected readonly resumeShown = signal(false);
  protected readonly stopShown = signal(false);

  protected readonly presence = signal('PRESENT');
  protected readonly backup = signal('ALL');
  protected readonly search = signal('');
  protected readonly sort = signal('RECENCY');
  protected readonly direction = signal('DESC');
  protected readonly tag = signal('');
  protected readonly tags = signal<string[]>([]);

  protected readonly rows = signal<Row[] | null>(null);
  protected readonly listingsProblem = signal(false);
  protected readonly keyFigures = computed(() => keyFiguresOf((this.rows() ?? []).map(row => row.listing)));

  // Édition en masse des tags (US-34) : identifiants cochés.
  protected readonly selection = signal(new Set<number>());
  protected readonly tagName = signal('');
  protected readonly tagEditing = signal(false);
  protected readonly tagEditMessage = signal('');
  protected readonly allSelected = computed(() => {
    const ids = this.displayedIds();
    return ids.length > 0 && ids.every(id => this.selection().has(id));
  });
  protected readonly tagEditDisabled = computed(() =>
    this.tagEditing() || this.selection().size === 0 || !this.tagName().trim());

  private readonly wardrobe = inject(WardrobeService);
  private readonly inventory = inject(ListingInventoryService);
  private readonly listingTags = inject(ListingTagService);
  private readonly relaunches = inject(ListingRelaunchService);
  private readonly runs = inject(AutomaticRunService);
  private readonly archives = inject(DataArchiveService);
  private readonly timers = new Set<ReturnType<typeof setTimeout>>();
  private searchTimer?: ReturnType<typeof setTimeout>;
  // Seule la réponse à la dernière demande s'affiche : une réponse lente ne remplace pas une plus récente.
  private lastRequest = 0;
  private wardrobeBusy = false;
  private destroyed = false;
  private runInProgress = false;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      // Une réponse arrivée après la sortie de l'écran ne relance plus rien.
      this.destroyed = true;
      this.timers.forEach(clearTimeout);
      clearTimeout(this.searchTimer);
    });
    this.loadTags();
    this.refresh();
    this.refreshRun();
  }

  protected selectionCount(): string {
    const count = this.selection().size;
    return `${count} annonce${count > 1 ? 's' : ''} sélectionnée${count > 1 ? 's' : ''}`;
  }

  protected listingCount(): string {
    const count = this.rows()?.length;
    return count === undefined ? '' : `${count} annonce${count > 1 ? 's' : ''}`;
  }

  protected scanSummary(): string {
    const overview = this.overview();
    if (!overview) {
      return '';
    }
    return overview.lastScanAt ? `Dernier scan terminé le ${dateOf(overview.lastScanAt)}.`
      : "Aucun scan terminé pour l'instant.";
  }

  protected backupSummary(): string {
    const overview = this.overview();
    if (!overview) {
      return '';
    }
    return overview.lastBackupAt ? `Dernière sauvegarde terminée le ${dateOf(overview.lastBackupAt)}.`
      : "Aucune sauvegarde terminée pour l'instant.";
  }

  protected statisticsSummary(): string {
    const overview = this.overview();
    if (!overview) {
      return '';
    }
    return overview.lastStatisticsReadAt ? `Vues et favoris relevés le ${dateOf(overview.lastStatisticsReadAt)}.`
      : "Vues et favoris jamais relevés pour l'instant.";
  }

  protected backupProblem(): string {
    return BACKUP_PROBLEM_MESSAGES[this.overview()?.backupProblem ?? ''] ?? '';
  }

  protected statisticsProblem(): string {
    return STATISTICS_PROBLEM_MESSAGES[this.overview()?.statisticsRefreshProblem ?? ''] ?? '';
  }

  protected statisticsTitle(listing: Listing): string | null {
    return listing.statistics ? 'Relevé le ' + dateOf(listing.statistics.readAt) : null;
  }

  // US-35 : l'outil conseille, il n'agit pas.
  protected adviceTitle(listing: Listing): string | null {
    const advice = listing.republicationAdvice;
    return advice ? `+${advice.viewsGained} vues depuis le ${dayOf(advice.since)}` : null;
  }

  protected backupText(listing: Listing): string {
    if (!listing.savedAt) {
      return 'non sauvegardée';
    }
    const photos = listing.photoCount
      ? ` · ${listing.photoCount} ${listing.photoCount > 1 ? 'photos' : 'photo'}` : '';
    return 'sauvegardée le ' + dateOf(listing.savedAt) + photos;
  }

  // Critères de la liste : un choix relit la liste aussitôt, la recherche après une pause de frappe.
  protected choose(criterion: 'presence' | 'backup' | 'sort' | 'direction' | 'tag', value: string): void {
    this[criterion].set(value);
    clearTimeout(this.searchTimer);
    this.loadListings();
  }

  protected type(search: string): void {
    this.search.set(search);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.loadListings(), SEARCH_DELAY_MS);
  }

  protected toggle(listingId: number, checked: boolean): void {
    this.selection.update(selection => {
      const changed = new Set(selection);
      if (checked) {
        changed.add(listingId);
      } else {
        changed.delete(listingId);
      }
      return changed;
    });
  }

  protected toggleAll(checked: boolean): void {
    this.selection.update(selection => {
      const changed = new Set(selection);
      this.displayedIds().forEach(id => checked ? changed.add(id) : changed.delete(id));
      return changed;
    });
  }

  protected async editTags(operation: TagOperation): Promise<void> {
    const tag = this.tagName().trim();
    const listingIds = this.displayedIds().filter(id => this.selection().has(id));
    this.tagEditing.set(true);
    try {
      await answerOf(this.listingTags.editListingTags({ listingIds, tag, operation }));
      const done = operation === 'ADD' ? 'ajouté à' : 'retiré de';
      this.tagEditMessage.set(`Tag « ${tag} » ${done} ${plural(listingIds.length, 'annonce')}`);
      this.selection.set(new Set());
      await this.loadTags();
      this.loadListings();
    } catch (failure) {
      this.tagEditMessage.set(TAG_EDIT_REFUSALS[statusOf(failure)] ?? TAG_EDIT_FAILED_MESSAGE);
    } finally {
      this.tagEditing.set(false);
    }
  }

  protected scan(): Promise<void> {
    return this.launch(this.wardrobe.scanWardrobe());
  }

  protected backUp(): Promise<void> {
    return this.launch(this.wardrobe.backUpWardrobe());
  }

  protected refreshStatistics(): Promise<void> {
    return this.launch(this.wardrobe.refreshStatistics());
  }

  protected async launchRun(): Promise<void> {
    this.runButtonShown.set(false);
    try {
      this.followRun(await answerOf(this.runs.launchAutomaticRun()));
    } catch {
      await this.refreshRun();
    }
  }

  // Une demande de pause ou d'arrêt attend la fin de l'action en cours (US-21).
  protected async controlRun(control: 'pause' | 'resume' | 'stop'): Promise<void> {
    const requests: Record<string, () => Observable<AutomaticRunOverview>> = {
      pause: () => this.runs.pauseAutomaticRun(),
      resume: () => this.runs.resumeAutomaticRun(),
      stop: () => this.runs.stopAutomaticRun()
    };
    try {
      this.showRun(await answerOf(requests[control]()));
    } catch {
      await this.refreshRun();
    }
  }

  // Requête plutôt qu'un simple lien : un refus s'affiche sur la page au lieu d'être téléchargé comme fichier.
  protected async downloadArchive(): Promise<void> {
    this.archiving.set(true);
    try {
      const response: HttpResponse<Blob> = await answerOf(this.archives.downloadDataArchive('response'));
      const disposition = response.headers.get('Content-Disposition') ?? '';
      const name = /filename="([^"]+)"/.exec(disposition)?.[1] ?? 'vinted-republisher-archive.zip';
      const link = document.createElement('a');
      link.href = URL.createObjectURL(response.body ?? new Blob());
      link.download = name;
      link.click();
      setTimeout(() => URL.revokeObjectURL(link.href), 0);
      this.archiveProblem.set('');
    } catch (failure) {
      if (statusOf(failure) === 0) {
        this.archiveProblem.set(ARCHIVE_UNREACHABLE_MESSAGE);
      } else {
        const refusal = await refusalOf<{ reason?: string }>(failure);
        this.archiveProblem.set(ARCHIVE_PROBLEM_MESSAGES[refusal?.reason ?? ''] ?? ARCHIVE_PROBLEM_MESSAGES['ARCHIVE_FAILED']);
      }
    } finally {
      this.archiving.set(false);
    }
  }

  // La ligne relancée est remplacée : nouvelle étape quand l'action a eu lieu, sinon même ligne et raison.
  protected async relaunch(row: Row): Promise<void> {
    const listing = row.listing;
    this.replaceRow(row, { ...row, relaunching: true });
    let shown = listing;
    let message: MessagePart[];
    try {
      const report: RelaunchReport | null = await answerOf(
        this.relaunches.relaunchListing(listing.vintedId, { stage: listing.stage }));
      if (report?.listing) {
        shown = report.listing;
        message = [{ text: relaunchMessageOf(report) }];
      } else {
        message = [{ text: RELAUNCH_FAILED_MESSAGE }];
      }
    } catch (failure) {
      const refusal = await refusalOf<RelaunchRefusal>(failure);
      if (statusOf(failure) === 0 || !refusal) {
        message = [{ text: RELAUNCH_FAILED_MESSAGE }];
      } else {
        // Étape changée : la ligne prend l'étape actuelle, d'où repartira le prochain clic.
        if (refusal.reason === 'STAGE_CHANGED' && refusal.stage) {
          shown = { ...listing, stage: refusal.stage };
        }
        message = refusalMessageOf(listing.vintedId, refusal);
      }
    }
    this.rows.update(rows => (rows ?? []).map(other => other.listing.vintedId === listing.vintedId
      ? { listing: shown, message, relaunching: false } : other));
  }

  private displayedIds(): number[] {
    return (this.rows() ?? []).map(row => row.listing.vintedId);
  }

  private replaceRow(row: Row, replacement: Row): void {
    this.rows.update(rows => (rows ?? []).map(other => other === row ? replacement : other));
  }

  // Application arrêtée ou en erreur : on réessaie jusqu'à ce qu'elle réponde.
  private async refresh(): Promise<void> {
    try {
      this.follow(await answerOf(this.wardrobe.showWardrobe()));
    } catch {
      this.wardrobeProblem.set(APPLICATION_DOWN_MESSAGE);
      this.later(() => this.refresh());
    }
  }

  private show(overview: WardrobeOverview): void {
    this.overview.set(overview);
    // Une seule action navigateur à la fois : aucune autre n'est proposée pendant un scan, une sauvegarde ou un
    // rafraîchissement des vues et favoris.
    const busy = inProgress(overview);
    this.launchesShown.set(!busy);
    this.wardrobeBusy = busy;
    this.showRunButton();
    this.wardrobeProblem.set(PROBLEM_MESSAGES[overview.problem ?? ''] ?? '');
  }

  // La liste est relue à chaque aperçu : pendant un scan elle se remplit, à sa fin elle est à jour.
  private follow(overview: WardrobeOverview): void {
    this.show(overview);
    this.loadListings();
    if (inProgress(overview)) {
      this.later(() => this.refresh());
    }
  }

  // La réponse donne l'action lancée ; en cas d'échec, refresh affiche le problème.
  private async launch(action: Observable<WardrobeOverview>): Promise<void> {
    this.launchesShown.set(false);
    try {
      this.follow(await answerOf(action));
    } catch {
      await this.refresh();
    }
  }

  private async loadListings(): Promise<void> {
    const request = ++this.lastRequest;
    const criteria = inventoryCriteriaOf({
      presence: this.presence(), backup: this.backup(), search: this.search(), sort: this.sort(),
      direction: this.direction(), tag: this.tag()
    });
    try {
      const inventory = await answerOf(this.inventory.showListingInventory(criteria.presence, criteria.backup,
        criteria.search, criteria.sort, criteria.direction, criteria.tag));
      if (request === this.lastRequest) {
        this.showListings(inventory.listings);
      }
    } catch {
      if (request === this.lastRequest) {
        this.listingsProblem.set(true);
      }
    }
  }

  private showListings(listings: Listing[]): void {
    this.listingsProblem.set(false);
    // La sélection ne garde que des lignes affichées (US-34).
    const shown = new Set(listings.map(listing => listing.vintedId));
    this.selection.update(selection => new Set([...selection].filter(id => shown.has(id))));
    this.rows.set(listings.map(listing => ({ listing, relaunching: false })));
  }

  // Tags proposés au filtre ; le tag choisi reste choisi s'il est toujours porté.
  private async loadTags(): Promise<void> {
    try {
      const tags = (await answerOf(this.listingTags.showListingTags())).tags;
      this.tags.set(tags);
      if (!tags.includes(this.tag())) {
        this.tag.set('');
      }
    } catch {
      // Filtre réduit à « Tous » : la liste reste utilisable.
    }
  }

  // Série automatique (US-19) : proposée seulement sans scan, sauvegarde ni série en cours.
  private showRunButton(): void {
    this.runButtonShown.set(!this.wardrobeBusy && !this.runInProgress);
  }

  private showRun(run: AutomaticRunOverview): void {
    this.runInProgress = run.status === 'RUNNING' || run.status === 'WAITING' || run.status === 'PAUSED';
    this.run.set(run);
    this.showRunButton();
    const active = run.status === 'RUNNING' || run.status === 'WAITING';
    this.pauseShown.set(active && !run.pauseRequested && !run.stopRequested);
    this.resumeShown.set((run.status === 'PAUSED' || Boolean(run.pauseRequested)) && !run.stopRequested);
    this.stopShown.set(this.runInProgress && !run.stopRequested);
  }

  // La liste est relue pendant la série, et une dernière fois quand elle s'achève.
  private followRun(run: AutomaticRunOverview): void {
    const wasInProgress = this.runInProgress;
    this.showRun(run);
    if (wasInProgress || this.runInProgress) {
      this.loadListings();
    }
    if (this.runInProgress) {
      this.later(() => this.refreshRun());
    }
  }

  private async refreshRun(): Promise<void> {
    try {
      this.followRun(await answerOf(this.runs.showAutomaticRun()));
    } catch {
      this.later(() => this.refreshRun());
    }
  }

  private later(action: () => void): void {
    if (this.destroyed) {
      return;
    }
    const timer = setTimeout(() => {
      this.timers.delete(timer);
      action();
    }, POLL_INTERVAL_MS);
    this.timers.add(timer);
  }
}

function inProgress(overview: WardrobeOverview): boolean {
  return overview.scanInProgress || overview.backupInProgress || overview.statisticsRefreshInProgress;
}

// Morceau de la ligne d'état de la série : texte, ou lien vers la page des échecs (US-39).
interface SummaryPart {
  text: string;
  failuresLink?: boolean;
}

function runSummaryOf(run: AutomaticRunOverview, listings: Listing[]): SummaryPart[] {
  if (run.status === 'FINISHED') {
    return finishedSummaryOf(run);
  }
  return [{ text: runStateOf(run, listings) }];
}

function finishedSummaryOf(run: AutomaticRunOverview): SummaryPart[] {
  const skipped = run.skippedListingIds.length;
  const skippedText = `${plural(skipped, 'annonce')} écartée${skipped > 1 ? 's' : ''}`;
  return [
    { text: `Traitement terminé : ${plural(run.relaunchCount, 'relance')}, ` },
    { text: skippedText, failuresLink: skipped > 0 },
    { text: '.' }
  ];
}

function runStateOf(run: AutomaticRunOverview, listings: Listing[]): string {
  switch (run.status) {
    case 'RUNNING':
      return runningSummaryOf(run.currentListingIds, listings);
    case 'WAITING':
      return `En attente jusqu'au ${dateOf(run.waitingUntil ?? '')}.`;
    case 'PAUSED':
      return 'Traitement en pause.';
    case 'STOPPED':
      return run.stopReason === 'STOPPED_BY_MEMBER' ? 'Traitement arrêté à votre demande.'
        : `Traitement arrêté : ${RUN_STOP_MESSAGES[run.stopReason ?? ''] ?? RUN_STOP_MESSAGES['RUN_FAILED']}.`;
    default:
      return '';
  }
}

function requestOf(run: AutomaticRunOverview): string {
  if (run.stopRequested) {
    return " (arrêt demandé, à la fin de l'action en cours)";
  }
  return run.pauseRequested ? " (pause demandée, à la fin de l'action en cours)" : '';
}
