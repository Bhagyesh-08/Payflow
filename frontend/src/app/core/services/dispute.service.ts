import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Dispute, CreateDisputeRequest, SubmitEvidenceRequest } from '../models/dispute.model';
import { API_CONFIG } from '../config/api.config';

@Injectable({
  providedIn: 'root'
})
export class DisputeService {
  private readonly baseUrl = API_CONFIG.baseUrl;

  constructor(private http: HttpClient) {}

  getMerchantDisputes(merchantId: number): Observable<Dispute[]> {
    return this.http.get<Dispute[]>(`${this.baseUrl}/merchants/${merchantId}/disputes`);
  }

  getDisputeById(disputeId: string): Observable<Dispute> {
    return this.http.get<Dispute>(`${this.baseUrl}/disputes/${disputeId}`);
  }

  getPaymentDisputes(paymentId: number): Observable<Dispute[]> {
    return this.http.get<Dispute[]>(`${this.baseUrl}/payments/${paymentId}/disputes`);
  }

  createDispute(request: CreateDisputeRequest): Observable<Dispute> {
    return this.http.post<Dispute>(`${this.baseUrl}/disputes`, request);
  }

  submitEvidence(disputeId: string, request: SubmitEvidenceRequest): Observable<Dispute> {
    return this.http.post<Dispute>(`${this.baseUrl}/disputes/${disputeId}/evidence`, request);
  }
}
