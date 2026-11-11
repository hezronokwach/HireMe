import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  const auth = { token: null as string | null, logout: vi.fn() };

  beforeEach(() => {
    auth.token = 'abc.def.ghi';
    auth.logout.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('adds the bearer token to backend requests', () => {
    http.get('/api/v1/bookings/my-bookings').subscribe();
    const request = backend.expectOne('/api/v1/bookings/my-bookings');
    expect(request.request.headers.get('Authorization')).toBe('Bearer abc.def.ghi');
    request.flush({});
  });

  it('never sends the token to another host', () => {
    http.get('https://example.com/api/v1/anything').subscribe();
    const request = backend.expectOne('https://example.com/api/v1/anything');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('sends no header when signed out', () => {
    auth.token = null;
    http.get('/api/v1/equipment').subscribe();
    const request = backend.expectOne('/api/v1/equipment');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('signs out on 401 and still passes the error on', () => {
    let status = 0;
    http.get('/api/v1/bookings/my-bookings').subscribe({ error: (e) => (status = e.status) });
    backend
      .expectOne('/api/v1/bookings/my-bookings')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(status).toBe(401);
    expect(auth.logout).toHaveBeenCalledTimes(1);
  });

  it('does not sign out on 403, or on 401 from the login request', () => {
    http.get('/api/v1/equipment/1').subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/equipment/1').flush(null, { status: 403, statusText: 'Forbidden' });
    http.post('/api/v1/auth/login', {}).subscribe({ error: () => undefined });
    backend
      .expectOne('/api/v1/auth/login')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.logout).not.toHaveBeenCalled();
  });
});
