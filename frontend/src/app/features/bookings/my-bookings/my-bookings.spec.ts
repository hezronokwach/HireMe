import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { BookingResponse, provideApi } from '../../../api';
import { dialogProviders, fakeDialog, fakeSnackBar } from '../../owner/testing';
import { MY_BOOKINGS_PAGE_SIZE, MyBookings } from './my-bookings';

function booking(id: number, status: string, equipmentId = 1): BookingResponse {
  return {
    bookingId: id,
    equipmentId,
    startDate: '2030-01-26',
    endDate: '2030-01-27',
    totalCostKes: 12000,
    status: status as BookingResponse['status'],
    creationDate: '2030-01-10T08:00:00Z',
  };
}

describe('MyBookings', () => {
  let http: HttpTestingController;
  let dialog: ReturnType<typeof fakeDialog>;
  let snackBar: ReturnType<typeof fakeSnackBar>;
  let harness: RouterTestingHarness;
  let el: HTMLElement;

  beforeEach(async () => {
    dialog = fakeDialog();
    snackBar = fakeSnackBar();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(''),
        provideRouter([{ path: 'my-bookings', component: MyBookings }]),
        ...dialogProviders(dialog, snackBar),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/my-bookings', MyBookings);
    el = harness.routeNativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  function listRequest() {
    return http.expectOne(`/api/v1/bookings/my-bookings?page=0&size=${MY_BOOKINGS_PAGE_SIZE}`);
  }

  async function respond(bookings: BookingResponse[]): Promise<void> {
    listRequest().flush({ content: bookings, totalElements: bookings.length });
    // Each listing is fetched once, however many bookings it has.
    for (const id of new Set(bookings.map((b) => b.equipmentId))) {
      const request = http.expectOne(`/api/v1/equipment/${id}`);
      if (id === 404) {
        request.flush(null, { status: 404, statusText: 'Not Found' });
      } else {
        request.flush({ id, name: `Listing ${id} name` });
      }
    }
    await settle();
  }

  it('shows each booking with its listing name, status and total; cancel only where allowed', async () => {
    await respond([
      booking(1, 'PENDING'),
      booking(2, 'CONFIRMED'),
      booking(3, 'ACTIVE', 2),
      booking(4, 'CANCELLED', 404),
    ]);

    const rows = [...el.querySelectorAll('.row')];
    expect(rows[0].textContent).toContain('Listing 1 name');
    expect(rows[0].textContent).toContain('(2 days)');
    expect(rows[0].textContent).toMatch(/KES\s?12,000/);
    expect(rows[3].textContent).toContain('Removed listing');
    expect(rows.map((r) => r.querySelector('.status')?.textContent?.trim())).toEqual([
      'Pending',
      'Confirmed',
      'Active',
      'Cancelled',
    ]);
    expect(
      rows.map((r) =>
        [...r.querySelectorAll('button')].some((b) => b.textContent?.includes('Cancel')),
      ),
    ).toEqual([true, true, false, false]);
    expect(rows[0].querySelector('a[href="/my-bookings/1"]')).not.toBeNull();
  });

  it('cancels after confirmation and reloads', async () => {
    await respond([booking(1, 'PENDING')]);

    el.querySelector<HTMLButtonElement>('.row button')!.click();
    expect(dialog.asked[0].message).toContain('Listing 1 name');
    const cancel = http.expectOne('/api/v1/bookings/1/cancel');
    expect(cancel.request.method).toBe('PUT');
    cancel.flush(booking(1, 'CANCELLED'));
    await settle();

    expect(snackBar.messages).toEqual(['Booking cancelled.']);
    // The listing name comes from the cache this time: no second request for it.
    listRequest().flush({ content: [booking(1, 'CANCELLED')], totalElements: 1 });
  });

  it('keeps the booking when the client changes their mind', async () => {
    await respond([booking(1, 'CONFIRMED')]);
    dialog.answer = false;

    el.querySelector<HTMLButtonElement>('.row button')!.click();

    http.expectNone('/api/v1/bookings/1/cancel');
  });

  it('has an empty state that points to the equipment list', async () => {
    await respond([]);
    expect(el.textContent).toContain("You haven't booked anything yet.");
  });
});
