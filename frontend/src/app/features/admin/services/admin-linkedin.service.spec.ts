import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { AdminLinkedInService } from './admin-linkedin.service';

describe('AdminLinkedInService', () => {
  let service: AdminLinkedInService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminLinkedInService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads the protected synchronization status', () => {
    service.getStatus().subscribe();

    const request = httpTesting.expectOne(
      `${environment.apiUrl}/api/admin/linkedin/status`,
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBeTrue();
    request.flush({
      enabled: true,
      configured: true,
      updated: false,
      postCount: 3,
      lastSuccessfulSync: '2026-09-10T02:00:00Z',
    });
  });

  it('requests an immediate synchronization', () => {
    service.refresh().subscribe();

    const request = httpTesting.expectOne(
      `${environment.apiUrl}/api/admin/linkedin/refresh`,
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBeTrue();
    request.flush({
      enabled: true,
      configured: true,
      updated: true,
      postCount: 3,
      lastSuccessfulSync: '2026-09-10T02:00:00Z',
    });
  });
});
