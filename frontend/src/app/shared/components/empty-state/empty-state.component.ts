import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  template: `
    <div class="empty-state">
      <div class="empty-icon">{{ icon }}</div>
      <h3 class="empty-title">{{ title }}</h3>
      <p class="empty-message">{{ message }}</p>
    </div>
  `,
  styles: [`
    .empty-state {
      text-align: center;
      padding: var(--spacing-xl) var(--spacing-md);
      color: var(--color-text-secondary);
    }

    .empty-icon {
      font-size: 3rem;
      margin-bottom: var(--spacing-md);
      opacity: 0.5;
    }

    .empty-title {
      font-size: var(--font-size-lg);
      color: var(--color-text-primary);
      margin-bottom: var(--spacing-sm);
    }

    .empty-message {
      font-size: var(--font-size-sm);
      color: var(--color-text-muted);
    }
  `]
})
export class EmptyStateComponent {
  @Input() icon = '📭';
  @Input() title = 'No data';
  @Input() message = 'There are no records to display';
}
