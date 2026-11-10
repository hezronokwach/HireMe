import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { AuthService, Role } from './auth.service';

/** Lets signed-in users through; sends everyone else to /login and back here afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  return inject(AuthService).isLoggedIn() || toLogin(inject(Router), state.url);
};

/** For /login and /register: a signed-in user goes to the home page instead. */
export const guestGuard: CanActivateFn = () => {
  return !inject(AuthService).isLoggedIn() || inject(Router).createUrlTree(['/']);
};

/**
 * Lets through users with one of `roles`. Signed-out users go to /login; signed-in users with
 * another role go to the home page. Use after authGuard or on its own.
 */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return (_route, state) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.isLoggedIn()) {
      return toLogin(router, state.url);
    }
    return auth.hasRole(...roles) || router.createUrlTree(['/']);
  };
}

function toLogin(router: Router, returnUrl: string): UrlTree {
  return router.createUrlTree(['/login'], returnUrl === '/' ? {} : { queryParams: { returnUrl } });
}
