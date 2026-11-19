import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./wardrobe/wardrobe').then(m => m.Wardrobe) },
  { path: 'listings/:listingId', loadComponent: () => import('./listing/listing-detail').then(m => m.ListingDetail) },
  { path: 'listings', loadComponent: () => import('./listing/listing-detail').then(m => m.ListingDetail) },
  { path: 'failures', loadComponent: () => import('./failures/failed-listings').then(m => m.FailedListings) },
  { path: 'queue', loadComponent: () => import('./run-queue/run-queue').then(m => m.RunQueue) },
  { path: 'journal', loadComponent: () => import('./journal/action-journal').then(m => m.ActionJournal) },
  { path: 'sales', loadComponent: () => import('./sales/sales').then(m => m.Sales) },
  { path: 'live-preview', loadComponent: () => import('./live-preview/live-preview').then(m => m.LivePreview) },
  { path: 'vinted-session', loadComponent: () => import('./vinted-session/vinted-session').then(m => m.VintedSession) },
  { path: '**', redirectTo: '' }
];
