import type { Listing } from '../../generated/api';
import { plural } from '../shared/format';

// Colonne « Republications » de la liste (US-44) : réussies et échecs de tout l'article, une partie à zéro omise.
export function republicationTallyTextOf(listing: Listing): string {
  const tally = listing.republicationTally;
  // Optionnel dans le contrat : la liste le rend toujours, la fiche et la relance jamais.
  if (!tally) {
    return '';
  }
  const parts = [
    tally.published > 0 ? plural(tally.published, 'réussie') : '',
    tally.notPublished > 0 ? plural(tally.notPublished, 'échec') : ''
  ];
  return parts.filter(part => part !== '').join(', ');
}
