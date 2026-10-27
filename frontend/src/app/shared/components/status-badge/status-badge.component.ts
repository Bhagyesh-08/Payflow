import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';

type StatusType = 'success' | 'warning' | 'error' | 'info' | 'neutral';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span [class]="'status-badge status-' + type">
      {{ label || status }}
    </span>
  `,
  styles: [`
    .status-badge {
      display: inline-block;
      padding: var(--spacing-xs) var(--spacing-sm);
      border-radius: var(--radius-sm);
      font-size: var(--font-size-xs);
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .status-success {
      background-color: rgba(16, 185, 129, 0.1);
      color: var(--color-success);
    }

    .status-warning {
      background-color: rgba(245, 158, 11, 0.1);
      color: var(--color-warning);
    }

    .status-error {
      background-color: rgba(239, 68, 68, 0.1);
      color: var(--color-error);
    }

    .status-info {
      background-color: rgba(59, 130, 246, 0.1);
      color: var(--color-info);
    }

    .status-neutral {
      background-color: var(--color-bg-tertiary);
      color: var(--color-text-secondary);
    }
  `]
})
export class StatusBadgeComponent implements OnChanges {
  @Input() type?: StatusType;
  @Input() label = '';
  @Input() status = '';

  ngOnChanges(): void {
    if (!this.type && this.status) {
      this.type = this.inferType(this.status);
    }
  }

  private inferType(status: string): StatusType {
    const s = status.toLowerCase();
    if (s.includes('complete') || s.includes('captured') || s.includes('won') || s.includes('match')) return 'success';
    if (s.includes('pending') || s.includes('authorized') || s.includes('open') || s.includes('evidence')) return 'warning';
    if (s.includes('fail') || s.includes('lost') || s.includes('declined') || s.includes('cancel')) return 'error';
    if (s.includes('initiated') || s.includes('request')) return 'info';
    return 'neutral';
  }
}
