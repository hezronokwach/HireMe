import { Component, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService as AuthApi } from '../../api';
import { errorMessage } from '../../core/api-errors';
import { AuthService } from '../../core/auth/auth.service';
import { safeReturnUrl } from '../return-url';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './login.html',
  styleUrls: ['../auth-form.scss', './login.scss'],
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Where to go after logging in (query parameter, set by the guards and the interceptor). */
  readonly returnUrl = input<string>();

  /** Whether this server offers the one-click demo owner (portfolio deployments only). */
  protected readonly demoAvailable = toSignal(
    inject(AuthApi)
      .getDemoStatus()
      .pipe(
        map((status) => !!status.ownerAvailable),
        catchError(() => of(false)),
      ),
    { initialValue: false },
  );
  protected readonly demoSigningIn = signal(false);

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email.trim(), password).subscribe({
      next: () => void this.router.navigateByUrl(safeReturnUrl(this.returnUrl())),
      error: (err: unknown) => {
        this.error.set(errorMessage(err, 'Could not log in. Please try again.'));
        this.submitting.set(false);
      },
    });
  }

  /** Straight into the owner side, on the booking requests the demo client made. */
  protected signInAsDemoOwner(): void {
    if (this.demoSigningIn() || this.submitting()) {
      return;
    }
    this.demoSigningIn.set(true);
    this.error.set(null);
    this.auth.loginAsDemoOwner().subscribe({
      next: () => void this.router.navigateByUrl('/owner-bookings'),
      error: (err: unknown) => {
        this.error.set(errorMessage(err, 'The demo is not available right now.'));
        this.demoSigningIn.set(false);
      },
    });
  }
}
