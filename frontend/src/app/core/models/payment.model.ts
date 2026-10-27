/**
 * Payment domain models matching backend DTOs
 */

export type PaymentStatus = 
  | 'CREATED'
  | 'INITIATED'
  | 'AUTHORIZED'
  | 'CAPTURED'
  | 'FAILED'
  | 'REFUNDED'
  | 'PARTIALLY_REFUNDED';

export interface Payment {
  id: number;
  paymentId: string;
  orderId: number;
  merchantId: number;
  amount: string; // BigDecimal as string
  currency: string;
  status: PaymentStatus;
  gatewayPaymentId: string | null;
  paymentMethod: string | null;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePaymentRequest {
  orderId: number;
  paymentMethod?: string;
}
