import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ReconciliationService } from './reconciliation.service';
import { ReconciliationRun, ReconciliationRecord, StartReconciliationRequest } from '../models/reconciliation.model';
import { Page } from '../models/page.model';
import { API_CONFIG } from '../config/api.config';

describe('ReconciliationService', () => {
  let service: ReconciliationService;
  let httpMock: HttpTestingController;
  const baseUrl = `${API_CONFIG.baseUrl}/reconciliation`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ReconciliationService]
    });
    service = TestBed.inject(ReconciliationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should start reconciliation run', () => {
    const request: StartReconciliationRequest = {
      gateway: 'RAZORPAY',
      merchantId: 1,
      businessDate: '2024-01-01'
    };

    const mockResponse: ReconciliationRun = {
      id: 1,
      merchantId: 1,
      gateway: 'RAZORPAY',
      businessDate: '2024-01-01',
      startedAt: '2024-01-01T00:00:00',
      status: 'RUNNING'
    };

    service.startRun(request).subscribe(run => {
      expect(run).toEqual(mockResponse);
    });

    const req = httpMock.expectOne(`${baseUrl}/runs`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockResponse);
  });

  it('should get run by ID', () => {
    const mockRun: ReconciliationRun = {
      id: 1,
      merchantId: 1,
      gateway: 'RAZORPAY',
      businessDate: '2024-01-01',
      startedAt: '2024-01-01T00:00:00',
      status: 'COMPLETED'
    };

    service.getRunById(1).subscribe(run => {
      expect(run).toEqual(mockRun);
    });

    const req = httpMock.expectOne(`${baseUrl}/runs/1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockRun);
  });

  it('should get merchant runs with pagination', () => {
    const mockPage: Page<ReconciliationRun> = {
      content: [],
      pageable: { pageNumber: 0, pageSize: 20, sort: { sorted: false, unsorted: true, empty: true }, offset: 0, paged: true, unpaged: false },
      totalElements: 0,
      totalPages: 0,
      last: true,
      size: 20,
      number: 0,
      sort: { sorted: false, unsorted: true, empty: true },
      numberOfElements: 0,
      first: true,
      empty: true
    };

    service.getMerchantRuns(1, 0, 20).subscribe(page => {
      expect(page).toEqual(mockPage);
    });

    const req = httpMock.expectOne(`${baseUrl}/merchants/1/runs?page=0&size=20`);
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('should get merchant discrepancies with pagination', () => {
    const mockPage: Page<ReconciliationRecord> = {
      content: [],
      pageable: { pageNumber: 0, pageSize: 20, sort: { sorted: false, unsorted: true, empty: true }, offset: 0, paged: true, unpaged: false },
      totalElements: 0,
      totalPages: 0,
      last: true,
      size: 20,
      number: 0,
      sort: { sorted: false, unsorted: true, empty: true },
      numberOfElements: 0,
      first: true,
      empty: true
    };

    service.getMerchantDiscrepancies(1, 0, 20).subscribe(page => {
      expect(page).toEqual(mockPage);
    });

    const req = httpMock.expectOne(`${baseUrl}/merchants/1/discrepancies?page=0&size=20`);
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });
});
