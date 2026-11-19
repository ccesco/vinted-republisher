import { describe, expect, it } from 'vitest';
import type { Listing } from '../../generated/api';
import { republicationCellOf } from './republication-cell';

function aListing(changes: Partial<Listing>): Listing {
  return {
    vintedId: 1, title: 'Pull', price: { amount: 10, currency: 'EUR' }, url: 'https://vinted.test/items/1',
    present: false, stage: 'TO_DELETE', ...changes
  };
}

describe('republication cell', () => {
  it('gives the publication day and the new listing of a republished listing', () => {
    const listing = aListing({
      stage: 'REPUBLISHED', republication: {
        outcome: 'PUBLISHED', attemptedAt: '2026-11-18T11:00:00Z', publishedAt: '2026-11-18T11:05:00Z',
        newListingId: 2, newListingUrl: 'https://vinted.test/items/2', backupSavedAt: '2026-11-10T10:00:00Z'
      }
    });

    expect(republicationCellOf(listing)).toEqual({ text: 'le 18/11/2026', newListingUrl: 'https://vinted.test/items/2' });
  });

  it('gives no link when the new listing is not known', () => {
    const listing = aListing({
      stage: 'REPUBLISHED', republication: {
        outcome: 'PUBLISHED', attemptedAt: '2026-11-18T11:00:00Z', publishedAt: '2026-11-18T11:05:00Z',
        newListingId: 2, backupSavedAt: '2026-11-10T10:00:00Z'
      }
    });

    expect(republicationCellOf(listing)).toEqual({ text: 'le 18/11/2026' });
  });

  it('gives the attempt day of a republication to check', () => {
    const listing = aListing({
      stage: 'REPUBLICATION_TO_CHECK', republication: {
        outcome: 'FAILED', attemptedAt: '2026-11-18T11:00:00Z', backupSavedAt: '2026-11-10T10:00:00Z'
      }
    });

    expect(republicationCellOf(listing)).toEqual({ text: 'tentée le 18/11/2026, à vérifier' });
  });

  it('gives nothing for a listing without republication', () => {
    expect(republicationCellOf(aListing({}))).toEqual({ text: '' });
  });
});
