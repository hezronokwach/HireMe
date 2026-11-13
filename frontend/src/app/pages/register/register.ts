import { Component, inject, input, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { Router, RouterLink } from '@angular/router';
import { RegisterRequestRoleEnum } from '../../api';
import { errorMessage, fieldErrors } from '../../core/api-errors';
import { AuthService } from '../../core/auth/auth.service';
import { safeReturnUrl } from '../return-url';

/** Same rule as the backend: +2547XXXXXXXX or 07XXXXXXXX. */
export const KENYAN_PHONE = /^(?:\+254|0)7\d{8}$/;

/** Accepts the number with spaces or dashes (0712 345 678); they're removed before sending. */
export function normalizePhone(value: string): string {
  return value.replace(/[\s-]/g, '');
}

function kenyanPhone(control: AbstractControl<string>): ValidationErrors | null {
  const value = normalizePhone(control.value ?? '');
  return !value || KENYAN_PHONE.test(value) ? null : { phone: true };
}

type Field = 'fullName' | 'email' | 'phoneNumber' | 'password';

@Component({
  selector: 'app-register',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    MatButtonModule,
  ],
  templateUrl: './register.html',
  styleUrls: ['../auth-form.scss', './register.scss'],
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Where to go once the account exists, e.g. the listing the visitor was about to book. */
  readonly returnUrl = input<string>();

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Messages the backend sent for single fields. */
  protected readonly serverErrors = signal<Partial<Record<Field, string>>>({});

  protected readonly roles = [
    { value: RegisterRequestRoleEnum.CLIENT, label: 'I want to hire equipment' },
    { value: RegisterRequestRoleEnum.OWNER, label: 'I have equipment to rent out' },
  ];

  protected readonly form = inject(NonNullableFormBuilder).group({
    fullName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    phoneNumber: ['', [Validators.required, kenyanPhone]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    role: [RegisterRequestRoleEnum.CLIENT as RegisterRequestRoleEnum, Validators.required],
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.serverErrors.set({});
    const value = this.form.getRawValue();
    this.auth
      .register({
        fullName: value.fullName.trim(),
        email: value.email.trim(),
        phoneNumber: normalizePhone(value.phoneNumber),
        password: value.password,
        role: value.role,
      })
      .subscribe({
        next: () => void this.router.navigateByUrl(safeReturnUrl(this.returnUrl())),
        error: (err: unknown) => {
          const fields = fieldErrors(err);
          this.serverErrors.set(fields);
          this.error.set(
            Object.keys(fields).length
              ? 'Please fix the highlighted fields.'
              : errorMessage(err, 'Could not create the account.'),
          );
          this.submitting.set(false);
        },
      });
  }
}
