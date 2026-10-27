import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { RefundService } from './refund.service';
import { CreateRefundRequest } from '../models/refund.model';

describe('RefundService', () => {
  let service: RefundService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(RefundService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should create refund with Idempotency-Key header', () => {
    const request: CreateRefundRequest = {
      amount: '100.00',
      currency: 'INR',
      reason: 'Customer request'
    };
    const idempotencyKey = 'test-key-123';

    service.createRefund(1, request, idempotencyKey).subscribe();

    const req = httpMock.expectOne('/api/v1/payments/1/refunds');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toBe(idempotencyKey);
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('should fetch refund by refund ID', () => {
    service.getRefundById('REF_ABC').subscribe();

    const req = httpMock.expectOne('/api/v1/refunds/REF_ABC');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('should fetch refunds for payment', () => {
    service.getPaymentRefunds(123).subscribe();

    const req = httpMock.expectOne('/api/v1/payments/123/refunds');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('should use same idempotency key for retry', () => {
    const request: CreateRefundRequest = {
      amount: '100.00',
      currency: 'INR'
    };
    const idempotencyKey = 'retry-key-456';

    // First attempt
    service.createRefund(1, request, idempotencyKey).subscribe({
      error: () => {
        // Retry with same key
        service.createRefund(1, request, idempotencyKey).subscribe();

        const retryReq = httpMock.expectOne('/api/v1/payments/1/refunds');
        expect(retryReq.request.headers.get('Idempotency-Key')).toBe(idempotencyKey);
        retryReq.flush({});
      }
    });

    const req = httpMock.expectOne('/api/v1/payments/1/refunds');
    req.flush(null, { status: 500, statusText: 'Server Error' });
  });
});
