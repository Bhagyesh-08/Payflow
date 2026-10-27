import { Routes } from '@angular/router';
import { LayoutComponent } from './shared/components/layout/layout.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { PaymentsComponent } from './features/payments/payments.component';
import { RefundsComponent } from './features/refunds/refunds.component';
import { DisputesComponent } from './features/disputes/disputes.component';
import { ReconciliationComponent } from './features/reconciliation/reconciliation.component';
import { LedgerComponent } from './features/ledger/ledger.component';
import { SettingsComponent } from './features/settings/settings.component';
import { Component } from '@angular/core';

@Component({
  selector: 'app-not-found',
  standalone: true,
  template: `
    <div style="text-align:center;padding:4rem;max-width:600px;margin:0 auto">
      <h1 style="font-size:4rem;margin-bottom:1rem;color:var(--color-text-primary)">404</h1>
      <h2 style="margin-bottom:1rem;color:var(--color-text-primary)">Page Not Found</h2>
      <p style="color:var(--color-text-muted);margin-bottom:2rem">The page you're looking for doesn't exist.</p>
      <a href="/" style="padding:0.75rem 1.5rem;background-color:var(--color-primary);color:white;border-radius:0.5rem;text-decoration:none;font-weight:600">Go to Dashboard</a>
    </div>
  `
})
class NotFoundComponent {}

export const routes: Routes = [
  {
    path: '',
    component: LayoutComponent,
    children: [
      { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: DashboardComponent },
      { path: 'payments', component: PaymentsComponent },
      { path: 'refunds', component: RefundsComponent },
      { path: 'disputes', component: DisputesComponent },
      { path: 'reconciliation', component: ReconciliationComponent },
      { path: 'ledger', component: LedgerComponent },
      { path: 'settings', component: SettingsComponent },
      { path: '**', component: NotFoundComponent }
    ]
  }
];
