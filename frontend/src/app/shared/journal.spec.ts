import { describe, expect, it } from 'vitest';
import type { JournalEntry } from '../../generated/api';
import { causeOf, journalLineOf } from './journal';

function entry(more: Partial<JournalEntry>): JournalEntry {
  return { entryId: 1, occurredAt: '2026-11-18T10:00:00Z', action: 'BACKUP_VERIFICATION', outcome: 'FAILED', ...more };
}

describe('journal line', () => {
  it('names the verification of a backup and each of its reasons', () => {
    const line = journalLineOf(entry({ reason: 'FIELD_CHANGED:SIZE, PHOTO_MISSING' }));

    expect(line.action).toBe('Vérification de la sauvegarde');
    expect(line.outcome).toBe('en échec');
    expect(line.reason).toBe('champ changé (taille), photo manquante');
  });

  it('keeps an unknown reason as it is', () => {
    expect(journalLineOf(entry({ reason: 'SOMETHING_NEW' })).reason).toBe('SOMETHING_NEW');
  });
});

function anEntry(outcome: JournalEntry['outcome'], reason?: string): JournalEntry {
  return { entryId: 1, occurredAt: '2026-11-18T10:00:00Z', action: 'DELETION_RECHECK', outcome, listingId: 3, reason };
}

describe('cause of a failure', () => {
  it('is the reason in plain words', () => {
    expect(causeOf(anEntry('FAILED', 'SESSION_DISCONNECTED'))).toBe('session non connectée');
  });

  it('says the deletion was not done for a recheck found not done', () => {
    expect(causeOf(anEntry('NOT_DONE'))).toBe('suppression non faite');
  });

  it('keeps an unknown code as it is', () => {
    expect(causeOf(anEntry('REFUSED', 'NEW_CODE'))).toBe('NEW_CODE');
  });
});

describe('proof of a republication (US-44)', () => {
  it('names the verification and the validation of a republication, highlighting a doubt or a refusal', () => {
    const verification = journalLineOf(entry({ action: 'REPUBLICATION_VERIFICATION', outcome: 'IN_DOUBT' }));
    const validation = journalLineOf(entry({
      action: 'REPUBLICATION_VALIDATION', outcome: 'REFUSED', reason: 'NEW_LISTING_LINK_INVALID'
    }));

    expect(verification.action).toBe('Vérification de la republication');
    expect(verification.highlighted).toBe(true);
    expect(validation.action).toBe('Validation de la republication');
    expect(validation.reason).toBe('lien de la nouvelle annonce invalide');
    expect(validation.highlighted).toBe(true);
  });

  it('gives its refusals in plain words', () => {
    expect(journalLineOf(entry({ reason: 'NO_REPUBLICATION_TO_VERIFY' })).reason).toBe('aucune republication à vérifier');
    expect(journalLineOf(entry({ reason: 'NEW_LISTING_MISMATCH' })).reason)
      .toBe('nouvelle annonce différente de la sauvegarde');
  });

  it('says the republication was not done for a validation « non publiée »', () => {
    expect(causeOf({ ...anEntry('NOT_DONE'), action: 'REPUBLICATION_VALIDATION' })).toBe('republication non faite');
  });
});
