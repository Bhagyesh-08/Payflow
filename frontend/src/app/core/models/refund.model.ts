/**
 * Refund domain models matching backend DTOs
 */

export type RefundStatus =
  | 'REQUESTED'
  | 'PROCESSING'
  | 'SUCCEEDED'
  | 'FAILED';

export interface Refund {
  id: number;
  refundId: string;
  paymentId: string;
  merchantId: number;
  amount: string; // BigDecimal as string
  currency: string;
  status: RefundStatus;
  gateway: string | null;
  gatewayRefundId: string | null;
  reason: string | null;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRefundRequest {
  amount: string; // BigDecimal as string
  currency: string;
  reason?: string;
}
