import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { DisputeService } from './dispute.service';
import { Dispute, CreateDisputeRequest, SubmitEvidenceRequest } from '../models/dispute.model';
import { API_CONFIG } from '../config/api.config';

describe('DisputeService', () => {
  let service: DisputeService;
  let httpMock: HttpTestingController;
  const baseUrl = API_CONFIG.baseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [DisputeService]
    });
    service = TestBed.inject(DisputeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should get merchant disputes', () => {
    const mockDisputes: Dispute[] = [
      {
        disputeId: 'DISP001',
        paymentId: 'PAY001',
        merchantId: 1,
        amount: 100,
        currency: 'USD',
        status: 'OPEN',
        gateway: 'RAZORPAY',
        createdAt: '2024-01-01T00:00:00',
        updatedAt: '2024-01-01T00:00:00'
      }
    ];

    service.getMerchantDisputes(1).subscribe(disputes => {
      expect(disputes).toEqual(mockDisputes);
    });

    const req = httpMock.expectOne(`${baseUrl}/merchants/1/disputes`);
    expect(req.request.method).toBe('GET');
    req.flush(mockDisputes);
  });

  it('should get dispute by ID', () => {
    const mockDispute: Dispute = {
      disputeId: 'DISP001',
      paymentId: 'PAY001',
      merchantId: 1,
      amount: 100,
      currency: 'USD',
      status: 'OPEN',
      gateway: 'RAZORPAY',
      createdAt: '2024-01-01T00:00:00',
      updatedAt: '2024-01-01T00:00:00'
    };

    service.getDisputeById('DISP001').subscribe(dispute => {
      expect(dispute).toEqual(mockDispute);
    });

    const req = httpMock.expectOne(`${baseUrl}/disputes/DISP001`);
    expect(req.request.method).toBe('GET');
    req.flush(mockDispute);
  });

  it('should create dispute', () => {
    const request: CreateDisputeRequest = {
      paymentId: 1,
      amount: 100,
      currency: 'USD',
      reason: 'Test dispute'
    };

    const mockResponse: Dispute = {
      disputeId: 'DISP002',
      paymentId: 'PAY001',
      merchantId: 1,
      amount: 100,
      currency: 'USD',
      status: 'OPEN',
      gateway: 'RAZORPAY',
      reason: 'Test dispute',
      createdAt: '2024-01-01T00:00:00',
      updatedAt: '2024-01-01T00:00:00'
    };

    service.createDispute(request).subscribe(dispute => {
      expect(dispute).toEqual(mockResponse);
    });

    const req = httpMock.expectOne(`${baseUrl}/disputes`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockResponse);
  });

  it('should submit evidence', () => {
    const request: SubmitEvidenceRequest = {
      evidenceDescription: 'Test evidence'
    };

    const mockResponse: Dispute = {
      disputeId: 'DISP001',
      paymentId: 'PAY001',
      merchantId: 1,
      amount: 100,
      currency: 'USD',
      status: 'EVIDENCE_SUBMITTED',
      gateway: 'RAZORPAY',
      createdAt: '2024-01-01T00:00:00',
      updatedAt: '2024-01-01T00:00:00'
    };

    service.submitEvidence('DISP001', request).subscribe(dispute => {
      expect(dispute).toEqual(mockResponse);
    });

    const req = httpMock.expectOne(`${baseUrl}/disputes/DISP001/evidence`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockResponse);
  });
});
