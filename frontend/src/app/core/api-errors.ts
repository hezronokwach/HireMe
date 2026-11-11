import { HttpErrorResponse } from '@angular/common/http';
import { ErrorResponse } from '../api';

/** A message to show for a failed request: the backend's own message when it sent one. */
export function errorMessage(
  error: unknown,
  fallback = 'Something went wrong. Please try again.',
): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Cannot reach the server. Check your connection and try again.';
    }
    const body = error.error as ErrorResponse | null;
    if (body && typeof body.message === 'string' && body.message) {
      return body.message;
    }
  }
  return fallback;
}

/** Per-field messages from a 400 validation error, keyed by request field name. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (error instanceof HttpErrorResponse && error.status === 400) {
    const body = error.error as ErrorResponse | null;
    return body?.errors ?? {};
  }
  return {};
}
