import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ConfigService } from '../../core/services/config.service';
import { LedgerService } from '../../core/services/ledger.service';
import { LedgerTransaction } from '../../core/models/ledger.model';
import { Page } from '../../core/models/page.model';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { Router } from '@angular/router';

@Component({
  selector: 'app-ledger',
  standalone: true,
  imports: [CommonModule, LoadingSpinnerComponent, EmptyStateComponent],
  templateUrl: './ledger.component.html',
  styleUrls: ['./ledger.component.css']
})
export class LedgerComponent implements OnInit {
  private readonly configService = inject(ConfigService);
  private readonly ledgerService = inject(LedgerService);
  private readonly router = inject(Router);

  loading = false;
  error: string | null = null;
  transactions: LedgerTransaction[] = [];
  page: Page<LedgerTransaction> | null = null;
  expandedRows = new Set<number>();

  currentPage = 0;
  pageSize = 20;

  ngOnInit() {
    if (!this.configService.isConfigured) {
      this.router.navigate(['/settings']);
      return;
    }
    this.loadLedger();
  }

  loadLedger() {
    const merchantId = this.configService.config?.merchantId;
    if (!merchantId) {
      this.error = 'Merchant ID not configured';
      return;
    }

    this.loading = true;
    this.error = null;

    this.ledgerService
      .getLedgerByMerchantId(merchantId, this.currentPage, this.pageSize)
      .subscribe({
        next: (page) => {
          this.page = page;
          this.transactions = page.content;
          this.loading = false;
        },
        error: (err: HttpErrorResponse) => {
          this.loading = false;
          this.handleError(err);
        }
      });
  }

  toggleRow(transactionId: number) {
    if (this.expandedRows.has(transactionId)) {
      this.expandedRows.delete(transactionId);
    } else {
      this.expandedRows.add(transactionId);
    }
  }

  isRowExpanded(transactionId: number): boolean {
    return this.expandedRows.has(transactionId);
  }

  verifyBalance(transaction: LedgerTransaction): { balanced: boolean; message: string } {
    if (!transaction.entries || transaction.entries.length === 0) {
      return { balanced: false, message: 'No entries' };
    }

    let debitSum = 0;
    let creditSum = 0;

    transaction.entries.forEach(entry => {
      const amount = parseFloat(entry.amount);
      if (entry.entryType === 'DEBIT') {
        debitSum += amount;
      } else if (entry.entryType === 'CREDIT') {
        creditSum += amount;
      }
    });

    const balanced = Math.abs(debitSum - creditSum) < 0.01; // Tolerance for floating point
    return {
      balanced,
      message: balanced ? 'Balanced' : `Unbalanced: DR ${debitSum.toFixed(2)} CR ${creditSum.toFixed(2)}`
    };
  }

  nextPage() {
    if (this.page && !this.page.last) {
      this.currentPage++;
      this.loadLedger();
    }
  }

  previousPage() {
    if (this.page && !this.page.first) {
      this.currentPage--;
      this.loadLedger();
    }
  }

  private handleError(err: HttpErrorResponse) {
    if (err.status === 401 || err.status === 403) {
      this.error = 'Authentication failed. Please check your API key in Settings.';
    } else if (err.status === 0) {
      this.error = 'Cannot connect to backend. Please ensure the server is running.';
    } else {
      this.error = err.error?.message || 'An error occurred while loading ledger data';
    }
  }
}
