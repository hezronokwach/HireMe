import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { DemoGuide, GUIDE_STEPS } from './demo-guide';

describe('DemoGuide', () => {
  const auth = { logout: vi.fn() };

  beforeEach(() => {
    localStorage.clear();
    auth.logout.mockReset();
    TestBed.configureTestingModule({
      imports: [DemoGuide],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
  });

  async function render(): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(DemoGuide);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('lists the steps with links into the owner pages', async () => {
    const el = await render();
    const links = [...el.querySelectorAll<HTMLAnchorElement>('.steps a')];
    expect(links.length).toBe(GUIDE_STEPS.length);
    expect(links[0].getAttribute('href')).toBe('/owner-bookings?status=PENDING');
    expect(links[0].textContent).toContain('Answer a request');
    expect(links.at(-1)!.getAttribute('href')).toBe('/my-listings/new');
  });

  it('folds away, and remembers that', async () => {
    let el = await render();
    [...el.querySelectorAll<HTMLButtonElement>('button')]
      .find((b) => b.textContent?.includes('Hide the guide'))!
      .click();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [DemoGuide],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
    el = await render();
    expect(el.querySelector('.steps')).toBeNull();
    expect(el.textContent).toContain('Show the guide');
  });

  it('"Leave the demo" signs out', async () => {
    const el = await render();
    [...el.querySelectorAll<HTMLButtonElement>('button')]
      .find((b) => b.textContent?.includes('Leave the demo'))!
      .click();
    expect(auth.logout).toHaveBeenCalled();
  });
});
