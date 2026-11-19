import type { UnfilledField } from '../../generated/api';
import { FORM_FIELDS } from '../listing/listing-messages';

// Republication manuelle (US-45) : le formulaire attend le membre dans Chrome, avec les champs qu'il doit compléter.
export function manualRepublicationMessageOf(unfilled: UnfilledField[], where: string): string {
  const fields = unfilled.map(field => (FORM_FIELDS[field.field] ?? field.field)
    + (field.optionId ? ` (${field.optionId})` : '')).join(', ');
  return 'Formulaire rempli dans Chrome' + (fields ? ', à compléter : ' + fields : '')
    + `. Publiez puis validez ${where}.`;
}
