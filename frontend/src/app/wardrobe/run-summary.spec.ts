import { describe, expect, it } from 'vitest';
import type { Listing, RunWorker } from '../../generated/api';
import { runningSummaryOf, workerLinesOf } from './run-summary';

function aListing(vintedId: number, title: string): Listing {
  return {
    vintedId, title, price: { amount: 10, currency: 'EUR' }, url: `https://vinted.test/items/${vintedId}`,
    present: true, stage: 'TO_DELETE'
  };
}

function sequential(listingId?: number): RunWorker[] {
  return [listingId === undefined
    ? { kind: 'ALL_ACTIONS', state: 'IDLE', relaunchCount: 0 }
    : { kind: 'ALL_ACTIONS', state: 'WORKING', listingId, action: 'DELETION', relaunchCount: 0 }];
}

const formatted = (instant: string) => `le ${instant}`;

describe('running summary', () => {
  it('names the listing being processed', () => {
    expect(runningSummaryOf(sequential(3), [aListing(3, 'Pull')])).toBe('Traitement en cours : Pull (annonce 3).');
  });

  it('falls back to the identifier of a listing not shown', () => {
    expect(runningSummaryOf(sequential(3), [])).toBe('Traitement en cours : annonce 3.');
  });

  it('says only that a run is in progress between two relaunches or with workers', () => {
    expect(runningSummaryOf(sequential(), [])).toBe('Traitement en cours.');
    expect(runningSummaryOf([{ kind: 'DELETION', state: 'WORKING', listingId: 1, action: 'DELETION',
      relaunchCount: 0 }], [])).toBe('Traitement en cours.');
  });
});

describe('worker lines', () => {
  it('shows what each worker does', () => {
    // AT-46-12
    const workers: RunWorker[] = [
      { kind: 'REPUBLICATION', state: 'WORKING', listingId: 1, action: 'REPUBLICATION', relaunchCount: 0 },
      { kind: 'DELETION', state: 'WAITING', waitingUntil: '14:32', waitingReason: 'CADENCE', relaunchCount: 1 },
      { kind: 'VERIFICATION', state: 'IDLE', relaunchCount: 0 }
    ];
    expect(workerLinesOf(workers, [aListing(1, 'Bonnet')], formatted)).toEqual([
      'Republication : republie Bonnet (annonce 1)',
      'Suppression : en attente jusqu\'au le 14:32 (cadence)',
      'Vérification : rien à faire'
    ]);
  });

  it('says every action and waiting reason in words', () => {
    const lines = workerLinesOf([
      { kind: 'DELETION', state: 'WORKING', listingId: 2, action: 'DELETION_RECHECK', relaunchCount: 0 },
      { kind: 'VERIFICATION', state: 'WORKING', listingId: 3, action: 'REPUBLICATION_VERIFICATION',
        relaunchCount: 0 },
      { kind: 'REPUBLICATION', state: 'WAITING', waitingUntil: 'x', waitingReason: 'REPUBLICATION_DELAY',
        relaunchCount: 0 },
      { kind: 'DELETION', state: 'WAITING', waitingUntil: 'y', waitingReason: 'BROWSER_BUSY', relaunchCount: 0 },
      { kind: 'DELETION', state: 'WORKING', listingId: 4, action: 'DELETION', relaunchCount: 0 },
      { kind: 'VERIFICATION', state: 'STOPPED', relaunchCount: 0 }
    ], [], formatted);
    expect(lines).toEqual([
      'Suppression : revérifie la suppression de annonce 2',
      'Vérification : vérifie la republication de annonce 3',
      'Republication : en attente jusqu\'au le x (délai de republication)',
      'Suppression : en attente jusqu\'au le y (navigateur occupé)',
      'Suppression : supprime annonce 4',
      'Vérification : à l\'arrêt'
    ]);
  });

  it('shows no line for the sequential worker', () => {
    expect(workerLinesOf(sequential(3), [], formatted)).toEqual([]);
  });
});
