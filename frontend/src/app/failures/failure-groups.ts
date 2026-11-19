import type { FailedListing } from '../../generated/api';

export interface FailureGroup {
  kind: FailedListing['kind'];
  title: string;
  listings: FailedListing[];
}

// Les annonces en échec, groupées selon ce que le membre doit faire (US-45) ; chaque groupe garde l'ordre reçu.
const GROUPS: { kind: FailedListing['kind']; title: string }[] = [
  { kind: 'IN_DOUBT', title: 'Dans le doute' },
  { kind: 'REFUSED', title: 'Refusées' },
  { kind: 'OTHER', title: 'Autres échecs' }
];

export function failureGroupsOf(listings: FailedListing[]): FailureGroup[] {
  return GROUPS.map(group => ({ ...group, listings: listings.filter(listing => listing.kind === group.kind) }));
}
