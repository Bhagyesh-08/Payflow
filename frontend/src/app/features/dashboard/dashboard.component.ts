import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ConfigService } from '../../core/services/config.service';
import { LedgerService } from '../../core/services/ledger.service';
import { DisputeService } from '../../core/services/dispute.service';
import { ReconciliationService } from '../../core/services/reconciliation.service';
import { LedgerTransaction } from '../../core/models/ledger.model';
import { Dispute } from '../../core/models/dispute.model';
import { ReconciliationRun } from '../../core/models/reconciliation.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, StatusBadgeComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  merchantId: number | null = null;
  loading = true;
  
  // Widget states
  ledgersLoading = false;
  ledgersError: string | null = null;
  recentLedgers: LedgerTransaction[] = [];

  disputesLoading = false;
  disputesError: string | null = null;
  disputes: Dispute[] = [];

  reconciliationLoading = false;
  reconciliationError: string | null = null;
  latestRun: ReconciliationRun | null = null;

  constructor(
    private configService: ConfigService,
    private ledgerService: LedgerService,
    private disputeService: DisputeService,
    private reconciliationService: ReconciliationService
  ) {}

  ngOnInit(): void {
    const config = this.configService.config;
    if (!config) {
      this.loading = false;
      return;
    }

    this.merchantId = config.merchantId;
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    if (!this.merchantId) return;

    this.loading = true;

    forkJoin({
      ledgers: this.ledgerService.getLedgerByMerchantId(this.merchantId, 0, 5).pipe(
        catchError(err => {
          this.ledgersError = 'Failed to load ledgers';
          return of(null);
        })
      ),
      disputes: this.disputeService.getMerchantDisputes(this.merchantId).pipe(
        catchError(err => {
          this.disputesError = 'Failed to load disputes';
          return of(null);
        })
      ),
      reconciliation: this.reconciliationService.getMerchantRuns(this.merchantId, 0, 1).pipe(
        catchError(err => {
          this.reconciliationError = 'Failed to load reconciliation';
          return of(null);
        })
      )
    }).subscribe({
      next: (results) => {
        if (results.ledgers) {
          this.recentLedgers = results.ledgers.content.slice(0, 5);
        }
        if (results.disputes) {
          this.disputes = results.disputes.slice(0, 5);
        }
        if (results.reconciliation && results.reconciliation.content.length > 0) {
          this.latestRun = results.reconciliation.content[0];
        }
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  refresh(): void {
    this.ledgersError = null;
    this.disputesError = null;
    this.reconciliationError = null;
    this.loadDashboardData();
  }

  formatAmount(amount: string | number, currency: string): string {
    const numAmount = typeof amount === 'string' ? parseFloat(amount) : amount;
    return `${currency} ${numAmount.toFixed(2)}`;
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleString();
  }

  getDisputeStatusCount(status: string): number {
    return this.disputes.filter(d => d.status === status).length;
  }
}
