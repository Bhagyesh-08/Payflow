import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { PaymentService } from '../../core/services/payment.service';
import { RefundService } from '../../core/services/refund.service';
import { ConfigService } from '../../core/services/config.service';
import { Payment, PaymentStatus } from '../../core/models/payment.model';
import { Refund, CreateRefundRequest } from '../../core/models/refund.model';
import { Page } from '../../core/models/page.model';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';

type SearchType = 'id' | 'paymentId';

@Component({
  selector: 'app-payments',
  standalone: true,
  imports: [CommonModule, FormsModule, LoadingSpinnerComponent, EmptyStateComponent, StatusBadgeComponent],
  templateUrl: './payments.component.html',
  styleUrls: ['./payments.component.css']
})
export class PaymentsComponent implements OnInit {
  private readonly paymentService = inject(PaymentService);
  private readonly refundService = inject(RefundService);
  private readonly configService = inject(ConfigService);
  private readonly router = inject(Router);

  // List view state
  loading = false;
  error: string | null = null;
  payments: Payment[] = [];
  page: Page<Payment> | null = null;
  currentPage = 0;
  pageSize = 20;

  // Search state
  searchType: SearchType = 'paymentId';
  searchValue = '';
  searching = false;
  searchError: string | null = null;

  // Detail view state
  selectedPayment: Payment | null = null;
  detailLoading = false;
  detailError: string | null = null;
  actionInProgress = false;

  // Refund state
  refunds: Refund[] = [];
  refundsLoading = false;
  refundsError: string | null = null;
  showRefundForm = false;
  refundAmount = '';
  refundReason = '';
  refundSubmitting = false;
  refundIdempotencyKey: string | null = null;

  ngOnInit() {
    if (!this.configService.isConfigured) {
      this.router.navigate(['/settings']);
      return;
    }
    this.loadPayments();
  }

  loadPayments() {
    this.loading = true;
    this.error = null;

    this.paymentService.getPayments(this.currentPage, this.pageSize).subscribe({
      next: (page) => {
        this.page = page;
        this.payments = page.content;
        this.loading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error = this.formatError(err);
      }
    });
  }

  searchPayment() {
    if (!this.searchValue.trim()) {
      return;
    }

    this.searching = true;
    this.searchError = null;
    this.selectedPayment = null;

    const observable = this.searchType === 'id'
      ? this.paymentService.getPaymentById(Number(this.searchValue))
      : this.paymentService.getPaymentByPaymentId(this.searchValue);

    observable.subscribe({
      next: (payment) => {
        this.selectedPayment = payment;
        this.searching = false;
        this.loadRefunds(payment.id);
      },
      error: (err: HttpErrorResponse) => {
        this.searching = false;
        if (err.status === 404) {
          this.searchError = `Payment not found with ${this.searchType === 'id' ? 'database ID' : 'payment ID'}: ${this.searchValue}`;
        } else {
          this.searchError = this.formatError(err);
        }
      }
    });
  }

  selectPaymentFromList(payment: Payment) {
    this.selectedPayment = payment;
    this.searchValue = payment.paymentId;
    this.searchType = 'paymentId';
    this.loadRefunds(payment.id);
  }

  loadRefunds(paymentId: number) {
    this.refundsLoading = true;
    this.refundsError = null;
    this.refunds = [];

    this.refundService.getPaymentRefunds(paymentId).subscribe({
      next: (refunds) => {
        this.refunds = refunds;
        this.refundsLoading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.refundsLoading = false;
        this.refundsError = this.formatError(err);
      }
    });
  }

  canInitiate(payment: Payment): boolean {
    return payment.status === 'CREATED';
  }

  canAuthorize(payment: Payment): boolean {
    return payment.status === 'INITIATED';
  }

  canCapture(payment: Payment): boolean {
    return payment.status === 'AUTHORIZED';
  }

  canRefund(payment: Payment): boolean {
    return payment.status === 'CAPTURED' || payment.status === 'PARTIALLY_REFUNDED';
  }

  initiatePayment() {
    if (!this.selectedPayment || !confirm('Initiate this payment with the gateway?')) {
      return;
    }

    this.actionInProgress = true;
    this.detailError = null;

    this.paymentService.initiatePayment(this.selectedPayment.id).subscribe({
      next: (payment) => {
        this.selectedPayment = payment;
        this.actionInProgress = false;
      },
      error: (err: HttpErrorResponse) => {
        this.actionInProgress = false;
        this.detailError = this.formatError(err);
      }
    });
  }

  authorizePayment() {
    if (!this.selectedPayment || !confirm('Authorize this payment?')) {
      return;
    }

    this.actionInProgress = true;
    this.detailError = null;

    this.paymentService.authorizePayment(this.selectedPayment.id).subscribe({
      next: (payment) => {
        this.selectedPayment = payment;
        this.actionInProgress = false;
      },
      error: (err: HttpErrorResponse) => {
        this.actionInProgress = false;
        this.detailError = this.formatError(err);
      }
    });
  }

  capturePayment() {
    if (!this.selectedPayment || !confirm('Capture this payment?')) {
      return;
    }

    this.actionInProgress = true;
    this.detailError = null;

    this.paymentService.capturePayment(this.selectedPayment.id).subscribe({
      next: (payment) => {
        this.selectedPayment = payment;
        this.actionInProgress = false;
        this.loadRefunds(payment.id);
      },
      error: (err: HttpErrorResponse) => {
        this.actionInProgress = false;
        this.detailError = this.formatError(err);
      }
    });
  }

  openRefundForm() {
    if (!this.selectedPayment) return;
    
    this.showRefundForm = true;
    this.refundAmount = this.selectedPayment.amount;
    this.refundReason = '';
    this.refundIdempotencyKey = crypto.randomUUID();
  }

  closeRefundForm() {
    this.showRefundForm = false;
    this.refundAmount = '';
    this.refundReason = '';
    this.refundIdempotencyKey = null;
  }

  submitRefund() {
    if (!this.selectedPayment || !this.refundIdempotencyKey) return;

    const amount = parseFloat(this.refundAmount);
    if (isNaN(amount) || amount <= 0) {
      alert('Please enter a valid positive amount');
      return;
    }

    const paymentAmount = parseFloat(this.selectedPayment.amount);
    if (amount > paymentAmount) {
      alert(`Refund amount cannot exceed payment amount: ${this.selectedPayment.amount} ${this.selectedPayment.currency}`);
      return;
    }

    if (!confirm(`Create refund of ${amount} ${this.selectedPayment.currency}?`)) {
      return;
    }

    const request: CreateRefundRequest = {
      amount: amount.toString(),
      currency: this.selectedPayment.currency,
      reason: this.refundReason || undefined
    };

    this.refundSubmitting = true;

    this.refundService.createRefund(this.selectedPayment.id, request, this.refundIdempotencyKey).subscribe({
      next: (refund) => {
        this.refundSubmitting = false;
        this.closeRefundForm();
        this.loadRefunds(this.selectedPayment!.id);
        // Refresh payment to get updated status
        this.refreshPaymentDetails();
      },
      error: (err: HttpErrorResponse) => {
        this.refundSubmitting = false;
        alert(this.formatError(err));
      }
    });
  }

  retryRefund() {
    // Retry with same idempotency key
    this.submitRefund();
  }

  refreshPaymentDetails() {
    if (!this.selectedPayment) return;

    this.detailLoading = true;
    this.paymentService.getPaymentById(this.selectedPayment.id).subscribe({
      next: (payment) => {
        this.selectedPayment = payment;
        this.detailLoading = false;
      },
      error: () => {
        this.detailLoading = false;
      }
    });
  }

  nextPage() {
    if (this.page && !this.page.last) {
      this.currentPage++;
      this.loadPayments();
    }
  }

  previousPage() {
    if (this.page && !this.page.first) {
      this.currentPage--;
      this.loadPayments();
    }
  }

  clearSelection() {
    this.selectedPayment = null;
    this.refunds = [];
    this.searchValue = '';
    this.searchError = null;
    this.detailError = null;
  }

  getStatusType(status: PaymentStatus): 'success' | 'warning' | 'error' | 'info' | 'neutral' {
    const map: Record<PaymentStatus, 'success' | 'warning' | 'error' | 'info' | 'neutral'> = {
      'CREATED': 'neutral',
      'INITIATED': 'info',
      'AUTHORIZED': 'warning',
      'CAPTURED': 'success',
      'FAILED': 'error',
      'REFUNDED': 'neutral',
      'PARTIALLY_REFUNDED': 'warning'
    };
    return map[status] || 'neutral';
  }

  getRefundStatusType(status: string): 'success' | 'warning' | 'error' | 'info' | 'neutral' {
    const map: Record<string, 'success' | 'warning' | 'error' | 'info' | 'neutral'> = {
      'REQUESTED': 'neutral',
      'PROCESSING': 'info',
      'SUCCEEDED': 'success',
      'FAILED': 'error'
    };
    return map[status] || 'neutral';
  }

  private formatError(err: HttpErrorResponse): string {
    if (err.status === 401 || err.status === 403) {
      return 'Authentication failed. Please check your API key in Settings.';
    } else if (err.status === 0) {
      return 'Cannot connect to backend. Please ensure the server is running.';
    } else {
      return err.error?.message || 'An error occurred';
    }
  }
}
