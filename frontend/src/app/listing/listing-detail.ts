import { Component, DestroyRef, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Observable, map } from 'rxjs';
import {
  BackupVerification, DeletionRefusal, LifecycleEvent, ListingBackupContent, ListingBackupService,
  ListingBackupView, ListingDeletionService, ListingDetail as ListingDetailView, ListingDetailService,
  ListingDifference, ListingFieldDifference, ListingRepublicationService, ListingStatistics, RepublicationAdvice,
  RepublicationRefusal, StatisticsComparison, ValidationRefusal, VerificationFailure
} from '../../generated/api';
import { answerOf, refusalOf, statusOf } from '../shared/api-answer';
import { dateOf, dayOf, listingLink, priceOf } from '../shared/format';
import { JournalLine, journalLineOf } from '../shared/journal';
import { manualRepublicationMessageOf } from '../shared/manual-republication';
import { STAGE_LABELS } from '../wardrobe/wardrobe-messages';
import {
  APPLICATION_DOWN_MESSAGE, CONTENT_LABELS, DELETION_FAILED_MESSAGE, DELETION_REFUSALS, DRY_RUN_PASSED, FORM_FIELDS,
  LIFECYCLE_LABELS, RECHECK_FAILED_MESSAGE, REPUBLICATION_DRY_RUN_PASSED, REPUBLICATION_FAILED_MESSAGE,
  REPUBLICATION_REFUSALS, STILL_IN_ERROR, UNEXPECTED_FAILURE, UNKNOWN_LISTING_MESSAGE, VALIDATION_REFUSALS,
  VALIDATION_REFUSED_MESSAGE, VERIFICATION_FAILED_MESSAGE
} from './listing-messages';

const APPLICATION_TITLE = 'Vinted Republisher';

// Morceau de phrase : texte, ou lien vers la fiche d'une autre annonce ou vers Vinted.
export interface TextPart {
  text: string;
  href?: string;
}

interface CountRow {
  label: string;
  views?: number;
  favourites?: number;
}

@Component({
  selector: 'app-listing-detail',
  imports: [NgTemplateOutlet, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './listing-detail.html',
  styleUrl: './listing-detail.scss'
})
export class ListingDetail {
  protected readonly dateOf = dateOf;
  protected readonly priceOf = priceOf;

  protected readonly problem = signal('');
  protected readonly detail = signal<ListingDetailView | null>(null);
  protected readonly journal = signal<JournalLine[]>([]);
  protected readonly notBackedUp = signal(false);
  protected readonly view = signal<ListingBackupView | null>(null);
  protected readonly verification = signal<BackupVerification | null>(null);
  protected readonly validateDisabled = signal(true);
  protected readonly sending = signal(false);
  protected readonly deletionAsked = signal(false);
  protected readonly republicationAsked = signal(false);
  protected readonly republicationState = signal('');
  protected readonly republicationWaiting = signal(false);
  protected readonly newListingUrl = signal('');
  protected readonly manualRepublicationMessage = signal('');

  private readonly title = inject(Title);
  private readonly details = inject(ListingDetailService);
  private readonly backups = inject(ListingBackupService);
  private readonly deletions = inject(ListingDeletionService);
  private readonly republications = inject(ListingRepublicationService);
  private listingId = 0;
  // Numéro de la dernière ouverture : une réponse arrivée après le passage à une autre annonce est ignorée.
  private opening = 0;
  private republicationTimer?: ReturnType<typeof setInterval>;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      // Toute réponse encore attendue devient périmée, et l'onglet ne garde pas le titre de l'annonce.
      this.opening++;
      clearInterval(this.republicationTimer);
      this.title.setTitle(APPLICATION_TITLE);
    });
    // Un lien vers une autre annonce de la chaîne garde le même écran : la fiche se relit à chaque identifiant.
    inject(ActivatedRoute).paramMap.pipe(takeUntilDestroyed())
      .subscribe(parameters => this.open(parameters.get('listingId')));
  }

  private async open(listingId: string | null): Promise<void> {
    this.opening++;
    this.reset();
    if (!listingId) {
      this.problem.set(UNKNOWN_LISTING_MESSAGE);
      return;
    }
    this.listingId = Number(listingId);
    if (await this.loadListing()) {
      await this.load();
    }
  }

  private reset(): void {
    clearInterval(this.republicationTimer);
    this.problem.set('');
    this.detail.set(null);
    this.journal.set([]);
    this.notBackedUp.set(false);
    this.view.set(null);
    this.verification.set(null);
    this.sending.set(false);
    this.deletionAsked.set(false);
    this.republicationAsked.set(false);
    this.newListingUrl.set('');
  }

  protected stageText(): string {
    return 'Étape : ' + (STAGE_LABELS[this.detail()?.listing.stage ?? ''] ?? '');
  }

  protected presenceText(detail: ListingDetailView): string {
    return detail.listing.present ? 'Présente dans le dressing.'
      : 'Absente du dressing, vue pour la dernière fois le ' + dateOf(detail.lastSeenAt) + '.';
  }

  // US-35 : l'outil conseille, il n'agit pas.
  protected adviceText(advice: RepublicationAdvice | undefined): string {
    return advice ? `Conseil : à republier, seulement ${advice.viewsGained} vues gagnées depuis le `
      + `${dayOf(advice.since)} (moins de 10 en 7 jours).` : '';
  }

  // US-24 : l'annonce remplacée et celle-ci côte à côte, puis le total de toute la chaîne de republications.
  protected comparisonOrigin(compared: StatisticsComparison): TextPart[] {
    return [
      { text: 'Republiée le ' + dateOf(compared.republishedAt) + ' depuis ' },
      { text: "l'annonce " + compared.previousListingId, href: listingLink(compared.previousListingId) },
      { text: '.' }
    ];
  }

  protected comparisonRows(compared: StatisticsComparison): CountRow[] {
    return [
      readingRow('Avant (annonce remplacée', compared.before),
      readingRow('Après (cette annonce', compared.after),
      {
        label: `Total depuis la première mise en ligne (${compared.previousListingCount + 1} annonces)`,
        views: compared.cumulatedViewCount, favourites: compared.cumulatedFavouriteCount
      }
    ];
  }

  // US-32 : les faits établis de toutes les annonces de l'article, la plus ancienne d'abord.
  protected lifecycleOf(event: LifecycleEvent, shownId: number): TextPart[] {
    const parts: TextPart[] = [{ text: dateOf(event.at) + ' – ' }, nameOf(event.listingId, shownId, true), { text: ' ' }];
    if (event.type === 'REPUBLISHED') {
      parts.push({ text: 'republiée sous ' }, nameOf(event.newListingId ?? 0, shownId, false));
    } else {
      parts.push({ text: LIFECYCLE_LABELS[event.type] ?? '' });
    }
    return parts;
  }

  protected failureOf(failure: VerificationFailure): string {
    return failureOf(failure);
  }

  // Vérification sur Vinted (US-40) : une annonce absente l'est de Vinted, son formulaire répondant 404.
  protected onlineFailureOf(failure: VerificationFailure): string {
    return failure.kind === 'LISTING_ABSENT' ? 'annonce absente de Vinted' : failureOf(failure);
  }

  protected contentOf(content: ListingBackupContent): [string, string][] {
    const fields = content as unknown as Record<string, unknown>;
    return CONTENT_LABELS.filter(([key]) => fields[key] !== undefined && fields[key] !== '')
      .map(([key, label]) => [label, key === 'price' ? priceOf(content.price) : String(fields[key])]);
  }

  protected photoAlt(rank: number): string {
    return rank === 0 ? 'Photo 0 (couverture)' : 'Photo ' + rank;
  }

  protected deletionMode(view: ListingBackupView): string {
    return view.deletionDryRun ? 'Dry-run actif : aucune suppression réelle' : 'Suppressions réelles activées';
  }

  protected deletionState(view: ListingBackupView): string {
    const outcome = view.deletion?.outcome;
    if (outcome === 'CONFIRMED') {
      return 'Supprimée de Vinted le ' + dateOf(view.deletion?.settledAt ?? '') + '.';
    }
    if (outcome === 'UNCONFIRMED') {
      return 'Suppression non confirmée, vérifier sur Vinted.';
    }
    return view.dryRunPassed ? DRY_RUN_PASSED : '';
  }

  protected deleteDisabled(view: ListingBackupView): boolean {
    return this.sending() || view.deletion?.outcome === 'UNCONFIRMED' || !view.validatedAt
      || !view.verification.passed;
  }

  protected republicationMode(view: ListingBackupView): string {
    return view.republicationDryRun ? 'Dry-run actif : aucune republication réelle' : 'Republications réelles activées';
  }

  // Une republication vérifiée « non publiée » laisse relancer ; en erreur ou réussie, elle se vérifie.
  protected republishable(view: ListingBackupView): boolean {
    return !view.republication || view.republication.outcome === 'NOT_PUBLISHED';
  }

  protected republicationOutcome(view: ListingBackupView): TextPart[] {
    const republication = view.republication;
    switch (republication?.outcome) {
      case 'PUBLISHED':
        return [
          { text: 'Republiée sur Vinted le ' + dateOf(republication.publishedAt ?? '') + ' : ' },
          { text: 'nouvelle annonce ' + republication.newListingId, href: republication.newListingUrl },
          { text: '.' }
        ];
      case 'FAILED':
        return [{ text: 'Erreur de republication : vérifiez sur Vinted avant toute relance.' }];
      case 'NOT_PUBLISHED':
        return [{
          text: "Republication non faite d'après votre vérification sur Vinted (le "
            + dateOf(republication.checkedAt ?? '') + '). Une nouvelle republication est possible.'
        }];
      default:
        return view.republicationDryRunPassed ? [{ text: REPUBLICATION_DRY_RUN_PASSED }] : [];
    }
  }

  protected comparisonText(differences: ListingFieldDifference[] | undefined): string {
    if (!differences) {
      return '';
    }
    if (differences.length === 0) {
      return 'Nouvelle annonce identique à la sauvegarde.';
    }
    return "Différences avec la sauvegarde, rien n'a été corrigé : "
      + differences.map(difference => `${FORM_FIELDS[difference.field] ?? difference.field} « `
        + `${difference.saved} » sauvegardé, « ${difference.read} » lu sur Vinted`).join(' ; ') + '.';
  }

  protected deletionSummary(view: ListingBackupView): string {
    return `« ${view.content.title} », ${priceOf(view.content.price)}, ${keptPhotos(view)}, sauvegarde validée le `
      + dateOf(view.validatedAt ?? '') + '.';
  }

  protected republicationSummary(view: ListingBackupView): string {
    return `« ${view.content.title} », ${priceOf(view.content.price)}, ${keptPhotos(view)}, supprimée de Vinted le `
      + dateOf(view.deletion?.settledAt ?? '') + '.';
  }

  protected async validate(): Promise<void> {
    const current = this.view();
    if (!current) {
      return;
    }
    const opening = this.opening;
    this.validateDisabled.set(true);
    try {
      const view = await answerOf(this.backups.validateListingBackup(this.listingId, { savedAt: current.savedAt }));
      if (opening === this.opening) {
        this.show(view);
      }
    } catch (failure) {
      if (opening !== this.opening) {
        return;
      }
      const status = statusOf(failure);
      const refusal = status === 409 || status === 422 ? await refusalOf<ValidationRefusal>(failure) : null;
      // Refusée (version remplacée ou vérification échouée) : rien à revalider avant de recharger la page.
      if (refusal?.verification) {
        this.verification.set(refusal.verification);
      } else if (!refusal) {
        this.validateDisabled.set(false);
      }
      this.problem.set(VALIDATION_REFUSALS[refusal?.reason ?? ''] ?? APPLICATION_DOWN_MESSAGE);
    }
  }

  protected confirmDeletion(): Promise<void> {
    const savedAt = this.view()?.savedAt ?? '';
    return this.send(this.deletions.deleteListing(this.listingId, { savedAt }),
      refusal => deletionRefusalOf(refusal as unknown as DeletionRefusal, false));
  }

  protected recheck(): Promise<void> {
    return this.send(this.deletions.recheckListingDeletion(this.listingId),
      refusal => deletionRefusalOf(refusal as unknown as DeletionRefusal, true));
  }

  protected confirmRepublication(): Promise<void> {
    const savedAt = this.view()?.savedAt ?? '';
    return this.send(this.republications.republishListing(this.listingId, { savedAt }),
      refusal => republicationRefusalOf(refusal as unknown as RepublicationRefusal));
  }

  // Le formulaire attend le membre dans Chrome ; la republication en erreur se valide ensuite ici (US-45).
  protected prepareManualRepublication(): Promise<void> {
    const savedAt = this.view()?.savedAt ?? '';
    return this.send(this.republications.prepareManualRepublication(this.listingId, { savedAt }).pipe(map(report => {
      this.manualRepublicationMessage.set(manualRepublicationMessageOf(report.unfilledFields, 'ici'));
      return report.listing;
    })), refusal => republicationRefusalOf(refusal as unknown as RepublicationRefusal));
  }

  protected verifyRepublication(): Promise<void> {
    return this.send(this.republications.verifyListingRepublication(this.listingId),
      refusal => VERIFICATION_FAILED_MESSAGE + (REPUBLICATION_REFUSALS[String(refusal['reason'])] ?? UNEXPECTED_FAILURE),
      view => {
        if (view.republication?.outcome === 'FAILED') {
          this.problem.set(STILL_IN_ERROR);
        }
      });
  }

  protected validatePublished(): Promise<void> {
    return this.send(this.republications.validateListingRepublication(this.listingId,
      { published: true, newListingUrl: this.newListingUrl().trim() }), manualValidationRefusalOf);
  }

  protected validateNotPublished(): Promise<void> {
    return this.send(this.republications.validateListingRepublication(this.listingId, { published: false }),
      manualValidationRefusalOf);
  }

  // Vrai si l'annonce est connue : sa sauvegarde se lit ensuite.
  private async loadListing(): Promise<boolean> {
    const opening = this.opening;
    try {
      const detail = await answerOf(this.details.showListingDetail(this.listingId));
      if (opening !== this.opening) {
        return false;
      }
      this.title.setTitle(detail.listing.title);
      this.journal.set(detail.journal.map(journalLineOf));
      this.detail.set(detail);
      return true;
    } catch (failure) {
      if (opening !== this.opening) {
        return false;
      }
      // 400 : identifiant invalide dans l'adresse ; 404 : annonce inconnue.
      const status = statusOf(failure);
      this.problem.set(status === 404 || status === 400 ? UNKNOWN_LISTING_MESSAGE : APPLICATION_DOWN_MESSAGE);
      return false;
    }
  }

  private async load(): Promise<void> {
    const opening = this.opening;
    try {
      const view = await answerOf(this.backups.showListingBackup(this.listingId));
      if (opening === this.opening) {
        this.show(view);
      }
    } catch (failure) {
      if (opening !== this.opening) {
        return;
      }
      if (statusOf(failure) === 404) {
        this.notBackedUp.set(true);
      } else {
        this.problem.set(APPLICATION_DOWN_MESSAGE);
      }
    }
  }

  private show(view: ListingBackupView): void {
    this.view.set(view);
    this.problem.set('');
    this.verification.set(view.verification);
    this.validateDisabled.set(!view.verification.passed);
    this.sending.set(false);
    this.deletionAsked.set(false);
    this.republicationAsked.set(false);
    this.showRepublicationDate(this.republishable(view) ? view.deletion?.republishableAt : undefined);
  }

  // L'heure du navigateur suffit : l'application n'écoute que sur 127.0.0.1, même machine que le serveur.
  private showRepublicationDate(republishableAt: string | undefined): void {
    clearInterval(this.republicationTimer);
    this.republicationState.set('');
    this.republicationWaiting.set(false);
    if (!republishableAt) {
      return;
    }
    const date = new Date(republishableAt);
    const update = () => {
      const seconds = Math.ceil((date.getTime() - Date.now()) / 1000);
      this.republicationWaiting.set(seconds > 0);
      if (seconds <= 0) {
        clearInterval(this.republicationTimer);
        this.republicationState.set('Republication possible.');
        return;
      }
      const hour = date.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
      this.republicationState.set(`Republication possible dans ${Math.floor(seconds / 60)} min `
        + `${seconds % 60} s (à ${hour}).`);
    };
    update();
    this.republicationTimer = setInterval(update, 1000);
  }

  private async send(request: Observable<ListingBackupView>,
                     refusalMessageOf: (refusal: Record<string, unknown>) => string,
                     onAnswer: (view: ListingBackupView) => void = () => undefined): Promise<void> {
    this.sending.set(true);
    this.manualRepublicationMessage.set('');
    this.deletionAsked.set(false);
    this.republicationAsked.set(false);
    const opening = this.opening;
    let message = APPLICATION_DOWN_MESSAGE;
    try {
      const view = await answerOf(request);
      if (opening !== this.opening) {
        return;
      }
      this.show(view);
      onAnswer(view);
      return;
    } catch (failure) {
      const refusal = statusOf(failure) === 0 ? null : await refusalOf(failure);
      if (refusal) {
        message = refusalMessageOf(refusal);
      }
    }
    // L'état a pu changer côté application (doute enregistré, vérification échouée) : on le relit.
    await this.load();
    if (opening === this.opening) {
      this.problem.set(message);
    }
  }
}

function keptPhotos(view: ListingBackupView): string {
  const count = view.photos.length;
  return `${count} photo${count > 1 ? 's' : ''} gardée${count > 1 ? 's' : ''}`;
}

function readingRow(label: string, reading: ListingStatistics | undefined): CountRow {
  if (!reading) {
    return { label: label + ')' };
  }
  return { label: label + ', au ' + dateOf(reading.readAt) + ')', views: reading.viewCount, favourites: reading.favouriteCount };
}

// Lien vers la fiche d'une autre annonce de la chaîne, texte simple pour celle affichée.
function nameOf(id: number, shownId: number, startsSentence: boolean): TextPart {
  if (id === shownId) {
    return { text: startsSentence ? 'Cette annonce' : 'cette annonce' };
  }
  return { text: (startsSentence ? 'Annonce ' : "l'annonce ") + id, href: listingLink(id) };
}

function failureOf(failure: VerificationFailure): string {
  switch (failure.kind) {
    case 'NO_PHOTO': return 'aucune photo gardée : refaites la sauvegarde';
    case 'PHOTO_MISSING': return `photo ${failure.rank} manquante sur le disque`;
    case 'PHOTO_ALTERED': return `photo ${failure.rank} modifiée sur le disque depuis sa sauvegarde`;
    case 'LISTING_ABSENT': return 'annonce absente du dressing';
    case 'TITLE_CHANGED':
      return `titre « ${failure.savedTitle} » sauvegardé, « ${failure.scannedTitle} » au dernier scan`;
    case 'PRICE_CHANGED':
      return `prix ${priceOrEmpty(failure.savedPrice)} sauvegardé, ${priceOrEmpty(failure.scannedPrice)} au dernier scan`;
    case 'FIELD_CHANGED': {
      const difference = failure.difference;
      return `${(FORM_FIELDS[difference?.field ?? ''] ?? difference?.field ?? '').toLowerCase()} « ${difference?.saved ?? ''} »`
        + ` sauvegardé, « ${difference?.read ?? ''} » sur Vinted`;
    }
    default: return failure.kind;
  }
}

function priceOrEmpty(price: Parameters<typeof priceOf>[0] | undefined): string {
  return price ? priceOf(price) : '';
}

function differenceOf(difference: ListingDifference): string {
  switch (difference.kind) {
    case 'NOT_ACTIVE': return 'annonce plus active sur Vinted';
    case 'TITLE_CHANGED':
      return `titre « ${difference.savedTitle} » sauvegardé, « ${difference.readTitle} » lu sur Vinted`;
    case 'PRICE_CHANGED':
      return `prix ${priceOrEmpty(difference.savedPrice)} sauvegardé, ${priceOrEmpty(difference.readPrice)} lu sur Vinted`;
    default: return difference.kind;
  }
}

function cadenceDateOf(allowedAt: string | undefined): string {
  return ' (à partir du ' + dateOf(allowedAt ?? '') + ').';
}

function deletionRefusalOf(refusal: DeletionRefusal, recheck: boolean): string {
  const reason = DELETION_REFUSALS[refusal.reason];
  if (!reason) {
    return recheck ? RECHECK_FAILED_MESSAGE + UNEXPECTED_FAILURE : DELETION_FAILED_MESSAGE;
  }
  if (recheck) {
    return RECHECK_FAILED_MESSAGE + reason;
  }
  if (refusal.reason === 'CADENCE_LIMIT_REACHED') {
    return "Suppression refusée, rien n'a été supprimé : " + reason + cadenceDateOf(refusal.allowedAt);
  }
  const differences = (refusal.differences ?? []).map(differenceOf).join(' ; ');
  return "Suppression refusée, rien n'a été supprimé : " + reason + (differences ? ' : ' + differences + '.' : '');
}

function republicationRefusalOf(refusal: RepublicationRefusal): string {
  let reason = REPUBLICATION_REFUSALS[refusal.reason];
  if (!reason) {
    return REPUBLICATION_FAILED_MESSAGE;
  }
  if (refusal.reason === 'TOO_EARLY') {
    reason += ' (à partir du ' + dateOf(refusal.republishableAt ?? '') + ').';
  } else if (refusal.reason === 'CADENCE_LIMIT_REACHED') {
    reason += cadenceDateOf(refusal.allowedAt);
  } else if (refusal.reason === 'FIELD_NOT_FILLABLE') {
    reason = (FORM_FIELDS[refusal.field ?? ''] ?? refusal.field) + ' impossible à remplir'
      + (refusal.optionId ? ` (identifiant ${refusal.optionId})` : '') + '.';
  } else if (refusal.reason === 'PHOTOS_NOT_VERIFIED') {
    reason += ' : ' + (refusal.verification?.failures ?? []).map(failureOf).join(' ; ') + '.';
  }
  return "Republication refusée, rien n'a été publié : " + reason;
}

function manualValidationRefusalOf(refusal: Record<string, unknown>): string {
  return VALIDATION_REFUSED_MESSAGE + (REPUBLICATION_REFUSALS[String(refusal['reason'])] ?? UNEXPECTED_FAILURE);
}
