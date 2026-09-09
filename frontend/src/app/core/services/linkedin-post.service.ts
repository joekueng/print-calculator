import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, Observable, of } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface LinkedInPost {
  id: string;
  commentary: string;
  publishedAt: string;
  url: string;
  imageUrl: string | null;
  imageAltText: string | null;
}

@Injectable({ providedIn: 'root' })
export class LinkedInPostService {
  private readonly http = inject(HttpClient);
  private readonly postsUrl = `${environment.apiUrl}/api/public/linkedin/posts`;

  getPosts(): Observable<readonly LinkedInPost[]> {
    return this.http
      .get<LinkedInPost[]>(this.postsUrl)
      .pipe(catchError(() => of([])));
  }
}
