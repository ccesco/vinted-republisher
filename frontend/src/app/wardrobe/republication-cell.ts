import type { Listing } from '../../generated/api';
import { dayOf } from '../shared/format';

export interface RepublicationCell {
  text: string;
  newListingUrl?: string;
}

// Colonne « Republication » de la liste (US-38) : seulement pour une annonce republiée ou à vérifier.
export function republicationCellOf(listing: Listing): RepublicationCell {
  const republication = listing.republication;
  if (listing.stage === 'REPUBLISHED' && republication?.publishedAt) {
    const text = `le ${dayOf(republication.publishedAt)}`;
    return republication.newListingUrl ? { text, newListingUrl: republication.newListingUrl } : { text };
  }
  if (listing.stage === 'REPUBLICATION_TO_CHECK' && republication) {
    return { text: `tentée le ${dayOf(republication.attemptedAt)}, à vérifier` };
  }
  return { text: '' };
}
