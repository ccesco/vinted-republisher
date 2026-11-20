import type { ListingBackupView } from '../../generated/api';

// Annonce à relier (US-48) : supprimée, ni republication en erreur ni republication réussie ; sa nouvelle annonce,
// mise en ligne hors de l'outil, se retrouve par « Vérifier sur Vinted » ou se colle dans « Publiée ».
export function toLink(view: ListingBackupView): boolean {
  return view.deletion?.outcome === 'CONFIRMED'
    && (!view.republication || view.republication.outcome === 'NOT_PUBLISHED');
}

const SEARCHES: Record<NonNullable<ListingBackupView['newListingSearch']>, string> = {
  NOT_FOUND: 'Aucune nouvelle annonce trouvée sur Vinted',
  SEVERAL: 'Plusieurs annonces correspondent sur Vinted'
};

// Vérification restée sans résultat : rien n'est enregistré, le membre colle le lien de la nouvelle annonce.
export function newListingSearchMessageOf(search: NonNullable<ListingBackupView['newListingSearch']>): string {
  return SEARCHES[search] + ' : collez le lien de la nouvelle annonce dans « Publiée »';
}
