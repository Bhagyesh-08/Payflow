import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  template: `
    <aside class="sidebar">
      <div class="sidebar-brand">
        <h1>PayFlow</h1>
        <span class="brand-subtitle">Operations</span>
      </div>
      
      <nav class="sidebar-nav">
        <a routerLink="/dashboard" routerLinkActive="active" class="nav-item">
          <span class="nav-icon">📊</span>
          <span class="nav-label">Overview</span>
        </a>
        <a routerLink="/payments" routerLinkActive="active" class="nav-item">
          <span class="nav-icon">💳</span>
          <span class="nav-label">Payments</span>
        </a>
        <a routerLink="/refunds" routerLinkActive="active" class="nav-item">
          <span class="nav-icon">↩️</span>
          <span class="nav-label">Refunds</span>
        </a>
        <a routerLink="/disputes" routerLinkActive="active" class="nav-item">
          <span class="nav-icon">⚠️</span>
          <span class="nav-label">Disputes</span>
        </a>
        <a routerLink="/reconciliation" routerLinkActive="active" class="nav-item">
          <span class="nav-icon">🔍</span>
          <span class="nav-label">Reconciliation</span>
        </a>
        <a routerLink="/ledger" routerLinkActive="active" class="nav-item">
          <span class="nav-icon">📒</span>
          <span class="nav-label">Ledger</span>
        </a>
        <a routerLink="/settings" routerLinkActive="active" class="nav-item">
          <span class="nav-icon">⚙️</span>
          <span class="nav-label">Settings</span>
        </a>
      </nav>
    </aside>
  `,
  styles: [`
    .sidebar {
      width: 240px;
      background-color: var(--color-bg-secondary);
      border-right: 1px solid var(--color-border);
      display: flex;
      flex-direction: column;
      overflow-y: auto;
    }

    .sidebar-brand {
      padding: var(--spacing-lg);
      border-bottom: 1px solid var(--color-border);
    }

    .sidebar-brand h1 {
      font-size: var(--font-size-xl);
      color: var(--color-primary);
      margin-bottom: var(--spacing-xs);
    }

    .brand-subtitle {
      font-size: var(--font-size-sm);
      color: var(--color-text-muted);
    }

    .sidebar-nav {
      padding: var(--spacing-md) 0;
    }

    .nav-item {
      display: flex;
      align-items: center;
      padding: var(--spacing-md) var(--spacing-lg);
      color: var(--color-text-secondary);
      transition: all 0.2s;
      border-left: 3px solid transparent;
    }

    .nav-item:hover {
      background-color: var(--color-bg-hover);
      color: var(--color-text-primary);
    }

    .nav-item.active {
      background-color: var(--color-bg-tertiary);
      color: var(--color-text-primary);
      border-left-color: var(--color-primary);
    }

    .nav-icon {
      font-size: 1.25rem;
      margin-right: var(--spacing-md);
      width: 1.5rem;
      text-align: center;
    }

    .nav-label {
      font-size: var(--font-size-sm);
      font-weight: 500;
    }
  `]
})
export class SidebarComponent {}
