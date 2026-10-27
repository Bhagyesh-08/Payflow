import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ConfigService } from '../../../core/services/config.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <header class="header">
      <div class="header-left">
        <span class="header-title">{{ pageTitle }}</span>
      </div>
      
      <div class="header-right">
        @if (configService.isConfigured) {
          <div class="merchant-badge">
            <span class="badge-label">Merchant</span>
            <span class="badge-value">{{ configService.config?.merchantId }}</span>
          </div>
          <div class="status-indicator">
            <span class="status-dot"></span>
            <span class="status-text">Mock Gateway</span>
          </div>
        } @else {
          <a routerLink="/settings" class="config-warning">
            ⚠️ Configuration Required
          </a>
        }
      </div>
    </header>
  `,
  styles: [`
    .header {
      height: 60px;
      background-color: var(--color-bg-secondary);
      border-bottom: 1px solid var(--color-border);
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 var(--spacing-lg);
    }

    .header-title {
      font-size: var(--font-size-lg);
      font-weight: 600;
      color: var(--color-text-primary);
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: var(--spacing-lg);
    }

    .merchant-badge {
      display: flex;
      align-items: center;
      gap: var(--spacing-sm);
      padding: var(--spacing-sm) var(--spacing-md);
      background-color: var(--color-bg-tertiary);
      border-radius: var(--radius-md);
      font-size: var(--font-size-sm);
    }

    .badge-label {
      color: var(--color-text-muted);
    }

    .badge-value {
      color: var(--color-text-primary);
      font-weight: 600;
    }

    .status-indicator {
      display: flex;
      align-items: center;
      gap: var(--spacing-sm);
      font-size: var(--font-size-sm);
      color: var(--color-text-secondary);
    }

    .status-dot {
      width: 8px;
      height: 8px;
      background-color: var(--color-warning);
      border-radius: 50%;
    }

    .config-warning {
      color: var(--color-warning);
      font-size: var(--font-size-sm);
      font-weight: 500;
      padding: var(--spacing-sm) var(--spacing-md);
      background-color: rgba(245, 158, 11, 0.1);
      border-radius: var(--radius-md);
    }
  `]
})
export class HeaderComponent {
  readonly configService = inject(ConfigService);
  pageTitle = 'PayFlow Operations';
}
