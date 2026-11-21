import type { RelaunchRefusal, RelaunchReport } from '../../generated/api';
import {
  RELAUNCH_DONE_MESSAGES, RELAUNCH_DRY_RUN_MESSAGE, RELAUNCH_FAILED_MESSAGE, RELAUNCH_REFUSAL_MESSAGES,
  RELAUNCH_UNCERTAIN_MESSAGE, STAGES_TO_CHECK
} from '../wardrobe/wardrobe-messages';

// Relance d'une annonce (US-20) : mêmes messages dans la liste et la page des échecs (US-39).

// Morceau d'un message de relance : texte, ou lien vers la fiche d'une annonce.
export interface MessagePart {
  text: string;
  listingId?: number;
}

// Une action dont l'annonce reste ou devient « à vérifier » n'a pas d'issue prouvée (US-17).
export function relaunchMessageOf(report: RelaunchReport): string {
  if (report.dryRun) {
    return RELAUNCH_DRY_RUN_MESSAGE;
  }
  if (STAGES_TO_CHECK.includes(report.listing.stage)) {
    return RELAUNCH_UNCERTAIN_MESSAGE;
  }
  return RELAUNCH_DONE_MESSAGES[report.action] ?? '';
}

export function refusalMessageOf(listingId: number, refusal: RelaunchRefusal): MessagePart[] {
  const text = RELAUNCH_REFUSAL_MESSAGES[refusal.reason] ?? RELAUNCH_FAILED_MESSAGE;
  if (refusal.reason === 'ACTION_REFUSED') {
    return [{ text: text + ' ' }, { text: "fiche de l'annonce", listingId }];
  }
  return [{ text }];
}
