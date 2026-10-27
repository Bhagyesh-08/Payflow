import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_CONFIG } from '../config/api.config';
import { Page } from '../models/page.model';
import { LedgerTransaction } from '../models/ledger.model';

@Injectable({
  providedIn: 'root'
})
export class LedgerService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = API_CONFIG.baseUrl;

  getLedgerByMerchantId(
    merchantId: number,
    page: number = 0,
    size: number = 20,
    sortBy: string = 'createdAt',
    direction: 'ASC' | 'DESC' = 'DESC'
  ): Observable<Page<LedgerTransaction>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sortBy', sortBy)
      .set('direction', direction);

    const url = `${this.baseUrl}${API_CONFIG.endpoints.ledger.merchants(merchantId)}`;
    
    return this.http.get<Page<LedgerTransaction>>(url, { params });
  }

  getLedgerByPaymentId(paymentId: string): Observable<LedgerTransaction> {
    const url = `${this.baseUrl}${API_CONFIG.endpoints.ledger.payments(paymentId)}`;
    return this.http.get<LedgerTransaction>(url);
  }
}
