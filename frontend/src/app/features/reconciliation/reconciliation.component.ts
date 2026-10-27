import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReconciliationService } from '../../core/services/reconciliation.service';
import { ConfigService } from '../../core/services/config.service';
import { ReconciliationRun, ReconciliationRecord, StartReconciliationRequest } from '../../core/models/reconciliation.model';
import { Page } from '../../core/models/page.model';

@Component({
  selector: 'app-reconciliation',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reconciliation.component.html',
  styleUrl: './reconciliation.component.css'
})
export class ReconciliationComponent implements OnInit {
  activeView: 'runs' | 'discrepancies' = 'runs';

  // Runs
  runs: ReconciliationRun[] = [];
  runsPage = 0;
  runsSize = 20;
  runsTotalPages = 0;
  runsTotalElements = 0;
  runsLoading = false;
  runsError: string | null = null;

  // Run form
  showRunForm = false;
  runGateway = 'RAZORPAY';
  runBusinessDate = '';
  submittingRun = false;
  runFormError: string | null = null;

  // Discrepancies
  discrepancies: ReconciliationRecord[] = [];
  discrepPage = 0;
  discrepSize = 20;
  discrepTotalPages = 0;
  discrepTotalElements = 0;
  discrepLoading = false;
  discrepError: string | null = null;

  constructor(
    private reconciliationService: ReconciliationService,
    private configService: ConfigService
  ) {}

  ngOnInit(): void {
    this.setDefaultBusinessDate();
    this.loadRuns();
  }

  setDefaultBusinessDate(): void {
    const today = new Date();
    today.setDate(today.getDate() - 1); // Default to yesterday
    this.runBusinessDate = today.toISOString().split('T')[0];
  }

  switchView(view: 'runs' | 'discrepancies'): void {
    this.activeView = view;
    if (view === 'runs' && this.runs.length === 0) {
      this.loadRuns();
    } else if (view === 'discrepancies' && this.discrepancies.length === 0) {
      this.loadDiscrepancies();
    }
  }

  loadRuns(): void {
    const config = this.configService.config;
    if (!config) {
      this.runsError = 'Please configure merchant credentials in Settings';
      return;
    }

    this.runsLoading = true;
    this.runsError = null;

    this.reconciliationService.getMerchantRuns(config.merchantId, this.runsPage, this.runsSize).subscribe({
      next: (page: Page<ReconciliationRun>) => {
        this.runs = page.content;
        this.runsTotalPages = page.totalPages;
        this.runsTotalElements = page.totalElements;
        this.runsLoading = false;
      },
      error: (err) => {
        this.runsError = err.error?.message || 'Failed to load reconciliation runs';
        this.runsLoading = false;
      }
    });
  }

  loadDiscrepancies(): void {
    const config = this.configService.config;
    if (!config) {
      this.discrepError = 'Please configure merchant credentials in Settings';
      return;
    }

    this.discrepLoading = true;
    this.discrepError = null;

    this.reconciliationService.getMerchantDiscrepancies(config.merchantId, this.discrepPage, this.discrepSize).subscribe({
      next: (page: Page<ReconciliationRecord>) => {
        this.discrepancies = page.content;
        this.discrepTotalPages = page.totalPages;
        this.discrepTotalElements = page.totalElements;
        this.discrepLoading = false;
      },
      error: (err) => {
        this.discrepError = err.error?.message || 'Failed to load discrepancies';
        this.discrepLoading = false;
      }
    });
  }

  toggleRunForm(): void {
    this.showRunForm = !this.showRunForm;
    this.runFormError = null;
  }

  startReconciliation(): void {
    const config = this.configService.config;
    if (!config) {
      this.runFormError = 'Please configure merchant credentials';
      return;
    }

    if (!this.runGateway || !this.runBusinessDate) {
      this.runFormError = 'Please fill all required fields';
      return;
    }

    const confirmMsg = `Start reconciliation run for ${this.runGateway} on ${this.runBusinessDate}?\n\n⚠️ This uses synthetic provider records, not live settlement data.`;
    if (!confirm(confirmMsg)) {
      return;
    }

    this.submittingRun = true;
    this.runFormError = null;

    const request: StartReconciliationRequest = {
      gateway: this.runGateway,
      merchantId: config.merchantId,
      businessDate: this.runBusinessDate
    };

    this.reconciliationService.startRun(request).subscribe({
      next: () => {
        this.submittingRun = false;
        this.showRunForm = false;
        this.runsPage = 0;
        this.loadRuns();
      },
      error: (err) => {
        this.runFormError = err.error?.message || 'Failed to start reconciliation';
        this.submittingRun = false;
      }
    });
  }

  prevRunsPage(): void {
    if (this.runsPage > 0) {
      this.runsPage--;
      this.loadRuns();
    }
  }

  nextRunsPage(): void {
    if (this.runsPage < this.runsTotalPages - 1) {
      this.runsPage++;
      this.loadRuns();
    }
  }

  prevDiscrepPage(): void {
    if (this.discrepPage > 0) {
      this.discrepPage--;
      this.loadDiscrepancies();
    }
  }

  nextDiscrepPage(): void {
    if (this.discrepPage < this.discrepTotalPages - 1) {
      this.discrepPage++;
      this.loadDiscrepancies();
    }
  }

  formatDate(dateStr?: string): string {
    if (!dateStr) return 'N/A';
    return new Date(dateStr).toLocaleString();
  }

  formatAmount(amount?: number, currency?: string): string {
    if (amount === undefined || !currency) return 'N/A';
    return `${currency} ${amount.toFixed(2)}`;
  }

  getStatusClass(status: string): string {
    const statusLower = status.toLowerCase();
    if (statusLower.includes('complete')) return 'status-success';
    if (statusLower.includes('running') || statusLower.includes('progress')) return 'status-warning';
    if (statusLower.includes('fail') || statusLower.includes('error')) return 'status-error';
    return 'status-neutral';
  }

  getResultClass(result?: string): string {
    if (!result) return '';
    const resultLower = result.toLowerCase();
    if (resultLower.includes('match')) return 'result-match';
    if (resultLower.includes('discrep')) return 'result-discrep';
    return '';
  }
}
