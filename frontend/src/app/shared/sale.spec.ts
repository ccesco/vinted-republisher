import { describe, expect, it } from 'vitest';
import { saleDateOf, saleRefusalMessageOf } from './sale';

describe('sale (US-51)', () => {
  it('tells a noted sale from a noticed one', () => {
    expect(saleDateOf({ origin: 'NOTED', noticedAt: '2026-11-20T10:00:00Z' }))
      .toBe('Notée le ' + new Date('2026-11-20T10:00:00Z').toLocaleString('fr-FR'));
    expect(saleDateOf({ origin: 'NOTICED', noticedAt: '2026-11-17T10:00:00Z' }))
      .toBe('Constatée le ' + new Date('2026-11-17T10:00:00Z').toLocaleString('fr-FR'));
  });

  it('explains a refusal', () => {
    expect(saleRefusalMessageOf({ reason: 'ALREADY_SOLD' }, 409)).toBe('Cette annonce a déjà une vente.');
    expect(saleRefusalMessageOf({ reason: 'NOT_NOTED' }, 409))
      .toBe('Cette vente a été constatée dans le dressing : elle ne s\'annule pas.');
    expect(saleRefusalMessageOf(null, 404)).toBe('Annonce ou vente introuvable : rechargez la fiche.');
    expect(saleRefusalMessageOf(null, 0)).toBe('Application indisponible : rien n\'a changé.');
  });
});
