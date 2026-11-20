import { describe, expect, it } from 'vitest';
import {
  duplicateConfirmationOf, duplicateOutcomeMessageOf, duplicateRefusalMessageOf, twinsSentenceOf
} from './duplicates';

describe('duplicates (US-47)', () => {
  it('names the twin, or the twins, with a link to each', () => {
    expect(twinsSentenceOf([1])).toEqual([
      { text: 'Même titre et même prix que ' }, { text: "l'annonce 1", href: '#/listings/1' }, { text: '.' }]);
    expect(twinsSentenceOf([1, 3]).map(part => part.text).join(''))
      .toBe("Même titre et même prix que l'annonce 1, l'annonce 3.");
  });

  it('confirms what is deleted, what stays on sale, and that it cannot be undone', () => {
    expect(duplicateConfirmationOf(2, [1], false)).toBe("Supprimer de Vinted l'annonce 2 et l'effacer de l'outil "
      + "(sauvegarde, photos, relevés, tags). L'annonce 1 reste en vente. Irréversible.");
    expect(duplicateConfirmationOf(2, [1, 3], true)).toBe("Supprimer de Vinted l'annonce 2 et l'effacer de l'outil "
      + "(sauvegarde, photos, relevés, tags). Les annonces 1, 3 restent en vente. Irréversible. "
      + 'Dry-run : rien ne sera supprimé.');
  });

  it('tells how the deletion ended', () => {
    expect(duplicateOutcomeMessageOf(2, { outcome: 'DELETED', twinListingId: 1 }))
      .toBe("Doublon 2 supprimé de Vinted et effacé de l'outil ; l'annonce 1 reste en vente.");
    expect(duplicateOutcomeMessageOf(2, { outcome: 'ALREADY_GONE', twinListingId: 1 }))
      .toBe("Doublon 2 déjà absent de Vinted : effacé de l'outil ; l'annonce 1 reste en vente.");
    expect(duplicateOutcomeMessageOf(2, { outcome: 'DRY_RUN_PASSED', twinListingId: 1 }))
      .toBe("Dry-run : rien supprimé. L'annonce 1 et le doublon 2 ont été relus sur Vinted.");
    expect(duplicateOutcomeMessageOf(2, { outcome: 'UNCONFIRMED', twinListingId: 1 }))
      .toBe('Suppression du doublon 2 non confirmée : vérifiez sur Vinted, puis redemandez.');
  });

  it('explains a refusal, with the differences read on Vinted', () => {
    expect(duplicateRefusalMessageOf({ reason: 'TWIN_NOT_CONFIRMED' }, 422))
      .toBe('Doublon non supprimé : aucune jumelle confirmée en vente sur Vinted.');
    expect(duplicateRefusalMessageOf({
      reason: 'LISTING_CHANGED', differences: [
        { kind: 'NOT_ACTIVE' },
        { kind: 'PRICE_CHANGED', savedPrice: { amount: 12, currency: 'EUR' }, readPrice: { amount: 10, currency: 'EUR' } }]
    }, 422)).toBe('Doublon non supprimé : annonce différente sur Vinted (plus en vente ; prix 10,00 € lu, '
      + '12,00 € enregistré).');
    expect(duplicateRefusalMessageOf({ reason: 'CADENCE_LIMIT_REACHED', allowedAt: '2026-11-19T10:00:30Z' }, 409))
      .toMatch(/^Doublon non supprimé : limite de cadence atteinte, possible à partir du /);
  });

  it('says when the listing is unknown or the application does not answer', () => {
    expect(duplicateRefusalMessageOf(null, 404)).toBe("Doublon non supprimé : annonce inconnue de l'outil.");
    expect(duplicateRefusalMessageOf(null, 0)).toBe("L'application ne répond pas : rechargez la page.");
  });
});
