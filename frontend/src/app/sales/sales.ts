import { Component, inject, signal } from '@angular/core';
import { SalesOverview, SalesService } from '../../generated/api';
import { answerOf } from '../shared/api-answer';
import { listingLink, priceOf } from '../shared/format';
import { saleDateOf } from '../shared/sale';

@Component({
  selector: 'app-sales',
  templateUrl: './sales.html'
})
export class Sales {
  protected readonly overview = signal<SalesOverview | null>(null);
  protected readonly unavailable = signal(false);
  protected readonly saleDateOf = saleDateOf;
  protected readonly priceOf = priceOf;
  protected readonly listingLink = listingLink;
  private readonly sales = inject(SalesService);

  constructor() {
    this.load();
  }

  protected summary(): string {
    const overview = this.overview();
    if (!overview) {
      return '';
    }
    const count = overview.sales.length;
    if (count === 0) {
      return 'Aucune vente constatée.';
    }
    return count + (count === 1 ? ' vente' : ' ventes') + ', total ' + overview.totals.map(priceOf).join(' + ') + '.';
  }

  private async load(): Promise<void> {
    try {
      this.overview.set(await answerOf(this.sales.showSales()));
      this.unavailable.set(false);
    } catch {
      this.unavailable.set(true);
    }
  }
}
