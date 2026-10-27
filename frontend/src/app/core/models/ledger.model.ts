/**
 * Ledger domain models matching backend DTOs
 */

export interface LedgerEntry {
  id: number;
  accountId: number;
  accountCode: string;
  accountType: string;
  entryType: 'DEBIT' | 'CREDIT';
  amount: string; // BigDecimal as string for precision
  currency: string;
  reference: string;
  createdAt: string;
}

export interface LedgerTransaction {
  id: number;
  transactionReference: string;
  paymentId: string;
  merchantId: number;
  transactionType: string;
  currency: string;
  amount: string; // BigDecimal as string
  description: string;
  createdAt: string;
  entries: LedgerEntry[];
}
