import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { AuthService, Session } from '../../core/auth/auth.service';
import { KENYAN_PHONE, Register, normalizePhone } from './register';

describe('Kenyan phone rule', () => {
  it('accepts 07… and +2547… numbers, with spaces or dashes', () => {
    for (const phone of ['0712345678', '+254712345678', '0712 345 678', '0712-345-678']) {
      expect(KENYAN_PHONE.test(normalizePhone(phone))).toBe(true);
    }
  });

  it('rejects other numbers', () => {
    for (const phone of [
      '712345678',
      '0812345678',
      '+254812345678',
      '07123456789',
      '+1 555 0100',
    ]) {
      expect(KENYAN_PHONE.test(normalizePhone(phone))).toBe(false);
    }
  });
});

describe('Register', () => {
  let fixture: ComponentFixture<Register>;
  let response: Subject<Session>;
  const auth = { register: vi.fn() };
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    response = new Subject<Session>();
    auth.register.mockReset().mockReturnValue(response);
    TestBed.configureTestingModule({
      imports: [Register],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(Register);
    await fixture.whenStable();
  });

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  async function submit(values: Record<string, string>): Promise<void> {
    for (const [name, value] of Object.entries(values)) {
      const input = el().querySelector<HTMLInputElement>(`input[formControlName="${name}"]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    el().querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();
  }

  const valid = {
    fullName: ' Jane Wanjiku ',
    email: 'jane@example.com',
    phoneNumber: '0712 345 678',
    password: 'secret123',
  };

  it('blocks a bad phone number and a short password', async () => {
    await submit({ ...valid, phoneNumber: '0812345678', password: 'short' });
    expect(auth.register).not.toHaveBeenCalled();
    expect(el().textContent).toContain('Use a Kenyan mobile number');
    expect(el().textContent).toContain('Use at least 8 characters');
  });

  it('sends a cleaned-up request with the chosen role, then opens home', async () => {
    const owner = el().querySelectorAll<HTMLInputElement>('mat-radio-button input')[1];
    owner.click();
    await submit(valid);

    expect(auth.register).toHaveBeenCalledWith({
      fullName: 'Jane Wanjiku',
      email: 'jane@example.com',
      phoneNumber: '0712345678',
      password: 'secret123',
      role: 'OWNER',
    });
    response.next({} as Session);
    expect(navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('goes back to the listing the visitor was booking, dates and all', async () => {
    fixture.componentRef.setInput('returnUrl', '/equipment/7?from=2030-01-02&to=2030-01-03');
    await submit(valid);
    response.next({} as Session);
    expect(navigateByUrl).toHaveBeenCalledWith('/equipment/7?from=2030-01-02&to=2030-01-03');
  });

  it('shows field errors from the backend next to the fields', async () => {
    await submit(valid);
    response.error(
      new HttpErrorResponse({
        status: 400,
        error: {
          status: 400,
          message: 'Validation failed',
          errors: { email: 'Email is already registered' },
        },
      }),
    );
    await fixture.whenStable();

    expect(el().querySelector('[role="alert"]')?.textContent).toContain(
      'Please fix the highlighted fields.',
    );
    expect(el().textContent).toContain('Email is already registered');
  });

  it('shows a general error otherwise', async () => {
    await submit(valid);
    response.error(
      new HttpErrorResponse({
        status: 409,
        error: { status: 409, message: 'Email already in use' },
      }),
    );
    await fixture.whenStable();

    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Email already in use');
  });
});
