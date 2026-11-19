import type { QueuedAction } from '../../generated/api';
import { dateOf } from '../shared/format';

// File d'attente de la série (US-41) : libellés des actions prévues et de leur heure au plus tôt.

const QUEUED_ACTIONS: Record<QueuedAction['action'], string> = {
  DELETION: 'Suppression',
  DELETION_RECHECK: 'Revérification de la suppression',
  REPUBLICATION: 'Republication',
  REPUBLICATION_VERIFICATION: 'Vérification de la republication'
};

export function queuedActionOf(queued: QueuedAction): string {
  return QUEUED_ACTIONS[queued.action] ?? queued.action;
}

// Une action qui peut partir dès le calcul de la file part « maintenant ».
export function whenOf(earliestAt: string, computedAt: string): string {
  return new Date(earliestAt) <= new Date(computedAt) ? 'maintenant' : dateOf(earliestAt);
}
