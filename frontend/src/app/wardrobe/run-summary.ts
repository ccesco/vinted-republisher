import type { Listing } from '../../generated/api';

// Annonces de la tournée en cours (US-15), nommées par leur titre quand la liste les affiche.
export function runningSummaryOf(listingIds: number[], listings: Listing[]): string {
  if (listingIds.length === 0) {
    return 'Traitement en cours.';
  }
  const titles = new Map(listings.map(listing => [listing.vintedId, listing.title]));
  const names = listingIds.map(id => titles.has(id) ? `${titles.get(id)} (annonce ${id})` : `annonce ${id}`);
  return `Traitement en cours : ${names.join(', ')}.`;
}
