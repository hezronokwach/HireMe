import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideApi } from '../../api';
import { AuthService, SESSION_STORAGE_KEY, Session, emailFromToken } from './auth.service';
import { fakeJwt } from './testing';

describe('AuthService', () => {
  let http: HttpTestingController | undefined;
  let navigate: ReturnType<typeof vi.spyOn>;

  function setUp(): AuthService {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideApi(''),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    return TestBed.inject(AuthService);
  }

  function stored(session: Partial<Session>): void {
    localStorage.setItem(
      SESSION_STORAGE_KEY,
      JSON.stringify({
        token: 't',
        userId: 1,
        email: 'a@b.co',
        role: 'OWNER',
        expiresAt: Date.now() + 60_000,
        ...session,
      }),
    );
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => {
    http?.verify();
    vi.useRealTimers();
  });

  it('logs in, keeps the session and takes the email from the token', () => {
    const auth = setUp();
    let result: Session | undefined;
    auth.login('jane@example.com', 'secret123').subscribe((s) => (result = s));

    const request = http!.expectOne('/api/v1/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'jane@example.com', password: 'secret123' });
    request.flush({
      token: fakeJwt('Jane@Example.com'),
      userId: 7,
      role: 'OWNER',
      expiresAt: '2099-01-01T00:00:00Z',
    });

    expect(result?.email).toBe('Jane@Example.com');
    expect(auth.isLoggedIn()).toBe(true);
    expect(auth.hasRole('OWNER', 'ADMIN')).toBe(true);
    expect(auth.hasRole('CLIENT')).toBe(false);
    expect(JSON.parse(localStorage.getItem(SESSION_STORAGE_KEY) ?? '{}').userId).toBe(7);
  });

  it('a failed login leaves the user signed out', () => {
    const auth = setUp();
    let failed = false;
    auth.login('jane@example.com', 'wrong').subscribe({ error: () => (failed = true) });

    http!
      .expectOne('/api/v1/auth/login')
      .flush({ message: 'Invalid email or password' }, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBe(true);
    expect(auth.isLoggedIn()).toBe(false);
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('registers, then logs in with the new account', () => {
    const auth = setUp();
    auth
      .register({
        fullName: 'Jane',
        email: 'jane@example.com',
        phoneNumber: '0712345678',
        password: 'secret123',
        role: 'CLIENT' as never,
      })
      .subscribe();

    http!
      .expectOne('/api/v1/auth/register')
      .flush({ id: 7, email: 'jane@example.com', role: 'CLIENT' });
    const login = http!.expectOne('/api/v1/auth/login');
    expect(login.request.body).toEqual({ email: 'jane@example.com', password: 'secret123' });
    login.flush({
      token: fakeJwt('jane@example.com'),
      userId: 7,
      role: 'CLIENT',
      expiresAt: '2099-01-01T00:00:00Z',
    });

    expect(auth.role()).toBe('CLIENT');
  });

  it('restores a stored session on start', () => {
    stored({ email: 'owner@example.com' });
    const auth = setUp();

    expect(auth.currentSession()?.email).toBe('owner@example.com');
    expect(auth.token).toBe('t');
  });

  it('drops an expired stored session', () => {
    stored({ expiresAt: Date.now() - 1 });
    expect(setUp().isLoggedIn()).toBe(false);
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('drops a broken stored session', () => {
    localStorage.setItem(SESSION_STORAGE_KEY, '{not json');
    expect(setUp().isLoggedIn()).toBe(false);
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('logout clears the session and opens the login page', () => {
    stored({});
    const auth = setUp();

    auth.logout('/bookings');

    expect(auth.isLoggedIn()).toBe(false);
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/bookings' } });
  });

  it('signs out by itself when the token expires', () => {
    vi.useFakeTimers();
    stored({ expiresAt: Date.now() + 5_000 });
    const auth = setUp();

    vi.advanceTimersByTime(4_999);
    expect(auth.isLoggedIn()).toBe(true);
    vi.advanceTimersByTime(1);
    expect(auth.isLoggedIn()).toBe(false);
  });

  it('emailFromToken reads the subject and tolerates junk', () => {
    expect(emailFromToken(fakeJwt('a@b.co'))).toBe('a@b.co');
    expect(emailFromToken('nope')).toBeNull();
  });
});
