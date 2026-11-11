import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideApi } from '../../../api';
import { AuthService, Role } from '../../../core/auth/auth.service';
import { addDays, startOfToday, toIsoDay } from '../../../core/dates';
import { AVAILABILITY_DAYS, EquipmentDetail, backToListUrl } from './equipment-detail';

describe('EquipmentDetail', () => {
  let http: HttpTestingController;
  let role: Role;

  beforeEach(() => {
    role = 'CLIENT';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(''),
        provideRouter(
          [{ path: 'equipment/:id', component: EquipmentDetail }],
          withComponentInputBinding(),
        ),
        { provide: AuthService, useValue: { hasRole: (...roles: Role[]) => roles.includes(role) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(id: string): Promise<{ el: HTMLElement; harness: RouterTestingHarness }> {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/equipment/${id}`, EquipmentDetail);
    return { el: harness.routeNativeElement as HTMLElement, harness };
  }

  function respond(
    status = 'AVAILABLE',
    booked: { startDate: string; endDate: string }[] = [],
  ): void {
    http.expectOne('/api/v1/equipment/7').flush({
      id: 7,
      name: 'Excavator CAT 320',
      category: 'HEAVY_MACHINERY',
      description: 'Well kept.\nOperator available.',
      dailyRateKes: 25000,
      location: 'Nairobi',
      equipmentStatus: status,
    });
    const today = startOfToday();
    const availability = http.expectOne((r) => r.url === '/api/v1/equipment/7/availability');
    expect(availability.request.params.get('from')).toBe(toIsoDay(today));
    expect(availability.request.params.get('to')).toBe(toIsoDay(addDays(today, AVAILABILITY_DAYS)));
    availability.flush({ equipmentId: 7, bookedPeriods: booked });
  }

  it('shows the listing and its booked dates', async () => {
    const { el, harness } = await open('7');
    const year = new Date().getFullYear();
    respond('AVAILABLE', [
      { startDate: `${year + 1}-01-02`, endDate: `${year + 1}-01-04` },
      { startDate: `${year + 1}-01-09`, endDate: `${year + 1}-01-09` },
    ]);
    await harness.fixture.whenStable();
    harness.detectChanges();

    expect(el.querySelector('h1')?.textContent).toContain('Excavator CAT 320');
    expect(el.textContent).toMatch(/KES\s?25,000/);
    expect(el.textContent).toContain('Taking bookings');
    expect(el.querySelector('[data-testid="booked-periods"]')?.textContent).toMatch(
      /2 Jan \d{4} – 4 Jan \d{4}[\s\S]*9 Jan \d{4}/,
    );
  });

  it('shows clients a Book button (not wired up yet)', async () => {
    const { el, harness } = await open('7');
    respond();
    await harness.fixture.whenStable();
    expect(el.querySelector<HTMLButtonElement>('[data-testid="book"]')?.disabled).toBe(true);
    expect(el.textContent).toContain('No bookings yet');
  });

  it('shows owners no Book button', async () => {
    role = 'OWNER';
    const { el, harness } = await open('7');
    respond();
    await harness.fixture.whenStable();
    expect(el.querySelector('[data-testid="book"]')).toBeNull();
  });

  it('explains when a listing is under maintenance', async () => {
    const { el, harness } = await open('7');
    respond('UNDER_MAINTENANCE');
    await harness.fixture.whenStable();
    expect(el.textContent).toContain("This listing isn't taking bookings right now.");
  });

  it('shows a not-found page for a missing or malformed id', async () => {
    const { el, harness } = await open('7');
    http.expectOne('/api/v1/equipment/7').flush(null, { status: 404, statusText: 'Not Found' });
    http.expectOne((r) => r.url === '/api/v1/equipment/7/availability');
    await harness.fixture.whenStable();
    expect(el.textContent).toContain("This listing doesn't exist or has been removed.");
  });

  it('shows the not-found page for a malformed id without calling the API', async () => {
    const { el, harness } = await open('abc');
    await harness.fixture.whenStable();
    expect(el.textContent).toContain("doesn't exist");
  });

  it('only goes back to equipment list URLs', () => {
    expect(backToListUrl({ from: '/equipment?category=AGRICULTURAL' })).toBe(
      '/equipment?category=AGRICULTURAL',
    );
    expect(backToListUrl({ from: 'https://evil.example' })).toBe('/equipment');
    expect(backToListUrl({ from: '/equipment/7' })).toBe('/equipment');
    expect(backToListUrl(null)).toBe('/equipment');
  });
});
