import type { BackupFilter, InventorySort, PresenceFilter, SortDirection } from '../../generated/api';

export interface InventoryChoices {
  presence: string;
  backup: string;
  search: string;
  sort: string;
  direction: string;
  tag: string;
}

export interface InventoryCriteria {
  presence?: PresenceFilter;
  backup?: BackupFilter;
  search?: string;
  sort?: InventorySort;
  direction?: SortDirection;
  tag?: string;
}

// Un critère vide n'est pas envoyé : l'API prend alors sa valeur par défaut.
export function inventoryCriteriaOf(choices: InventoryChoices): InventoryCriteria {
  const given = (value: string) => value.trim() || undefined;
  return {
    presence: given(choices.presence) as PresenceFilter | undefined,
    backup: given(choices.backup) as BackupFilter | undefined,
    search: given(choices.search),
    sort: given(choices.sort) as InventorySort | undefined,
    direction: given(choices.direction) as SortDirection | undefined,
    tag: given(choices.tag)
  };
}
