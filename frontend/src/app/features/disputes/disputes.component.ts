import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DisputeService } from '../../core/services/dispute.service';
import { ConfigService } from '../../core/services/config.service';
import { Dispute, SubmitEvidenceRequest } from '../../core/models/dispute.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';

@Component({
  selector: 'app-disputes',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './disputes.component.html',
  styleUrl: './disputes.component.css'
})
export class DisputesComponent implements OnInit {
  disputes: Dispute[] = [];
  selectedDispute: Dispute | null = null;
  loading = false;
  error: string | null = null;

  evidenceDescription = '';
  submittingEvidence = false;
  evidenceError: string | null = null;

  expandedRow: string | null = null;

  constructor(
    private disputeService: DisputeService,
    private configService: ConfigService
  ) {}

  ngOnInit(): void {
    this.loadDisputes();
  }

  loadDisputes(): void {
    const config = this.configService.config;
    if (!config) {
      this.error = 'Please configure merchant credentials in Settings';
      return;
    }

    this.loading = true;
    this.error = null;

    this.disputeService.getMerchantDisputes(config.merchantId).subscribe({
      next: (disputes) => {
        this.disputes = disputes;
        this.loading = false;
      },
      error: (err) => {
        this.error = err.error?.message || 'Failed to load disputes';
        this.loading = false;
      }
    });
  }

  toggleRow(disputeId: string): void {
    this.expandedRow = this.expandedRow === disputeId ? null : disputeId;
    this.evidenceDescription = '';
    this.evidenceError = null;
  }

  submitEvidence(dispute: Dispute): void {
    if (!this.evidenceDescription.trim()) {
      this.evidenceError = 'Evidence description is required';
      return;
    }

    if (!confirm(`Submit evidence for dispute ${dispute.disputeId}?`)) {
      return;
    }

    this.submittingEvidence = true;
    this.evidenceError = null;

    const request: SubmitEvidenceRequest = {
      evidenceDescription: this.evidenceDescription.trim()
    };

    this.disputeService.submitEvidence(dispute.disputeId, request).subscribe({
      next: (updatedDispute) => {
        const index = this.disputes.findIndex(d => d.disputeId === updatedDispute.disputeId);
        if (index >= 0) {
          this.disputes[index] = updatedDispute;
        }
        this.evidenceDescription = '';
        this.submittingEvidence = false;
        this.expandedRow = null;
      },
      error: (err) => {
        this.evidenceError = err.error?.message || 'Failed to submit evidence';
        this.submittingEvidence = false;
      }
    });
  }

  canSubmitEvidence(dispute: Dispute): boolean {
    return dispute.status === 'OPEN';
  }

  formatAmount(amount: number, currency: string): string {
    return `${currency} ${amount.toFixed(2)}`;
  }

  formatDate(dateStr?: string): string {
    if (!dateStr) return 'N/A';
    return new Date(dateStr).toLocaleString();
  }
}
