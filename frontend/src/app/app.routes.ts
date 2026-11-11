import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/home/home').then((m) => m.Home),
    title: 'HireMe',
  },
  {
    path: 'equipment',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/equipment/equipment-list/equipment-list').then((m) => m.EquipmentList),
    title: 'Find equipment · HireMe',
  },
  {
    path: 'equipment/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/equipment/equipment-detail/equipment-detail').then(
        (m) => m.EquipmentDetail,
      ),
    title: 'Equipment · HireMe',
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
    title: 'Log in · HireMe',
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/register/register').then((m) => m.Register),
    title: 'Create account · HireMe',
  },
  { path: '**', redirectTo: '' },
];
