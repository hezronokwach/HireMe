import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { AuthService as AuthApi, DemoStatusResponse } from '../../api';
import { AuthService, Session } from '../../core/auth/auth.service';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let response: Subject<Session>;
  const auth = { login: vi.fn(), loginAsDemoOwner: vi.fn() };
  let demoStatus: DemoStatusResponse;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    response = new Subject<Session>();
    auth.login.mockReset().mockReturnValue(response);
    auth.loginAsDemoOwner.mockReset().mockReturnValue(response);
    demoStatus = { ownerAvailable: false };
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: AuthApi, useValue: { getDemoStatus: () => of(demoStatus) } },
      ],
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

  describe('demo owner', () => {
    async function recreate(): Promise<void> {
      fixture = TestBed.createComponent(Login);
      await fixture.whenStable();
    }

    it('is not offered when the server has no demo data', () => {
      expect(el().querySelector('[data-testid="demo-owner"]')).toBeNull();
    });

    it('signs in with one click and opens the booking requests', async () => {
      demoStatus = { ownerAvailable: true, ownerEmail: 'demo-owner@hireme.example' };
      await recreate();
      const button = el().querySelector<HTMLButtonElement>('[data-testid="demo-owner"] button')!;
      expect(button.textContent).toContain('Sign in as the demo owner');

      button.click();
      expect(auth.loginAsDemoOwner).toHaveBeenCalled();
      response.next({} as Session);
      expect(navigateByUrl).toHaveBeenCalledWith('/owner-bookings');
    });

    it("shows the backend's message when the demo isn't available", async () => {
      demoStatus = { ownerAvailable: true };
      await recreate();
      el().querySelector<HTMLButtonElement>('[data-testid="demo-owner"] button')!.click();
      response.error(
        new HttpErrorResponse({
          status: 404,
          error: { message: "The demo isn't set up on this server" },
        }),
      );
      await fixture.whenStable();
      expect(el().querySelector('[role="alert"]')?.textContent).toContain("isn't set up");
    });
  });
});
