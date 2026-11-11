import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { BookingResponse, provideApi } from '../../../api';
import { dialogProviders, fakeDialog, fakeSnackBar } from '../testing';
import { OWNER_BOOKINGS_PAGE_SIZE, OwnerBookings } from './owner-bookings';

function booking(id: number, status: string, equipmentId = 1): BookingResponse {
  return {
    bookingId: id,
    equipmentId,
    clientId: 9,
    startDate: '2030-01-26',
    endDate: '2030-01-28',
    totalCostKes: 18000,
    status: status as BookingResponse['status'],
    creationDate: '2030-01-10T08:00:00Z',
  };
}

describe('OwnerBookings', () => {
  let http: HttpTestingController;
  let dialog: ReturnType<typeof fakeDialog>;
  let snackBar: ReturnType<typeof fakeSnackBar>;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    dialog = fakeDialog();
    snackBar = fakeSnackBar();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(''),
        provideRouter([{ path: 'owner-bookings', component: OwnerBookings }]),
        ...dialogProviders(dialog, snackBar),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  async function open(url = '/owner-bookings'): Promise<HTMLElement> {
    await harness.navigateByUrl(url, OwnerBookings);
    http
      .expectOne('/api/v1/equipment/mine?page=0&size=100&sort=name%2Casc')
      .flush({ content: [{ id: 1, name: 'Tractor A' }] });
    return harness.routeNativeElement as HTMLElement;
  }

  function listRequest() {
    return http.expectOne((r) => r.url === '/api/v1/bookings/for-my-equipment');
  }

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  function button(el: HTMLElement, bookingId: number, text: string): HTMLButtonElement | undefined {
    return [
      ...el.querySelectorAll<HTMLButtonElement>(`[data-testid="booking-${bookingId}"] button`),
    ].find((b) => b.textContent?.includes(text));
  }

  it('lists bookings with the equipment name, days, total and the allowed actions', async () => {
    const el = await open();
    const request = listRequest();
    expect(request.request.params.has('status')).toBe(false);
    expect(request.request.params.get('size')).toBe(String(OWNER_BOOKINGS_PAGE_SIZE));
    request.flush({
      content: [booking(1, 'PENDING'), booking(2, 'CONFIRMED', 77), booking(3, 'COMPLETED')],
      totalElements: 3,
    });
    await settle();

    const first = el.querySelector('[data-testid="booking-1"]')!;
    expect(first.textContent).toContain('Tractor A');
    expect(first.textContent).toContain('(3 days)');
    expect(first.textContent).toMatch(/KES\s?18,000/);
    expect(first.textContent).toContain('Pending');
    expect(el.querySelector('[data-testid="booking-2"]')!.textContent).toContain('Listing #77');
    expect(button(el, 1, 'Confirm')).toBeTruthy();
    expect(button(el, 1, 'Decline')).toBeTruthy();
    expect(button(el, 2, 'Complete')).toBeTruthy();
    expect(button(el, 2, 'Confirm')).toBeUndefined();
    expect(el.querySelectorAll('[data-testid="booking-3"] button').length).toBe(0);
  });

  it('filters by status through the URL', async () => {
    const el = await open('/owner-bookings?status=PENDING');
    expect(listRequest().request.params.get('status')).toBe('PENDING');

    await settle();
    const chips = [...el.querySelectorAll<HTMLElement>('mat-chip-option')];
    chips
      .find((c) => c.textContent?.includes('Confirmed'))!
      .querySelector<HTMLElement>('button')!
      .click();
    await settle();

    expect(TestBed.inject(Router).url).toBe('/owner-bookings?status=CONFIRMED');
    listRequest().flush({ content: [], totalElements: 0 });
    await settle();
    expect(el.textContent).toContain('No confirmed bookings.');
  });

  it('confirms straight away and reloads the list', async () => {
    const el = await open();
    listRequest().flush({ content: [booking(1, 'PENDING')], totalElements: 1 });
    await settle();

    button(el, 1, 'Confirm')!.click();
    const confirm = http.expectOne('/api/v1/bookings/1/confirm');
    expect(confirm.request.method).toBe('PUT');
    confirm.flush(booking(1, 'CONFIRMED'));
    await settle();

    expect(dialog.asked).toEqual([]);
    expect(snackBar.messages[0]).toContain('Booking confirmed.');
    listRequest().flush({ content: [booking(1, 'CONFIRMED')], totalElements: 1 });
  });

  it('asks before declining, and declining is a cancel', async () => {
    const el = await open();
    listRequest().flush({ content: [booking(1, 'PENDING')], totalElements: 1 });
    await settle();

    button(el, 1, 'Decline')!.click();
    expect(dialog.asked[0].title).toBe('Decline this request?');
    expect(dialog.asked[0].message).toContain('Tractor A');
    http.expectOne('/api/v1/bookings/1/cancel').flush(booking(1, 'CANCELLED'));
    await settle();
    listRequest().flush({ content: [], totalElements: 0 });
  });

  it("shows the API's reason when an action is refused and reloads the real state", async () => {
    const el = await open();
    listRequest().flush({ content: [booking(1, 'CONFIRMED')], totalElements: 1 });
    await settle();

    button(el, 1, 'Complete')!.click();
    http.expectOne('/api/v1/bookings/1/complete').flush(
      {
        status: 409,
        message: 'Only ACTIVE or CONFIRMED bookings can be completed (current status: CANCELLED)',
      },
      { status: 409, statusText: 'Conflict' },
    );
    await settle();

    expect(snackBar.messages[0]).toContain('current status: CANCELLED');
    listRequest().flush({ content: [booking(1, 'CANCELLED')], totalElements: 1 });
  });
});
