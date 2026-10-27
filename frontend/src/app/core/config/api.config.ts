/**
 * API Configuration
 * Uses Angular dev proxy to forward /api to backend
 */
export const API_CONFIG = {
  baseUrl: '/api/v1',
  endpoints: {
    ledger: {
      merchants: (merchantId: number) => `/ledger/merchants/${merchantId}`,
      transactions: (id: number) => `/ledger/transactions/${id}`,
      payments: (paymentId: string) => `/ledger/payments/${paymentId}`
    }
  }
} as const;
