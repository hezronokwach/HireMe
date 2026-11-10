import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map, switchMap } from 'rxjs';
import { AuthResponse, AuthService as AuthApi, RegisterRequest, UserResponse } from '../../api';

export type Role = 'CLIENT' | 'OWNER' | 'ADMIN';

/** What the app knows about the signed-in user. */
export interface Session {
  token: string;
  userId: number;
  email: string;
  role: Role;
  /** Epoch milliseconds. */
  expiresAt: number;
}

export const SESSION_STORAGE_KEY = 'hireme.session';

/**
 * Holds the JWT and who it belongs to. The session is kept in localStorage so a reload stays
 * signed in, and it ends by itself when the token expires.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthApi);
  private readonly router = inject(Router);
  private readonly storage = safeLocalStorage();
  private readonly session = signal<Session | null>(null);
  private expiryTimer?: ReturnType<typeof setTimeout>;

  /** The current session, or null when signed out. */
  readonly currentSession = this.session.asReadonly();
  readonly isLoggedIn = computed(() => this.session() !== null);
  readonly role = computed(() => this.session()?.role ?? null);

  constructor() {
    const stored = this.read();
    if (stored) {
      this.start(stored);
    }
    inject(DestroyRef).onDestroy(() => clearTimeout(this.expiryTimer));
  }

  get token(): string | null {
    return this.session()?.token ?? null;
  }

  hasRole(...roles: Role[]): boolean {
    const role = this.role();
    return role !== null && roles.includes(role);
  }

  login(email: string, password: string): Observable<Session> {
    return this.api.login({ email, password }).pipe(
      map((response) => {
        const session = toSession(response, email);
        this.storage?.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
        this.start(session);
        return session;
      }),
    );
  }

  /** Creates the account, then signs in with it. */
  register(request: RegisterRequest): Observable<Session> {
    return this.api
      .register(request)
      .pipe(
        switchMap((user: UserResponse) =>
          this.login(user.email ?? request.email, request.password),
        ),
      );
  }

  /**
   * Ends the session. With `returnUrl`, the login page sends the user back there afterwards
   * (used when the backend rejects an expired token).
   */
  logout(returnUrl?: string): void {
    clearTimeout(this.expiryTimer);
    this.storage?.removeItem(SESSION_STORAGE_KEY);
    this.session.set(null);
    void this.router.navigate(['/login'], returnUrl ? { queryParams: { returnUrl } } : {});
  }

  private start(session: Session): void {
    clearTimeout(this.expiryTimer);
    this.session.set(session);
    // setTimeout overflows above ~24.8 days; tokens live far less, but stay safe.
    const delay = Math.min(session.expiresAt - Date.now(), 2_147_483_647);
    this.expiryTimer = setTimeout(() => this.logout(this.router.url), delay);
  }

  private read(): Session | null {
    try {
      const raw = this.storage?.getItem(SESSION_STORAGE_KEY);
      if (!raw) {
        return null;
      }
      const session = JSON.parse(raw) as Session;
      if (!session.token || !(session.expiresAt > Date.now())) {
        this.storage?.removeItem(SESSION_STORAGE_KEY);
        return null;
      }
      return session;
    } catch {
      this.storage?.removeItem(SESSION_STORAGE_KEY);
      return null;
    }
  }
}

function toSession(response: AuthResponse, fallbackEmail: string): Session {
  if (!response.token || response.userId == null || !response.role || !response.expiresAt) {
    throw new Error('The login response is missing fields');
  }
  return {
    token: response.token,
    userId: response.userId,
    role: response.role as Role,
    email: emailFromToken(response.token) ?? fallbackEmail,
    expiresAt: Date.parse(response.expiresAt),
  };
}

/** The backend puts the email in the JWT subject. Display only: the token isn't verified here. */
export function emailFromToken(token: string): string | null {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = new TextDecoder().decode(Uint8Array.from(atob(payload), (c) => c.charCodeAt(0)));
    const sub = (JSON.parse(json) as { sub?: unknown }).sub;
    return typeof sub === 'string' ? sub : null;
  } catch {
    return null;
  }
}

function safeLocalStorage(): Storage | null {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}
