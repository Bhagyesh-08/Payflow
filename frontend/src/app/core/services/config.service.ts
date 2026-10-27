import { Injectable, signal } from '@angular/core';

/**
 * Configuration service for merchant credentials.
 * 
 * Security Notes:
 * - Credentials stored in memory only (session-scoped)
 * - SessionStorage used as fallback for page reloads
 * - NOT secure against XSS - treat as development convenience only
 * - Production deployments should use proper auth flow
 */
@Injectable({
  providedIn: 'root'
})
export class ConfigService {
  private readonly STORAGE_KEY = 'payflow_config';
  
  private readonly configSignal = signal<MerchantConfig | null>(this.loadFromStorage());

  get config() {
    return this.configSignal();
  }

  get isConfigured(): boolean {
    const config = this.configSignal();
    return !!(config?.merchantId && config?.apiKey);
  }

  setConfig(merchantId: number, apiKey: string): void {
    const config: MerchantConfig = { merchantId, apiKey };
    this.configSignal.set(config);
    this.saveToStorage(config);
  }

  clearConfig(): void {
    this.configSignal.set(null);
    sessionStorage.removeItem(this.STORAGE_KEY);
  }

  hasApiKey(): boolean {
    return !!this.configSignal()?.apiKey;
  }

  private loadFromStorage(): MerchantConfig | null {
    try {
      const stored = sessionStorage.getItem(this.STORAGE_KEY);
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  }

  private saveToStorage(config: MerchantConfig): void {
    try {
      sessionStorage.setItem(this.STORAGE_KEY, JSON.stringify(config));
    } catch (error) {
      console.warn('Failed to persist config:', error);
    }
  }
}

export interface MerchantConfig {
  merchantId: number;
  apiKey: string;
}
