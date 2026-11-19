import type { Listing, RunWorker } from '../../generated/api';

const WORKER_NAMES: Record<string, string> = {
  REPUBLICATION: 'Republication', DELETION: 'Suppression', VERIFICATION: 'Vérification'
};

const ACTION_VERBS: Record<string, string> = {
  DELETION: 'supprime', DELETION_RECHECK: 'revérifie la suppression de', REPUBLICATION: 'republie',
  REPUBLICATION_VERIFICATION: 'vérifie la republication de'
};

const WAITING_REASONS: Record<string, string> = {
  CADENCE: 'cadence', REPUBLICATION_DELAY: 'délai de republication', BROWSER_BUSY: 'navigateur occupé'
};

// Annonce en cours de la série séquentielle (US-19) ; avec les workers (US-46), une ligne par worker la remplace.
export function runningSummaryOf(workers: RunWorker[], listings: Listing[]): string {
  const listingId = workers.length === 1 && workers[0].kind === 'ALL_ACTIONS' ? workers[0].listingId : undefined;
  return listingId === undefined ? 'Traitement en cours.' : `Traitement en cours : ${nameOf(listingId, listings)}.`;
}

// Ce que fait chaque worker (US-46) ; aucune ligne pour la série séquentielle.
export function workerLinesOf(workers: RunWorker[], listings: Listing[], dateOf: (instant: string) => string): string[] {
  return workers.filter(worker => worker.kind !== 'ALL_ACTIONS')
    .map(worker => `${WORKER_NAMES[worker.kind]} : ${doingOf(worker, listings, dateOf)}`);
}

function doingOf(worker: RunWorker, listings: Listing[], dateOf: (instant: string) => string): string {
  switch (worker.state) {
    case 'WORKING':
      return `${ACTION_VERBS[worker.action ?? '']} ${nameOf(worker.listingId ?? 0, listings)}`;
    case 'WAITING':
      return `en attente jusqu'au ${dateOf(worker.waitingUntil ?? '')} (${WAITING_REASONS[worker.waitingReason ?? '']})`;
    case 'IDLE':
      return 'rien à faire';
    default:
      return 'à l\'arrêt';
  }
}

// Titre lu dans la liste affichée, identifiant seul sinon.
function nameOf(listingId: number, listings: Listing[]): string {
  const title = listings.find(listing => listing.vintedId === listingId)?.title;
  return title === undefined ? `annonce ${listingId}` : `${title} (annonce ${listingId})`;
}
