import { describe, expect, it } from 'vitest';
import type { BackupVerificationProgress } from '../../generated/api';
import { verificationSummaryOf } from './verification-summary';

function progress(more: Partial<BackupVerificationProgress>): BackupVerificationProgress {
  return { status: 'IDLE', verifiedCount: 0, totalCount: 0, validatedCount: 0, failedCount: 0, ...more };
}

describe('verification summary', () => {
  it('says nothing before any verification', () => {
    expect(verificationSummaryOf(progress({}))).toBe('');
  });

  it('counts the listings verified so far', () => {
    expect(verificationSummaryOf(progress({ status: 'RUNNING', verifiedCount: 3, totalCount: 12 })))
      .toBe('Vérification en cours : 3 / 12');
  });

  it('gives the listings validated and in error at the end', () => {
    expect(verificationSummaryOf(progress({ status: 'FINISHED', validatedCount: 2, failedCount: 0 })))
      .toBe('Vérification terminée : 2 validées, 0 en erreur');
    expect(verificationSummaryOf(progress({ status: 'FINISHED', validatedCount: 1, failedCount: 1 })))
      .toBe('Vérification terminée : 1 validée, 1 en erreur');
  });

  it('says why the verification stopped', () => {
    expect(verificationSummaryOf(progress({ status: 'STOPPED', stopReason: 'SESSION_DISCONNECTED' })))
      .toBe('Vérification arrêtée : session Vinted expirée (connectez-vous, puis relancez)');
    expect(verificationSummaryOf(progress({ status: 'STOPPED' })))
      .toBe('Vérification arrêtée : échec inattendu (voir les logs)');
  });
});
