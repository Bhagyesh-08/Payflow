import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { PaymentService } from './payment.service';
import { Payment } from '../models/payment.model';

describe('PaymentService', () => {
  let service: PaymentService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(PaymentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch payments with pagination parameters', () => {
    service.getPayments(0, 20, 'createdAt', 'DESC').subscribe();

    const req = httpMock.expectOne(
      (request) => request.url.includes('/api/v1/payments')
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('sortBy')).toBe('createdAt');
    expect(req.request.params.get('direction')).toBe('DESC');
    req.flush({ content: [], totalElements: 0 });
  });

  it('should fetch payment by database ID', () => {
    service.getPaymentById(123).subscribe();

    const req = httpMock.expectOne('/api/v1/payments/123');
    expect(req.request.method).toBe('GET');
    req.flush({} as Payment);
  });

  it('should fetch payment by payment ID', () => {
    service.getPaymentByPaymentId('PAY_ABC').subscribe();

    const req = httpMock.expectOne('/api/v1/payments/payment-id/PAY_ABC');
    expect(req.request.method).toBe('GET');
    req.flush({} as Payment);
  });

  it('should initiate payment', () => {
    service.initiatePayment(123).subscribe();

    const req = httpMock.expectOne('/api/v1/payments/123/initiate');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({} as Payment);
  });

  it('should authorize payment', () => {
    service.authorizePayment(123).subscribe();

    const req = httpMock.expectOne('/api/v1/payments/123/authorize');
    expect(req.request.method).toBe('POST');
    req.flush({} as Payment);
  });

  it('should capture payment', () => {
    service.capturePayment(123).subscribe();

    const req = httpMock.expectOne('/api/v1/payments/123/capture');
    expect(req.request.method).toBe('POST');
    req.flush({} as Payment);
  });
});
