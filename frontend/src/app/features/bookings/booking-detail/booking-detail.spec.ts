import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideApi } from '../../../api';
import { dialogProviders, fakeDialog, fakeSnackBar } from '../../owner/testing';
import { BookingDetail } from './booking-detail';

describe('BookingDetail', () => {
  let http: HttpTestingController;
  let snackBar: ReturnType<typeof fakeSnackBar>;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    snackBar = fakeSnackBar();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(''),
        provideRouter(
          [{ path: 'my-bookings/:id', component: BookingDetail }],
          withComponentInputBinding(),
        ),
        ...dialogProviders(fakeDialog(), snackBar),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  async function open(id: string): Promise<HTMLElement> {
    await harness.navigateByUrl(`/my-bookings/${id}`, BookingDetail);
    return harness.routeNativeElement as HTMLElement;
  }

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  function respond(status: string): void {
    http.expectOne('/api/v1/bookings/5').flush({
      bookingId: 5,
      equipmentId: 1,
      startDate: '2030-01-26',
      endDate: '2030-01-28',
      totalCostKes: 18000,
      status,
      creationDate: '2030-01-10T08:00:00Z',
    });
    http
      .expectOne('/api/v1/equipment/1')
      .flush({ id: 1, name: 'Tractor A', location: 'Nakuru', dailyRateKes: 6000 });
  }

  it('shows what was booked, for when, the total and what the status means', async () => {
    const el = await open('5');
    respond('CONFIRMED');
    await settle();

    expect(el.querySelector('h1')?.textContent).toContain('Tractor A');
    expect(el.querySelector('[data-testid="status"]')?.textContent).toContain('Confirmed');
    expect(el.querySelector('[data-testid="status-help"]')?.textContent).toContain(
      'The owner confirmed it',
    );
    expect(el.textContent).toContain('(3 days)');
    expect(el.textContent).toContain('Nakuru');
    expect(el.textContent).toMatch(/KES\s?18,000/);
    expect(el.textContent).toContain('#5');
  });

  it('cancels a confirmed booking after asking, then shows the new status', async () => {
    const el = await open('5');
    respond('CONFIRMED');
    await settle();

    [...el.querySelectorAll<HTMLButtonElement>('button')]
      .find((b) => b.textContent?.includes('Cancel booking'))!
      .click();
    http.expectOne('/api/v1/bookings/5/cancel').flush({});
    await settle();

    expect(snackBar.messages).toEqual(['Booking cancelled.']);
    // Reloaded; the listing comes from the cache.
    http.expectOne('/api/v1/bookings/5').flush({
      bookingId: 5,
      equipmentId: 1,
      startDate: '2030-01-26',
      endDate: '2030-01-28',
      status: 'CANCELLED',
    });
    await settle();
    expect(el.querySelector('[data-testid="status"]')?.textContent).toContain('Cancelled');
    expect(
      [...el.querySelectorAll('button')].some((b) => b.textContent?.includes('Cancel booking')),
    ).toBe(false);
  });

  it('an active booking has no cancel button and says why', async () => {
    const el = await open('5');
    respond('ACTIVE');
    await settle();

    expect(el.textContent).toContain('Only the owner can cancel it now.');
    expect([...el.querySelectorAll('button')].some((b) => b.textContent?.includes('Cancel'))).toBe(
      false,
    );
  });

  it("treats someone else's booking like a missing one", async () => {
    const el = await open('5');
    http.expectOne('/api/v1/bookings/5').flush(null, { status: 403, statusText: 'Forbidden' });
    await settle();

    expect(el.textContent).toContain("This booking doesn't exist, or it isn't yours.");
  });
});
