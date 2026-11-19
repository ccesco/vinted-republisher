import type { BackupVerificationProgress } from '../../generated/api';

// BackupVerificationStopReason (US-40).
const STOP_MESSAGES: Record<string, string> = {
  SESSION_DISCONNECTED: 'session Vinted expirée (connectez-vous, puis relancez)',
  BROWSER_UNAVAILABLE: 'navigateur indisponible (voir les logs)',
  VINTED_UNREACHABLE: 'Vinted est inaccessible (réseau ou blocage anti-robot) : réessayez plus tard',
  LISTING_UNREADABLE: 'formulaire illisible (page changée ?) : voir les logs',
  VERIFICATION_ERROR: 'échec inattendu (voir les logs)'
};

// Ligne d'état de « Vérifier et valider » ; rien tant qu'aucune vérification n'a eu lieu depuis le démarrage.
export function verificationSummaryOf(progress: BackupVerificationProgress): string {
  switch (progress.status) {
    case 'RUNNING':
      return `Vérification en cours : ${progress.verifiedCount} / ${progress.totalCount}`;
    case 'FINISHED':
      return `Vérification terminée : ${progress.validatedCount} `
        + `validée${progress.validatedCount > 1 ? 's' : ''}, ${progress.failedCount} en erreur`;
    case 'STOPPED':
      return 'Vérification arrêtée : '
        + (STOP_MESSAGES[progress.stopReason ?? ''] ?? STOP_MESSAGES['VERIFICATION_ERROR']);
    default:
      return '';
  }
}
