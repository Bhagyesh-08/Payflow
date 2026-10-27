import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ReconciliationRun, ReconciliationRecord, StartReconciliationRequest } from '../models/reconciliation.model';
import { Page } from '../models/page.model';
import { API_CONFIG } from '../config/api.config';

@Injectable({
  providedIn: 'root'
})
export class ReconciliationService {
  private readonly baseUrl = `${API_CONFIG.baseUrl}/reconciliation`;

  constructor(private http: HttpClient) {}

  startRun(request: StartReconciliationRequest): Observable<ReconciliationRun> {
    return this.http.post<ReconciliationRun>(`${this.baseUrl}/runs`, request);
  }

  getRunById(id: number): Observable<ReconciliationRun> {
    return this.http.get<ReconciliationRun>(`${this.baseUrl}/runs/${id}`);
  }

  getRunRecords(id: number): Observable<ReconciliationRecord[]> {
    return this.http.get<ReconciliationRecord[]>(`${this.baseUrl}/runs/${id}/records`);
  }

  getMerchantRuns(merchantId: number, page: number = 0, size: number = 20): Observable<Page<ReconciliationRun>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<Page<ReconciliationRun>>(`${this.baseUrl}/merchants/${merchantId}/runs`, { params });
  }

  getMerchantDiscrepancies(merchantId: number, page: number = 0, size: number = 20): Observable<Page<ReconciliationRecord>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<Page<ReconciliationRecord>>(`${this.baseUrl}/merchants/${merchantId}/discrepancies`, { params });
  }
}
