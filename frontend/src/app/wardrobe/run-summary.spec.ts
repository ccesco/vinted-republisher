import { describe, expect, it } from 'vitest';
import type { Listing } from '../../generated/api';
import { runningSummaryOf } from './run-summary';

function aListing(vintedId: number, title: string): Listing {
  return {
    vintedId, title, price: { amount: 10, currency: 'EUR' }, url: `https://vinted.test/items/${vintedId}`,
    present: true, stage: 'TO_DELETE'
  };
}

describe('running summary', () => {
  it('names the listing being processed', () => {
    expect(runningSummaryOf([3], [aListing(3, 'Pull')])).toBe('Traitement en cours : Pull (annonce 3).');
  });

  it('names every listing of the round', () => {
    expect(runningSummaryOf([1, 2], [aListing(1, 'Bonnet'), aListing(2, 'Pull')]))
      .toBe('Traitement en cours : Bonnet (annonce 1), Pull (annonce 2).');
  });

  it('falls back to the identifier of a listing not shown', () => {
    expect(runningSummaryOf([3], [])).toBe('Traitement en cours : annonce 3.');
  });

  it('says only that a run is in progress between two rounds', () => {
    expect(runningSummaryOf([], [])).toBe('Traitement en cours.');
  });
});
