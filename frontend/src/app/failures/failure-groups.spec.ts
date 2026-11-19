import { describe, expect, it } from 'vitest';
import type { FailedListing } from '../../generated/api';
import { failureGroupsOf } from './failure-groups';

function failed(listingId: number, kind: FailedListing['kind']): FailedListing {
  return {
    listingId, listingTitle: 'Annonce ' + listingId, stage: 'TO_REPUBLISH', kind,
    failure: { entryId: listingId, occurredAt: '2026-11-18T10:00:00Z', action: 'REPUBLICATION', outcome: 'FAILED' }
  };
}

describe('failure groups', () => {
  it('splits the failed listings by kind, keeping their order, always in three groups', () => {
    const groups = failureGroupsOf([failed(1, 'REFUSED'), failed(2, 'IN_DOUBT'), failed(3, 'REFUSED')]);

    expect(groups.map(group => group.title)).toEqual(['Dans le doute', 'Refusées', 'Autres échecs']);
    expect(groups.map(group => group.listings.map(listing => listing.listingId))).toEqual([[2], [1, 3], []]);
  });
});
