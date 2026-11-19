import type { Listing } from '../../generated/api';
import type { InventoryCriteria } from './inventory-criteria';

export interface KeyFigures {
  online: number;
  validated: number;
  views: number;
  favourites: number;
}

// Calculés sur les annonces présentes reçues : l'API ne donne pas d'agrégat.
export function keyFiguresOf(listings: Listing[]): KeyFigures {
  const online = listings.filter(listing => listing.present);
  return {
    online: online.length,
    validated: online.filter(listing => listing.validatedAt).length,
    views: online.reduce((sum, listing) => sum + (listing.statistics?.viewCount ?? 0), 0),
    favourites: online.reduce((sum, listing) => sum + (listing.statistics?.favouriteCount ?? 0), 0)
  };
}

// Une liste sans filtre contient toutes les annonces présentes : les chiffres clés s'en déduisent sans autre appel.
export function coversWholeWardrobe(criteria: InventoryCriteria): boolean {
  return criteria.presence !== 'ABSENT' && (criteria.backup ?? 'ALL') === 'ALL'
    && !criteria.search && !criteria.tag && !criteria.stage;
}
