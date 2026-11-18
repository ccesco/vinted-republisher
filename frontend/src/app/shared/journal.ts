import type { JournalEntry } from '../../generated/api';
import { dateOf } from './format';

// Journal des actions (US-23) : libellés des codes du contrat, partagés par l'écran du journal et la fiche.
const JOURNAL_ACTIONS: Record<string, string> = {
  SCAN: 'Scan du dressing', WARDROBE_BACKUP: 'Sauvegarde du dressing', BACKUP_VALIDATION: 'Validation',
  DELETION: 'Suppression', DELETION_RECHECK: 'Revérification', REPUBLICATION: 'Republication'
};
const JOURNAL_OUTCOMES: Record<string, string> = {
  STARTED: 'lancée', SUCCEEDED: 'réussie', DRY_RUN: 'dry-run réussi', REFUSED: 'refusée',
  IN_DOUBT: 'dans le doute', NOT_DONE: 'non faite', FAILED: 'en échec'
};
// Refus, doutes et échecs : mis en évidence.
const JOURNAL_HIGHLIGHTED = ['REFUSED', 'IN_DOUBT', 'FAILED'];
const JOURNAL_REASONS: Record<string, string> = {
  BACKUP_REPLACED: 'sauvegarde remplacée', VERIFICATION_FAILED: 'vérification échouée',
  PHOTOS_NOT_VERIFIED: 'photos gardées non intactes',
  NOT_VALIDATED: 'sauvegarde non validée', ALREADY_DELETED: 'déjà supprimée',
  DELETION_UNCONFIRMED: 'suppression précédente non confirmée', NO_UNCONFIRMED_DELETION: 'aucune suppression à revérifier',
  LISTING_CHANGED: 'annonce différente sur Vinted', LISTING_PAGE_UNREADABLE: "page de l'annonce illisible",
  NOT_DELETED: 'pas de suppression confirmée', TOO_EARLY: 'trop tôt', ALREADY_REPUBLISHED: 'déjà republiée',
  REPUBLICATION_IN_ERROR: 'republication précédente en erreur', FIELD_NOT_FILLABLE: 'champ impossible à remplir',
  FORM_UNREADABLE: 'formulaire de mise en vente illisible', BROWSER_BUSY: 'autre action en cours',
  BROWSER_UNAVAILABLE: 'navigateur indisponible', VINTED_UNREACHABLE: 'Vinted inaccessible',
  SESSION_DISCONNECTED: 'session non connectée', WARDROBE_UNREADABLE: 'dressing illisible',
  LISTING_UNREADABLE: 'annonce illisible', SCAN_FAILED: 'échec du scan', BACKUP_FAILED: 'échec de la sauvegarde',
  DELETION_FAILED: 'échec de la suppression', REPUBLICATION_FAILED: 'échec de la republication'
};

export interface JournalLine {
  date: string;
  action: string;
  outcome: string;
  highlighted: boolean;
  reason: string;
  listingId?: number;
  listingName: string;
}

export function journalLineOf(entry: JournalEntry): JournalLine {
  return {
    date: dateOf(entry.occurredAt),
    action: actionOf(entry),
    outcome: outcomeOf(entry),
    highlighted: JOURNAL_HIGHLIGHTED.includes(entry.outcome),
    reason: reasonOf(entry),
    listingId: entry.listingId,
    listingName: entry.listingTitle ?? String(entry.listingId ?? '')
  };
}

// Annonces en échec (US-39) : la raison en clair ; une revérification « non faite » n'en a pas.
export function causeOf(entry: JournalEntry): string {
  return entry.outcome === 'NOT_DONE' && !entry.reason ? 'suppression non faite' : reasonOf(entry);
}

export function actionOf(entry: JournalEntry): string {
  return JOURNAL_ACTIONS[entry.action] ?? entry.action;
}

export function outcomeOf(entry: JournalEntry): string {
  return JOURNAL_OUTCOMES[entry.outcome] ?? entry.outcome;
}

function reasonOf(entry: JournalEntry): string {
  return entry.reason ? (JOURNAL_REASONS[entry.reason] ?? entry.reason) : '';
}
