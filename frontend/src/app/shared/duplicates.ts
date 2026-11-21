import type { DuplicateDeletionRefusal, DuplicateDeletionReport, ListingDifference } from '../../generated/api';
import { listingLink, priceOf } from './format';

// Doublons (US-47) : libellés partagés par la page « Doublons » et la fiche d'une annonce.

export interface SentencePart {
  text: string;
  href?: string;
}

const REFUSALS: Record<string, string> = {
  NOT_A_DUPLICATE: "plus aucune autre annonce présente n'a ce titre et ce prix",
  HAS_HISTORY: 'annonce avec un historique à garder (republication, suppression ou vente)',
  BROWSER_BUSY: 'une autre action est en cours sur cette annonce',
  TWIN_NOT_CONFIRMED: 'aucune jumelle confirmée en vente sur Vinted',
  LISTING_CHANGED: 'annonce différente sur Vinted',
  BROWSER_UNAVAILABLE: 'navigateur indisponible',
  VINTED_UNREACHABLE: 'Vinted inaccessible',
  SESSION_DISCONNECTED: 'session Vinted non connectée',
  LISTING_PAGE_UNREADABLE: "page de l'annonce illisible",
  DELETION_FAILED: 'échec inattendu avant la confirmation (détail dans les logs)'
};

const APPLICATION_DOWN = "L'application ne répond pas : rechargez la page.";

export function twinsSentenceOf(twinIds: number[]): SentencePart[] {
  const parts: SentencePart[] = [{ text: 'Même titre et même prix que ' }];
  twinIds.forEach((id, index) => {
    if (index > 0) {
      parts.push({ text: ', ' });
    }
    parts.push({ text: "l'annonce " + id, href: listingLink(id) });
  });
  parts.push({ text: '.' });
  return parts;
}

export function duplicateConfirmationOf(listingId: number, twinIds: number[], dryRun: boolean): string {
  const kept = twinIds.length === 1 ? `L'annonce ${twinIds[0]} reste en vente.`
    : `Les annonces ${twinIds.join(', ')} restent en vente.`;
  return `Supprimer de Vinted l'annonce ${listingId} et l'effacer de l'outil (sauvegarde, photos, relevés, tags). `
    + `${kept} Irréversible.` + (dryRun ? ' Dry-run : rien ne sera supprimé.' : '');
}

export function duplicateOutcomeMessageOf(listingId: number, report: DuplicateDeletionReport): string {
  const twin = report.twinListingId;
  switch (report.outcome) {
    case 'DELETED':
      return `Doublon ${listingId} supprimé de Vinted et effacé de l'outil ; l'annonce ${twin} reste en vente.`;
    case 'ALREADY_GONE':
      return `Doublon ${listingId} déjà absent de Vinted : effacé de l'outil ; l'annonce ${twin} reste en vente.`;
    case 'DRY_RUN_PASSED':
      return `Dry-run : rien supprimé. L'annonce ${twin} et le doublon ${listingId} ont été relus sur Vinted.`;
    default:
      return `Suppression du doublon ${listingId} non confirmée : vérifiez sur Vinted, puis redemandez.`;
  }
}

// Doublon effacé de l'outil : sa fiche n'existe plus.
export function erased(report: DuplicateDeletionReport): boolean {
  return report.outcome === 'DELETED' || report.outcome === 'ALREADY_GONE';
}

export function duplicateRefusalMessageOf(refusal: DuplicateDeletionRefusal | null, status: number): string {
  if (status === 404) {
    return "Doublon non supprimé : annonce inconnue de l'outil.";
  }
  if (!refusal) {
    return APPLICATION_DOWN;
  }
  let reason = REFUSALS[refusal.reason] ?? refusal.reason;
  if (refusal.differences?.length) {
    reason += ' (' + refusal.differences.map(differenceOf).join(' ; ') + ')';
  }
  return 'Doublon non supprimé : ' + reason + '.';
}

function differenceOf(difference: ListingDifference): string {
  switch (difference.kind) {
    case 'NOT_ACTIVE':
      return 'plus en vente';
    case 'TITLE_CHANGED':
      return `titre « ${difference.readTitle} » lu, « ${difference.savedTitle} » enregistré`;
    default:
      return `prix ${difference.readPrice ? priceOf(difference.readPrice) : '?'} lu, `
        + `${difference.savedPrice ? priceOf(difference.savedPrice) : '?'} enregistré`;
  }
}
