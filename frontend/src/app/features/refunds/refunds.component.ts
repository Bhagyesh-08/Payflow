import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-refunds',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="placeholder-page">
      <div class="placeholder-content">
        <span class="placeholder-icon">↩️</span>
        <h2>Refunds</h2>
        <p>Refunds are managed through the Payments page.</p>
        <p>To create or view refunds, search for a payment first.</p>
        <a routerLink="/payments" class="link-button">Go to Payments</a>
      </div>
    </div>
  `,
  styles: [`
    .placeholder-page { display: flex; align-items: center; justify-content: center; min-height: 400px; text-align: center; }
    .placeholder-content { max-width: 500px; padding: var(--spacing-xl); }
    .placeholder-icon { font-size: 4rem; display: block; margin-bottom: var(--spacing-lg); opacity: 0.5; }
    .placeholder-content h2 { margin-bottom: var(--spacing-md); color: var(--color-text-primary); }
    .placeholder-content p { color: var(--color-text-muted); margin-bottom: var(--spacing-md); }
    .link-button {
      display: inline-block;
      margin-top: var(--spacing-md);
      padding: var(--spacing-sm) var(--spacing-lg);
      background-color: var(--color-primary);
      color: white;
      border-radius: var(--radius-md);
      font-size: var(--font-size-sm);
      font-weight: 600;
      text-decoration: none;
      transition: background-color 0.2s;
    }
    .link-button:hover {
      background-color: var(--color-primary-hover);
    }
  `]
})
export class RefundsComponent {}
