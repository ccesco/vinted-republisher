import { describe, expect, it } from 'vitest';
import type { ListingBackupView } from '../../generated/api';
import { newListingSearchMessageOf, toLink } from './listing-link';

const deleted = { deletion: { outcome: 'CONFIRMED' } } as ListingBackupView;

describe('listing to link', () => {
  it('is a deleted listing never republished, or checked not published', () => {
    expect(toLink(deleted)).toBe(true);
    expect(toLink({ ...deleted, republication: { outcome: 'NOT_PUBLISHED' } } as ListingBackupView)).toBe(true);
  });

  it('is neither a listing not deleted nor one with a republication in error or published', () => {
    expect(toLink({} as ListingBackupView)).toBe(false);
    expect(toLink({ deletion: { outcome: 'UNCONFIRMED' } } as ListingBackupView)).toBe(false);
    expect(toLink({ ...deleted, republication: { outcome: 'FAILED' } } as ListingBackupView)).toBe(false);
    expect(toLink({ ...deleted, republication: { outcome: 'PUBLISHED' } } as ListingBackupView)).toBe(false);
  });
});

describe('new listing search message', () => {
  it('asks for the link when no new listing or several were found', () => {
    expect(newListingSearchMessageOf('NOT_FOUND'))
      .toBe('Aucune nouvelle annonce trouvée sur Vinted : collez le lien de la nouvelle annonce dans « Publiée »');
    expect(newListingSearchMessageOf('SEVERAL'))
      .toBe('Plusieurs annonces correspondent sur Vinted : collez le lien de la nouvelle annonce dans « Publiée »');
  });
});
