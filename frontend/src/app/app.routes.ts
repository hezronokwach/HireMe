import { Routes } from '@angular/router';
import { guestGuard, roleGuard } from './core/auth/auth.guards';

const ownerOnly = roleGuard('OWNER', 'ADMIN');
const clientOnly = roleGuard('CLIENT', 'ADMIN');

export const routes: Routes = [
  // No landing page: the app opens on the equipment, which anyone can browse. Signing in is only
  // needed to book (clients) or to manage listings (owners).
  { path: '', pathMatch: 'full', redirectTo: 'equipment' },
  {
    path: 'equipment',
    loadComponent: () =>
      import('./features/equipment/equipment-list/equipment-list').then((m) => m.EquipmentList),
    title: 'Find equipment · HireMe',
  },
  {
    path: 'equipment/:id',
    loadComponent: () =>
      import('./features/equipment/equipment-detail/equipment-detail').then(
        (m) => m.EquipmentDetail,
      ),
    title: 'Equipment · HireMe',
  },
  {
    path: 'my-listings',
    canActivate: [ownerOnly],
    loadComponent: () =>
      import('./features/owner/my-listings/my-listings').then((m) => m.MyListings),
    title: 'My listings · HireMe',
  },
  {
    path: 'my-listings/new',
    canActivate: [ownerOnly],
    loadComponent: () =>
      import('./features/owner/listing-form/listing-form').then((m) => m.ListingForm),
    title: 'New listing · HireMe',
  },
  {
    path: 'my-listings/:id/edit',
    canActivate: [ownerOnly],
    loadComponent: () =>
      import('./features/owner/listing-form/listing-form').then((m) => m.ListingForm),
    title: 'Edit listing · HireMe',
  },
  {
    path: 'owner-bookings',
    canActivate: [ownerOnly],
    loadComponent: () =>
      import('./features/owner/owner-bookings/owner-bookings').then((m) => m.OwnerBookings),
    title: 'Bookings on my equipment · HireMe',
  },
  {
    path: 'my-bookings',
    canActivate: [clientOnly],
    loadComponent: () =>
      import('./features/bookings/my-bookings/my-bookings').then((m) => m.MyBookings),
    title: 'My bookings · HireMe',
  },
  {
    path: 'my-bookings/:id',
    canActivate: [clientOnly],
    loadComponent: () =>
      import('./features/bookings/booking-detail/booking-detail').then((m) => m.BookingDetail),
    title: 'Booking · HireMe',
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
