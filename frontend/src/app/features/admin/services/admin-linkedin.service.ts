import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface AdminLinkedInSyncStatus {
  enabled: boolean;
  configured: boolean;
  updated: boolean;
  postCount: number;
  lastSuccessfulSync: string | null;
}

@Injectable({ providedIn: 'root' })
export class AdminLinkedInService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/admin/linkedin`;

  getStatus(): Observable<AdminLinkedInSyncStatus> {
    return this.http.get<AdminLinkedInSyncStatus>(`${this.baseUrl}/status`, {
      withCredentials: true,
    });
  }

  refresh(): Observable<AdminLinkedInSyncStatus> {
    return this.http.post<AdminLinkedInSyncStatus>(
      `${this.baseUrl}/refresh`,
      {},
      { withCredentials: true },
    );
  }
}
