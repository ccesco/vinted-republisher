import type { Price } from '../../generated/api';

export function priceOf(price: Price): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: price.currency }).format(price.amount);
}

export function dateOf(instant: string): string {
  return new Date(instant).toLocaleString('fr-FR');
}

export function dayOf(instant: string): string {
  return new Date(instant).toLocaleDateString('fr-FR');
}

export function plural(count: number, word: string): string {
  return `${count} ${word}${count > 1 ? 's' : ''}`;
}

export function listingLink(listingId: number): string {
  return `#/listings/${encodeURIComponent(listingId)}`;
}
