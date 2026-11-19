import { describe, expect, it } from 'vitest';
import { manualRepublicationMessageOf } from './manual-republication';

describe('manual republication message', () => {
  it('lists the fields left for the member to complete', () => {
    expect(manualRepublicationMessageOf([{ field: 'SIZE', optionId: 588 }, { field: 'COLORS' }], 'ici'))
      .toBe('Formulaire rempli dans Chrome, à compléter : Taille (588), Couleurs. Publiez puis validez ici.');
  });

  it('says the form is fully filled when nothing is left', () => {
    expect(manualRepublicationMessageOf([], 'sur la fiche'))
      .toBe('Formulaire rempli dans Chrome. Publiez puis validez sur la fiche.');
  });
});
