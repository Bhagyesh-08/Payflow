export interface ReconciliationRun {
  id: number;
  merchantId: number;
  gateway: string;
  businessDate: string;
  startedAt: string;
  completedAt?: string;
  status: string;
  totalRecords?: number;
  matchedRecords?: number;
  discrepancyRecords?: number;
  unresolvedRecords?: number;
  errorMessage?: string;
}

export interface ReconciliationRecord {
  id: number;
  runId: number;
  gateway: string;
  gatewayReference?: string;
  paymentId?: string;
  internalAmount?: number;
  gatewayAmount?: number;
  currency?: string;
  internalStatus?: string;
  gatewayStatus?: string;
  result?: string;
  discrepancyType?: string;
  reconciledAt?: string;
  notes?: string;
}

export interface StartReconciliationRequest {
  gateway: string;
  merchantId: number;
  businessDate: string;
}
