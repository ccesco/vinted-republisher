import type { JournalEntry } from '../../generated/api';
import { FORM_FIELDS } from '../listing/listing-messages';
import { dateOf } from './format';

// Journal des actions (US-23) : libellés des codes du contrat, partagés par l'écran du journal et la fiche.
const JOURNAL_ACTIONS: Record<string, string> = {
  SCAN: 'Scan du dressing', WARDROBE_BACKUP: 'Sauvegarde du dressing', BACKUP_VALIDATION: 'Validation',
  BACKUP_VERIFICATION: 'Vérification de la sauvegarde', DELETION: 'Suppression', DELETION_RECHECK: 'Revérification',
  REPUBLICATION: 'Republication', REPUBLICATION_VERIFICATION: 'Vérification de la republication',
  REPUBLICATION_VALIDATION: 'Validation de la republication', STATISTICS_REFRESH: 'Rafraîchissement des vues et favoris'
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
  DELETION_FAILED: 'échec de la suppression', REPUBLICATION_FAILED: 'échec de la republication',
  // Refus d'une vérification ou d'une validation de la republication (US-44).
  NO_REPUBLICATION_TO_VERIFY: 'aucune republication à vérifier',
  NEW_LISTING_LINK_INVALID: 'lien de la nouvelle annonce invalide',
  NEW_LISTING_MISMATCH: 'nouvelle annonce différente de la sauvegarde',
  // Motifs d'une vérification de la sauvegarde (US-40), séparés par des virgules ; un champ changé suit son code.
  NO_PHOTO: 'aucune photo gardée', PHOTO_MISSING: 'photo manquante', PHOTO_ALTERED: 'photo modifiée',
  LISTING_ABSENT: 'annonce absente', FIELD_CHANGED: 'champ changé'
};

// Une raison peut en réunir plusieurs (« FIELD_CHANGED:SIZE, PHOTO_MISSING ») ; un code inconnu reste tel quel.
function labelsOf(reason: string): string {
  return reason.split(', ').map(part => {
    const [code, detail] = part.split(':');
    const label = JOURNAL_REASONS[code] ?? code;
    return detail ? `${label} (${(FORM_FIELDS[detail] ?? detail).toLowerCase()})` : label;
  }).join(', ');
}

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

// Annonces en échec (US-39) : la raison en clair. Une ligne « non faite » sans raison dit ce qui n'est pas fait :
// la suppression pour une revérification, la republication pour une validation « non publiée » (US-44).
export function causeOf(entry: JournalEntry): string {
  if (entry.outcome === 'NOT_DONE' && !entry.reason) {
    return entry.action === 'REPUBLICATION_VALIDATION' ? 'republication non faite' : 'suppression non faite';
  }
  return reasonOf(entry);
}

export function actionOf(entry: JournalEntry): string {
  return JOURNAL_ACTIONS[entry.action] ?? entry.action;
}

export function outcomeOf(entry: JournalEntry): string {
  return JOURNAL_OUTCOMES[entry.outcome] ?? entry.outcome;
}

function reasonOf(entry: JournalEntry): string {
  return entry.reason ? labelsOf(entry.reason) : '';
}
