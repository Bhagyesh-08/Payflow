import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfigService } from '../../core/services/config.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="settings-page">
      <h2>Merchant Configuration</h2>
      <p class="settings-subtitle">
        Configure merchant credentials for API access. For local development only.
      </p>

      <div class="settings-card">
        <div class="config-status">
          @if (configService.isConfigured) {
            <div class="status-box status-configured">
              <span class="status-icon">✓</span>
              <div>
                <strong>Configuration Active</strong>
                <p>Merchant ID: {{ configService.config?.merchantId }}</p>
                <p>API Key: {{ maskedApiKey }}</p>
              </div>
            </div>
          } @else {
            <div class="status-box status-missing">
              <span class="status-icon">⚠️</span>
              <div>
                <strong>Configuration Required</strong>
                <p>Enter merchant credentials to access the dashboard</p>
              </div>
            </div>
          }
        </div>

        <form (ngSubmit)="saveConfig()" class="config-form">
          <div class="form-group">
            <label for="merchantId">Merchant ID</label>
            <input
              type="number"
              id="merchantId"
              [(ngModel)]="merchantId"
              name="merchantId"
              class="form-input"
              placeholder="Enter merchant ID"
              required
            />
            <small class="form-help">Development merchant ID: 1</small>
          </div>

          <div class="form-group">
            <label for="apiKey">API Key</label>
            <input
              type="password"
              id="apiKey"
              [(ngModel)]="apiKey"
              name="apiKey"
              class="form-input"
              placeholder="Enter API key"
              required
            />
            <small class="form-help">
              Development key: dev_merchant_key_12345
            </small>
          </div>

          <div class="form-actions">
            <button type="submit" class="btn btn-primary" [disabled]="!isFormValid()">
              Save Configuration
            </button>
            @if (configService.isConfigured) {
              <button type="button" class="btn btn-secondary" (click)="clearConfig()">
                Clear Configuration
              </button>
            }
          </div>
        </form>

        <div class="security-notice">
          <strong>⚠️ Security Notice</strong>
          <p>
            Credentials are stored in browser sessionStorage for development convenience.
            This is NOT secure against XSS attacks. Production deployments must use proper
            authentication flows.
          </p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .settings-page {
      max-width: 800px;
      padding: var(--spacing-lg);
    }

    .settings-page h2 {
      margin-bottom: var(--spacing-sm);
    }

    .settings-subtitle {
      color: var(--color-text-muted);
      margin-bottom: var(--spacing-xl);
    }

    .settings-card {
      background-color: var(--color-bg-secondary);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-lg);
      padding: var(--spacing-xl);
    }

    .config-status {
      margin-bottom: var(--spacing-xl);
    }

    .status-box {
      display: flex;
      align-items: start;
      gap: var(--spacing-md);
      padding: var(--spacing-md);
      border-radius: var(--radius-md);
    }

    .status-configured {
      background-color: rgba(16, 185, 129, 0.1);
      border: 1px solid rgba(16, 185, 129, 0.3);
    }

    .status-missing {
      background-color: rgba(245, 158, 11, 0.1);
      border: 1px solid rgba(245, 158, 11, 0.3);
    }

    .status-icon {
      font-size: 1.5rem;
    }

    .status-box strong {
      display: block;
      margin-bottom: var(--spacing-xs);
    }

    .status-box p {
      font-size: var(--font-size-sm);
      color: var(--color-text-secondary);
      margin: 0;
    }

    .config-form {
      margin-bottom: var(--spacing-xl);
    }

    .form-group {
      margin-bottom: var(--spacing-lg);
    }

    .form-group label {
      display: block;
      font-size: var(--font-size-sm);
      font-weight: 600;
      margin-bottom: var(--spacing-sm);
      color: var(--color-text-primary);
    }

    .form-input {
      width: 100%;
      padding: var(--spacing-sm) var(--spacing-md);
      background-color: var(--color-bg-primary);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      color: var(--color-text-primary);
      font-size: var(--font-size-base);
      transition: border-color 0.2s;
    }

    .form-input:focus {
      outline: none;
      border-color: var(--color-primary);
    }

    .form-help {
      display: block;
      margin-top: var(--spacing-xs);
      font-size: var(--font-size-xs);
      color: var(--color-text-muted);
    }

    .form-actions {
      display: flex;
      gap: var(--spacing-md);
    }

    .btn {
      padding: var(--spacing-sm) var(--spacing-lg);
      border-radius: var(--radius-md);
      font-size: var(--font-size-sm);
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
    }

    .btn-primary {
      background-color: var(--color-primary);
      color: white;
    }

    .btn-primary:hover:not(:disabled) {
      background-color: var(--color-primary-hover);
    }

    .btn-primary:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .btn-secondary {
      background-color: var(--color-bg-tertiary);
      color: var(--color-text-primary);
    }

    .btn-secondary:hover {
      background-color: var(--color-bg-hover);
    }

    .security-notice {
      padding: var(--spacing-md);
      background-color: rgba(245, 158, 11, 0.1);
      border: 1px solid rgba(245, 158, 11, 0.3);
      border-radius: var(--radius-md);
      font-size: var(--font-size-sm);
    }

    .security-notice strong {
      display: block;
      margin-bottom: var(--spacing-xs);
      color: var(--color-warning);
    }

    .security-notice p {
      color: var(--color-text-secondary);
      margin: 0;
      line-height: 1.5;
    }
  `]
})
export class SettingsComponent {
  readonly configService = inject(ConfigService);
  private readonly router = inject(Router);

  merchantId: number | null = null;
  apiKey = '';

  ngOnInit() {
    const config = this.configService.config;
    if (config) {
      this.merchantId = config.merchantId;
    }
  }

  get maskedApiKey(): string {
    const key = this.configService.config?.apiKey || '';
    if (key.length <= 8) return '***';
    return key.substring(0, 4) + '...' + key.substring(key.length - 4);
  }

  isFormValid(): boolean {
    return !!this.merchantId && this.apiKey.length > 0;
  }

  saveConfig() {
    if (!this.isFormValid() || !this.merchantId) return;

    this.configService.setConfig(this.merchantId, this.apiKey);
    alert('Configuration saved successfully');
    this.router.navigate(['/ledger']);
  }

  clearConfig() {
    if (confirm('Are you sure you want to clear the configuration?')) {
      this.configService.clearConfig();
      this.merchantId = null;
      this.apiKey = '';
    }
  }
}
