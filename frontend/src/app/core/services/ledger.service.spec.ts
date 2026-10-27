import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { LedgerService } from './ledger.service';
import { Page } from '../models/page.model';
import { LedgerTransaction } from '../models/ledger.model';

describe('LedgerService', () => {
  let service: LedgerService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(LedgerService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch ledger transactions with pagination', () => {
    const mockPage: Page<LedgerTransaction> = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 20,
      number: 0,
      first: true,
      last: true,
      empty: true,
      pageable: {
        pageNumber: 0,
        pageSize: 20,
        sort: { sorted: false, unsorted: true, empty: true },
        offset: 0,
        paged: true,
        unpaged: false
      },
      sort: { sorted: false, unsorted: true, empty: true },
      numberOfElements: 0
    };

    service.getLedgerByMerchantId(1, 0, 20).subscribe(page => {
      expect(page).toEqual(mockPage);
    });

    const req = httpMock.expectOne(
      (request) => request.url.includes('/api/v1/ledger/merchants/1')
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    req.flush(mockPage);
  });
});
