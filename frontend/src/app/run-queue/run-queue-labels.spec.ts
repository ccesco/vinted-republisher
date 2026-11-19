import { describe, expect, it } from 'vitest';
import type { QueuedAction } from '../../generated/api';
import { queuedActionOf, whenOf } from './run-queue-labels';

function anAction(action: QueuedAction['action']): QueuedAction {
  return { listingId: 3, listingTitle: 'Pull', action, earliestAt: '2026-11-19T10:00:00Z', dryRun: false };
}

describe('queued action', () => {
  it('names each action in plain words', () => {
    expect(queuedActionOf(anAction('DELETION'))).toBe('Suppression');
    expect(queuedActionOf(anAction('DELETION_RECHECK'))).toBe('Revérification de la suppression');
    expect(queuedActionOf(anAction('REPUBLICATION'))).toBe('Republication');
    expect(queuedActionOf(anAction('REPUBLICATION_VERIFICATION'))).toBe('Vérification de la republication');
  });
});

describe('when a queued action goes', () => {
  it('is now when not after the computation', () => {
    expect(whenOf('2026-11-19T10:00:00Z', '2026-11-19T10:00:00Z')).toBe('maintenant');
    expect(whenOf('2026-11-19T09:59:00Z', '2026-11-19T10:00:00Z')).toBe('maintenant');
  });

  it('is its date and time otherwise', () => {
    expect(whenOf('2026-11-19T10:05:00Z', '2026-11-19T10:00:00Z'))
      .toBe(new Date('2026-11-19T10:05:00Z').toLocaleString('fr-FR'));
  });
});
