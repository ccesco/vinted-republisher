import { ListingSale, SaleRefusal } from '../../generated/api';
import { dateOf } from './format';

// US-51 : une vente notée par le membre se distingue d'une vente constatée dans le dressing (US-36).
export function saleDateOf(sale: Pick<ListingSale, 'origin' | 'noticedAt'>): string {
  return (sale.origin === 'NOTED' ? 'Notée le ' : 'Constatée le ') + dateOf(sale.noticedAt);
}

export function saleRefusalMessageOf(refusal: SaleRefusal | null, status: number): string {
  if (refusal?.reason === 'ALREADY_SOLD') {
    return 'Cette annonce a déjà une vente.';
  }
  if (refusal?.reason === 'NOT_NOTED') {
    return 'Cette vente a été constatée dans le dressing : elle ne s\'annule pas.';
  }
  return status === 404 ? 'Annonce ou vente introuvable : rechargez la fiche.'
    : 'Application indisponible : rien n\'a changé.';
}
