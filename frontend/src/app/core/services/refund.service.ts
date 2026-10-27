import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Refund, CreateRefundRequest } from '../models/refund.model';

@Injectable({
  providedIn: 'root'
})
export class RefundService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1';

  createRefund(
    paymentId: number,
    request: CreateRefundRequest,
    idempotencyKey: string
  ): Observable<Refund> {
    const headers = new HttpHeaders({
      'Idempotency-Key': idempotencyKey
    });

    return this.http.post<Refund>(
      `${this.baseUrl}/payments/${paymentId}/refunds`,
      request,
      { headers }
    );
  }

  getRefundById(refundId: string): Observable<Refund> {
    return this.http.get<Refund>(`${this.baseUrl}/refunds/${refundId}`);
  }

  getPaymentRefunds(paymentId: number): Observable<Refund[]> {
    return this.http.get<Refund[]>(`${this.baseUrl}/payments/${paymentId}/refunds`);
  }
}
