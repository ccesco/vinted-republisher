import type { Listing } from '../../generated/api';

export interface KeyFigures {
  online: number;
  validated: number;
  views: number;
  favourites: number;
}

// Calculés sur les annonces affichées et présentes : l'API ne donne pas d'agrégat.
export function keyFiguresOf(listings: Listing[]): KeyFigures {
  const online = listings.filter(listing => listing.present);
  return {
    online: online.length,
    validated: online.filter(listing => listing.validatedAt).length,
    views: online.reduce((sum, listing) => sum + (listing.statistics?.viewCount ?? 0), 0),
    favourites: online.reduce((sum, listing) => sum + (listing.statistics?.favouriteCount ?? 0), 0)
  };
}
