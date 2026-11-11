import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { EquipmentResponse, provideApi } from '../../../api';
import { dialogProviders, fakeDialog, fakeSnackBar } from '../testing';
import { MY_LISTINGS_PAGE_SIZE, MyListings } from './my-listings';

const TRACTOR = {
  id: 1,
  ownerId: 5,
  name: 'Tractor',
  category: 'AGRICULTURAL',
  location: 'Nakuru',
  dailyRateKes: 6000,
  equipmentStatus: 'AVAILABLE',
} as EquipmentResponse;
const TENT = {
  ...TRACTOR,
  id: 2,
  name: 'Tent',
  equipmentStatus: 'UNDER_MAINTENANCE',
} as EquipmentResponse;

describe('MyListings', () => {
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
        provideRouter([{ path: 'my-listings', component: MyListings }]),
        ...dialogProviders(dialog, snackBar),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/my-listings', MyListings);
    el = harness.routeNativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  function listRequest(page = 0) {
    return http.expectOne(`/api/v1/equipment/mine?page=${page}&size=${MY_LISTINGS_PAGE_SIZE}`);
  }

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  function button(text: string, row = 0): HTMLButtonElement {
    const rows = el.querySelectorAll('.row');
    return [...rows[row].querySelectorAll<HTMLButtonElement>('button')].find((b) =>
      b.textContent?.includes(text),
    )!;
  }

  it("shows the owner's listings with their status and actions", async () => {
    listRequest().flush({
      content: [TRACTOR, TENT],
      page: 0,
      size: 12,
      totalElements: 2,
      totalPages: 1,
    });
    await settle();

    const rows = el.querySelectorAll('.row');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Tractor');
    expect(rows[0].querySelector('[data-testid="status"]')?.textContent).toContain(
      'Taking bookings',
    );
    expect(rows[1].querySelector('[data-testid="status"]')?.textContent).toContain(
      'Under maintenance',
    );
    expect(rows[0].querySelector('a[href="/my-listings/1/edit"]')).not.toBeNull();
    expect(button('Pause bookings', 0)).toBeTruthy();
    expect(button('Take bookings', 1)).toBeTruthy();
  });

  it('pauses bookings by sending only the status, then reloads', async () => {
    listRequest().flush({ content: [TRACTOR], totalElements: 1 });
    await settle();

    button('Pause bookings').click();
    const update = http.expectOne('/api/v1/equipment/1');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ status: 'UNDER_MAINTENANCE' });
    update.flush({ ...TRACTOR, equipmentStatus: 'UNDER_MAINTENANCE' });
    await settle();

    expect(snackBar.messages).toEqual(['“Tractor” no longer takes new bookings.']);
    listRequest().flush({ content: [], totalElements: 0 });
  });

  it('asks before deleting, and does nothing when the owner keeps it', async () => {
    listRequest().flush({ content: [TRACTOR], totalElements: 1 });
    await settle();

    dialog.answer = false;
    button('Delete').click();
    await settle();

    expect(dialog.asked[0].title).toBe('Delete this listing?');
    http.expectNone('/api/v1/equipment/1');
  });

  it("deletes after confirmation, and shows the API's reason when it is refused", async () => {
    listRequest().flush({ content: [TRACTOR], totalElements: 1 });
    await settle();

    button('Delete').click();
    http
      .expectOne((r) => r.method === 'DELETE' && r.url === '/api/v1/equipment/1')
      .flush(
        { status: 409, message: 'Equipment has active bookings and cannot be deleted' },
        { status: 409, statusText: 'Conflict' },
      );
    await settle();
    expect(snackBar.messages).toEqual(['Equipment has active bookings and cannot be deleted']);
    expect(button('Delete').disabled).toBe(false);

    button('Delete').click();
    http
      .expectOne((r) => r.method === 'DELETE')
      .flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    expect(snackBar.messages[1]).toBe('“Tractor” was deleted.');
    listRequest().flush({ content: [], totalElements: 0 });
    await settle();
    expect(el.textContent).toContain("You haven't listed any equipment yet.");
  });

  it('after deleting the only listing on a later page, shows the page before', async () => {
    listRequest().flush({
      content: Array(12)
        .fill(TRACTOR)
        .map((t, i) => ({ ...t, id: i + 1 })),
      totalElements: 13,
    });
    await settle();
    el.querySelector<HTMLButtonElement>('mat-paginator button[aria-label="Next page"]')!.click();
    await settle();
    listRequest(1).flush({ content: [{ ...TRACTOR, id: 13 }], totalElements: 13 });
    await settle();

    button('Delete').click();
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/v1/equipment/13').flush(null);
    await settle();

    listRequest(0).flush({ content: [TRACTOR], totalElements: 12 });
  });
});
