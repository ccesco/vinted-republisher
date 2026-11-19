import { describe, expect, it } from 'vitest';
import type { Listing } from '../../generated/api';
import { republicationTallyTextOf } from './republication-tally';

function aListing(published: number, notPublished: number): Listing {
  return {
    vintedId: 1, title: 'Pull', price: { amount: 10, currency: 'EUR' }, url: 'https://vinted.test/items/1',
    present: true, stage: 'TO_DELETE', republicationTally: { published, notPublished }
  };
}

describe('republication tally', () => {
  it('gives the published republications and the failures, in the plural', () => {
    expect(republicationTallyTextOf(aListing(2, 3))).toBe('2 réussies, 3 échecs');
  });

  it('gives one of each in the singular', () => {
    expect(republicationTallyTextOf(aListing(1, 1))).toBe('1 réussie, 1 échec');
  });

  it('leaves out a part at zero', () => {
    expect(republicationTallyTextOf(aListing(1, 0))).toBe('1 réussie');
    expect(republicationTallyTextOf(aListing(0, 3))).toBe('3 échecs');
  });

  it('gives nothing without republication', () => {
    expect(republicationTallyTextOf(aListing(0, 0))).toBe('');
  });

  it('gives nothing when the answer bears no tally', () => {
    expect(republicationTallyTextOf({ ...aListing(0, 0), republicationTally: undefined })).toBe('');
  });
});
