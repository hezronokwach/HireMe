import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { AuthService, Session } from '../../core/auth/auth.service';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let response: Subject<Session>;
  const auth = { login: vi.fn() };
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    response = new Subject<Session>();
    auth.login.mockReset().mockReturnValue(response);
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(Login);
    await fixture.whenStable();
  });

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  async function fill(email: string, password: string): Promise<void> {
    for (const [name, value] of [
      ['email', email],
      ['password', password],
    ]) {
      const input = el().querySelector<HTMLInputElement>(`input[formControlName="${name}"]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    el().querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();
  }

  it('does not call the backend when the form is invalid', async () => {
    await fill('not-an-email', '');
    expect(auth.login).not.toHaveBeenCalled();
    expect(el().textContent).toContain('Enter a valid email');
  });

  it('logs in and goes to the return URL', async () => {
    fixture.componentRef.setInput('returnUrl', '/bookings');
    await fill(' jane@example.com ', 'secret123');
    expect(auth.login).toHaveBeenCalledWith('jane@example.com', 'secret123');

    response.next({} as Session);
    expect(navigateByUrl).toHaveBeenCalledWith('/bookings');
  });

  it('ignores a return URL to another site', async () => {
    fixture.componentRef.setInput('returnUrl', 'https://evil.example');
    await fill('jane@example.com', 'secret123');
    response.next({} as Session);
    expect(navigateByUrl).toHaveBeenCalledWith('/');
  });

  it("shows the backend's message when login fails", async () => {
    await fill('jane@example.com', 'wrong');
    response.error(
      new HttpErrorResponse({
        status: 401,
        error: { status: 401, message: 'Invalid email or password' },
      }),
    );
    await fixture.whenStable();

    expect(el().querySelector('[role="alert"]')?.textContent).toContain(
      'Invalid email or password',
    );
    expect(el().querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(false);
  });
});
