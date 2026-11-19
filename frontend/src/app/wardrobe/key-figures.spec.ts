import { describe, expect, it } from 'vitest';
import type { Listing } from '../../generated/api';
import { coversWholeWardrobe, keyFiguresOf } from './key-figures';

function aListing(changes: Partial<Listing>): Listing {
  return {
    vintedId: 1, title: 'Pull', price: { amount: 10, currency: 'EUR' }, url: 'https://vinted.test/items/1',
    present: true, stage: 'TO_BACK_UP', ...changes
  };
}

function readOf(viewCount: number, favouriteCount: number) {
  return { statistics: { viewCount, favouriteCount, readAt: '2026-11-17T10:00:00Z' } };
}

describe('key figures', () => {
  it('count listings online, validated backups, views and favourites of the shown listings', () => {
    const listings = [
      aListing({ vintedId: 1, validatedAt: '2026-11-10T10:00:00Z', ...readOf(100, 5) }),
      aListing({ vintedId: 2, validatedAt: '2026-11-11T10:00:00Z', ...readOf(20, 4) }),
      aListing({ vintedId: 3 })
    ];

    expect(keyFiguresOf(listings)).toEqual({ online: 3, validated: 2, views: 120, favourites: 9 });
  });

  it('leave absent listings out', () => {
    const listings = [aListing({ present: false, validatedAt: '2026-11-10T10:00:00Z', ...readOf(50, 2) })];

    expect(keyFiguresOf(listings)).toEqual({ online: 0, validated: 0, views: 0, favourites: 0 });
  });
});

describe('a list covering the whole wardrobe', () => {
  it('is one without criteria, whatever its order, with present or all listings', () => {
    expect(coversWholeWardrobe({})).toBe(true);
    expect(coversWholeWardrobe({ presence: 'PRESENT', backup: 'ALL', sort: 'RECENCY', direction: 'DESC' })).toBe(true);
    expect(coversWholeWardrobe({ presence: 'ALL', backup: 'ALL', sort: 'VIEWS', direction: 'ASC' })).toBe(true);
  });

  it('is not one filtered by presence, backup, search, tag or stage', () => {
    expect(coversWholeWardrobe({ presence: 'ABSENT' })).toBe(false);
    expect(coversWholeWardrobe({ backup: 'VALIDATED' })).toBe(false);
    expect(coversWholeWardrobe({ search: 'pull' })).toBe(false);
    expect(coversWholeWardrobe({ tag: 'hiver' })).toBe(false);
    expect(coversWholeWardrobe({ stage: 'TO_DELETE' })).toBe(false);
  });
});
