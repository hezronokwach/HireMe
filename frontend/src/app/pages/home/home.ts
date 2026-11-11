import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

/** The signed-in landing page. */
@Component({
  selector: 'app-home',
  imports: [MatCardModule, MatButtonModule, RouterLink],
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
        <mat-card-actions>
          @if (session.role === 'CLIENT') {
            <a mat-flat-button routerLink="/equipment">Find equipment</a>
            <a mat-button routerLink="/my-bookings">My bookings</a>
          } @else {
            <a mat-flat-button routerLink="/my-listings">My listings</a>
            <a mat-button routerLink="/owner-bookings">Bookings on my equipment</a>
          }
        </mat-card-actions>
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
