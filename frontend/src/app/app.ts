import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { DemoGuide } from './features/demo/demo-guide';

export interface NavLink {
  path: string;
  label: string;
  /** For the phone dock, where space is tight. */
  short: string;
  icon: string;
}

const EXPLORE: NavLink = {
  path: '/equipment',
  label: 'Find equipment',
  short: 'Explore',
  icon: 'search',
};

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatButtonModule, MatIconModule, DemoGuide],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly roleLabels: Record<string, string> = {
    CLIENT: 'Client',
    OWNER: 'Owner',
    ADMIN: 'Admin',
  };

  /** What the signed-in user can reach: everyone browses; clients book; owners list and manage. */
  protected readonly links = computed<NavLink[]>(() => {
    const links = [EXPLORE];
    if (this.auth.hasRole('CLIENT')) {
      links.push({
        path: '/my-bookings',
        label: 'My bookings',
        short: 'Bookings',
        icon: 'event_available',
      });
    }
    if (this.auth.hasRole('OWNER', 'ADMIN')) {
      links.push(
        { path: '/my-listings', label: 'My listings', short: 'Listings', icon: 'inventory_2' },
        { path: '/owner-bookings', label: 'Bookings', short: 'Requests', icon: 'event_note' },
      );
    }
    return links;
  });

  protected initial(email: string): string {
    return (email.trim()[0] ?? '?').toUpperCase();
  }
}
