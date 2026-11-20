export interface DeletionFacts {
 sold: boolean;
 backedUp: boolean;
 unconfirmed: boolean;
 validated: boolean;
 verified: boolean;
}

// Raison qui grise « Supprimer de Vinted » (US-52), la première qui s'applique ; vide si la suppression est possible.
export function deletionBlockOf(facts: DeletionFacts): string {
 if (facts.sold) {
  return 'Annonce vendue : rien à supprimer.';
 }
 if (!facts.backedUp) {
  return 'Aucune sauvegarde : sauvegarder d\'abord.';
 }
 if (facts.unconfirmed) {
  return 'Suppression non confirmée : revérifier sur Vinted.';
 }
 if (!facts.validated) {
  return 'Sauvegarde non validée : valider d\'abord.';
 }
 if (!facts.verified) {
  return 'Vérification de la sauvegarde en échec.';
 }
 return '';
}
