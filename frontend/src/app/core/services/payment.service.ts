import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Payment } from '../models/payment.model';
import { Page } from '../models/page.model';

@Injectable({
  providedIn: 'root'
})
export class PaymentService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/payments';

  getPayments(
    page: number = 0,
    size: number = 20,
    sortBy: string = 'createdAt',
    direction: 'ASC' | 'DESC' = 'DESC'
  ): Observable<Page<Payment>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sortBy', sortBy)
      .set('direction', direction);

    return this.http.get<Page<Payment>>(this.baseUrl, { params });
  }

  getPaymentById(id: number): Observable<Payment> {
    return this.http.get<Payment>(`${this.baseUrl}/${id}`);
  }

  getPaymentByPaymentId(paymentId: string): Observable<Payment> {
    return this.http.get<Payment>(`${this.baseUrl}/payment-id/${paymentId}`);
  }

  initiatePayment(id: number): Observable<Payment> {
    return this.http.post<Payment>(`${this.baseUrl}/${id}/initiate`, {});
  }

  authorizePayment(id: number): Observable<Payment> {
    return this.http.post<Payment>(`${this.baseUrl}/${id}/authorize`, {});
  }

  capturePayment(id: number): Observable<Payment> {
    return this.http.post<Payment>(`${this.baseUrl}/${id}/capture`, {});
  }
}
