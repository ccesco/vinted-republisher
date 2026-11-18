import { describe, expect, it } from 'vitest';
import { inventoryCriteriaOf } from './inventory-criteria';

describe('inventory criteria', () => {
  it('keep every chosen criterion, search trimmed', () => {
    expect(inventoryCriteriaOf({
      presence: 'ALL', backup: 'VALIDATED', search: '  pull ', sort: 'VIEWS', direction: 'ASC', tag: 'été'
    })).toEqual({
      presence: 'ALL', backup: 'VALIDATED', search: 'pull', sort: 'VIEWS', direction: 'ASC', tag: 'été'
    });
  });

  it('leave out an empty search and the « all tags » choice', () => {
    expect(inventoryCriteriaOf({
      presence: 'PRESENT', backup: 'ALL', search: '   ', sort: 'RECENCY', direction: 'DESC', tag: ''
    })).toEqual({
      presence: 'PRESENT', backup: 'ALL', search: undefined, sort: 'RECENCY', direction: 'DESC', tag: undefined
    });
  });
});
