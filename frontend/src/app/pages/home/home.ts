import { Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { AuthService } from '../../core/auth/auth.service';

/** The signed-in landing page. Browsing, listings and bookings arrive with #15–#17. */
@Component({
  selector: 'app-home',
  imports: [MatCardModule],
  template: `
    @if (auth.currentSession(); as session) {
      <mat-card class="welcome" appearance="outlined">
        <mat-card-header>
          <mat-card-title>Welcome to HireMe</mat-card-title>
          <mat-card-subtitle>Signed in as {{ session.email }}</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          @switch (session.role) {
            @case ('OWNER') {
              <p>List your equipment and manage the bookings clients make for it.</p>
            }
            @case ('ADMIN') {
              <p>You can see and manage every listing and booking.</p>
            }
            @default {
              <p>Find equipment near you and book it for the days you need.</p>
            }
          }
        </mat-card-content>
      </mat-card>
    }
  `,
  styles: `
    .welcome {
      max-width: 640px;
      margin: 0 auto;
    }
  `,
})
export class Home {
  protected readonly auth = inject(AuthService);
}
