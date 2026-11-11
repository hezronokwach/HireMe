import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideApi } from '../../api';
import { AuthService, Role } from '../../core/auth/auth.service';
import { AssistantPanel, STARTERS } from './assistant-panel';

const URL = '/api/v1/equipment/ai/assistant';

describe('AssistantPanel', () => {
  let fixture: ComponentFixture<AssistantPanel>;
  let http: HttpTestingController;
  let role: Role;

  beforeEach(async () => {
    sessionStorage.clear();
    role = 'CLIENT';
    TestBed.configureTestingModule({
      imports: [AssistantPanel],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(''),
        provideRouter([]),
        { provide: AuthService, useValue: { hasRole: (...roles: Role[]) => roles.includes(role) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AssistantPanel);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const answer = {
    reply: 'The Tractor A is cheapest and free those days.',
    listings: [
      {
        id: 1,
        name: 'Tractor A',
        category: 'AGRICULTURAL',
        location: 'Nakuru',
        dailyRateKes: 6000,
        takingBookings: true,
        quote: { from: '2030-11-19', to: '2030-11-21', days: 3, totalKes: 18000, free: true },
      },
      {
        id: 2,
        name: 'Tractor B',
        category: 'AGRICULTURAL',
        location: 'Nakuru',
        dailyRateKes: 7500,
        takingBookings: true,
        quote: { from: '2030-11-19', to: '2030-11-21', days: 3, totalKes: 22500, free: false },
      },
    ],
    from: '2030-11-19',
    to: '2030-11-21',
    suggestions: ['Any cheaper?', 'Does it come with a driver?'],
  };

  it('offers ways to start, and a starter sends that message', async () => {
    const starters = [...el().querySelectorAll<HTMLButtonElement>('.suggestions button')];
    expect(starters.map((b) => b.textContent?.trim())).toEqual(STARTERS);

    starters[0].click();
    await settle();

    expect(http.expectOne(URL).request.body.messages[0].text).toBe(STARTERS[0]);
    expect(el().querySelector('[data-testid="user-message"]')?.textContent).toContain(STARTERS[0]);
    expect(el().querySelector('.thinking')).not.toBeNull();
  });

  it('shows the reply with listing cards: price for the days, and a booking hand-off when free', async () => {
    el().querySelector<HTMLButtonElement>('.suggestions button')!.click();
    http.expectOne(URL).flush(answer);
    await settle();

    expect(el().querySelector('[data-testid="assistant-message"]')?.textContent).toContain(
      'cheapest',
    );
    const free = el().querySelector('[data-testid="card-1"]')!;
    expect(free.querySelector('[data-testid="card-quote"]')?.textContent).toMatch(
      /3 days · KES\s?18,000 · Free/,
    );
    expect(free.querySelector('[data-testid="book-these-dates"]')?.getAttribute('href')).toBe(
      '/equipment/1?from=2030-11-19&to=2030-11-21',
    );
    const booked = el().querySelector('[data-testid="card-2"]')!;
    expect(booked.textContent).toContain('Already booked then');
    expect(booked.querySelector('[data-testid="book-these-dates"]')).toBeNull();
    expect(booked.textContent).toContain('View');
  });

  it('offers the suggestions under the latest answer, and they send', async () => {
    el().querySelector<HTMLButtonElement>('.suggestions button')!.click();
    http.expectOne(URL).flush(answer);
    await settle();

    const suggestion = [...el().querySelectorAll<HTMLButtonElement>('.suggestions button')].find(
      (b) => b.textContent?.includes('Any cheaper?'),
    )!;
    suggestion.click();
    await settle();

    const sent = http.expectOne(URL).request.body.messages;
    expect(sent.at(-1)).toEqual({ role: 'USER', text: 'Any cheaper?' });
    expect(sent.at(-2).listingIds).toEqual([1, 2]);
  });

  it('owners see View instead of booking', async () => {
    role = 'OWNER';
    el().querySelector<HTMLButtonElement>('.suggestions button')!.click();
    http.expectOne(URL).flush(answer);
    await settle();

    expect(el().querySelector('[data-testid="book-these-dates"]')).toBeNull();
  });

  it('Enter sends what was typed; Shift+Enter does not', async () => {
    const input = el().querySelector<HTMLTextAreaElement>('[data-testid="assistant-input"]')!;
    input.value = 'tent for a wedding';
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true }));
    http.expectNone(URL);

    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await settle();

    expect(http.expectOne(URL).request.body.messages[0].text).toBe('tent for a wedding');
    expect(input.value).toBe('');
  });

  it('shows errors with a retry', async () => {
    el().querySelector<HTMLButtonElement>('.suggestions button')!.click();
    http.expectOne(URL).flush({ message: 'down' }, { status: 503, statusText: 'Unavailable' });
    await settle();

    expect(el().querySelector('[role="alert"]')?.textContent).toContain('unavailable right now');
    [...el().querySelectorAll<HTMLButtonElement>('[role="alert"] button')][0].click();
    http.expectOne(URL).flush(answer);
  });
});
