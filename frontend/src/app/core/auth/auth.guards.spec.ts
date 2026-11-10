import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './auth.guards';
import { AuthService, Role } from './auth.service';

describe('auth guards', () => {
  let role: Role | null;

  beforeEach(() => {
    role = null;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            isLoggedIn: () => role !== null,
            hasRole: (...roles: Role[]) => role !== null && roles.includes(role),
          },
        },
      ],
    });
  });

  function run(guard: CanActivateFn, url = '/bookings'): string | true {
    const result = TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    ) as boolean | UrlTree;
    return result === true ? true : TestBed.inject(Router).serializeUrl(result as UrlTree);
  }

  it('authGuard sends signed-out users to login with a return URL', () => {
    expect(run(authGuard)).toBe('/login?returnUrl=%2Fbookings');
    expect(run(authGuard, '/')).toBe('/login');
    role = 'CLIENT';
    expect(run(authGuard)).toBe(true);
  });

  it('guestGuard keeps signed-in users off login and register', () => {
    expect(run(guestGuard)).toBe(true);
    role = 'OWNER';
    expect(run(guestGuard)).toBe('/');
  });

  it('roleGuard allows only the listed roles', () => {
    const ownersOnly = roleGuard('OWNER', 'ADMIN');
    expect(run(ownersOnly, '/listings')).toBe('/login?returnUrl=%2Flistings');
    role = 'CLIENT';
    expect(run(ownersOnly)).toBe('/');
    role = 'OWNER';
    expect(run(ownersOnly)).toBe(true);
    role = 'ADMIN';
    expect(run(ownersOnly)).toBe(true);
  });
});
