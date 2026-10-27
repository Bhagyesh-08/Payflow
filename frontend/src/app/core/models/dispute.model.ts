export interface Dispute {
  disputeId: string;
  paymentId: string;
  merchantId: number;
  amount: number;
  currency: string;
  status: DisputeStatus;
  gateway: string;
  gatewayDisputeId?: string;
  reason?: string;
  evidenceDueAt?: string;
  resolvedAt?: string;
  resolutionReason?: string;
  createdAt: string;
  updatedAt: string;
}

export type DisputeStatus = 
  | 'OPEN'
  | 'EVIDENCE_SUBMITTED'
  | 'WON'
  | 'LOST'
  | 'CLOSED';

export interface CreateDisputeRequest {
  paymentId: number;
  amount: number;
  currency: string;
  reason?: string;
  gatewayDisputeId?: string;
}

export interface SubmitEvidenceRequest {
  evidenceDescription: string;
}
