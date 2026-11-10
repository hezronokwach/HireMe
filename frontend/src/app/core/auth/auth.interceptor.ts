import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

/** Requests the backend answers with 401 for bad credentials, not for a bad token. */
const LOGIN_PATH = '/api/v1/auth/login';

/**
 * Adds `Authorization: Bearer <token>` to requests for the backend (never to other hosts), and
 * signs the user out when the backend says the token is no longer valid.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!isBackendUrl(request.url)) {
    return next(request);
  }
  const token = auth.token;
  const outgoing = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;
  return next(outgoing).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        token &&
        !request.url.endsWith(LOGIN_PATH)
      ) {
        auth.logout(router.url);
      }
      return throwError(() => error);
    }),
  );
};

export function isBackendUrl(url: string): boolean {
  const base = environment.apiUrl;
  return base ? url.startsWith(base + '/') : url.startsWith('/');
}
