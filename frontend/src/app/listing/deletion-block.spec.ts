import { describe, expect, it } from 'vitest';
import { deletionBlockOf, DeletionFacts } from './deletion-block';

const deletable: DeletionFacts = { sold: false, backedUp: true, unconfirmed: false, validated: true, verified: true };

describe('deletion block', () => {
 it('lets a validated and verified backup be deleted', () => {
  expect(deletionBlockOf(deletable)).toBe('');
 });

 it('says why the deletion is blocked, first reason first', () => {
  expect(deletionBlockOf({ ...deletable, sold: true, backedUp: false })).toBe('Annonce vendue : rien à supprimer.');
  expect(deletionBlockOf({ ...deletable, backedUp: false, unconfirmed: true }))
   .toBe('Aucune sauvegarde : sauvegarder d\'abord.');
  expect(deletionBlockOf({ ...deletable, unconfirmed: true, validated: false }))
   .toBe('Suppression non confirmée : revérifier sur Vinted.');
  expect(deletionBlockOf({ ...deletable, validated: false, verified: false }))
   .toBe('Sauvegarde non validée : valider d\'abord.');
  expect(deletionBlockOf({ ...deletable, verified: false })).toBe('Vérification de la sauvegarde en échec.');
 });
});
