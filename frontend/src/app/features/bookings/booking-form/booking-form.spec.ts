import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { EquipmentResponse, provideApi } from '../../../api';
import { addDays, startOfToday, toIsoDay } from '../../../core/dates';
import { fakeSnackBar } from '../../owner/testing';
import { BookingForm, quote } from './booking-form';

describe('quote', () => {
  it('charges the daily rate for every day, both ends included', () => {
    expect(quote(6000, new Date(2030, 0, 26), new Date(2030, 0, 28))).toEqual({
      days: 3,
      total: 18000,
    });
    expect(quote(6000, new Date(2030, 0, 26), new Date(2030, 0, 26))).toEqual({
      days: 1,
      total: 6000,
    });
  });

  it('has nothing to say without both days, or for a backwards range', () => {
    expect(quote(6000, null, null)).toBeNull();
    expect(quote(6000, new Date(2030, 0, 26), new Date(2030, 0, 25))).toBeNull();
    expect(quote(undefined, new Date(2030, 0, 26), new Date(2030, 0, 26))).toBeNull();
  });
});

describe('BookingForm', () => {
  const today = startOfToday();
  const day = (n: number) => addDays(today, n);
  const TRACTOR = { id: 1, name: 'Tractor', dailyRateKes: 6000 } as EquipmentResponse;

  let fixture: ComponentFixture<BookingForm>;
  let http: HttpTestingController;
  let snackBar: ReturnType<typeof fakeSnackBar>;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    snackBar = fakeSnackBar();
    TestBed.configureTestingModule({
      imports: [BookingForm],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(''),
        provideRouter([]),
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(BookingForm);
    fixture.componentRef.setInput('equipment', TRACTOR);
    // Booked: days 5 to 6 from today.
    fixture.componentRef.setInput('bookedPeriods', [
      { startDate: toIsoDay(day(5)), endDate: toIsoDay(day(6)) },
    ]);
    fixture.componentRef.setInput('lastDay', day(365));
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function form() {
    return (fixture.componentInstance as unknown as { form: { setValue(v: object): void } }).form;
  }

  async function pick(start: Date | null, end: Date | null): Promise<void> {
    form().setValue({ start, end });
    await fixture.whenStable();
  }

  function book(): HTMLButtonElement {
    return el().querySelector<HTMLButtonElement>('[data-testid="book"]')!;
  }

  it('shows the total before sending, and treats one picked day as a one-day booking', async () => {
    expect(book().disabled).toBe(true);
    await pick(day(1), null);
    expect(el().querySelector('[data-testid="quote"]')?.textContent).toMatch(
      /1 day × KES\s?6,000 =\s+KES\s?6,000/,
    );

    await pick(day(1), day(3));
    expect(el().querySelector('[data-testid="quote"]')?.textContent).toMatch(
      /3 days × KES\s?6,000 =\s+KES\s?18,000/,
    );
    expect(book().disabled).toBe(false);
  });

  it("won't take dates that run over a booked day", async () => {
    await pick(day(4), day(7));
    expect(el().textContent).toContain('These dates include days that are already booked.');
    expect(book().disabled).toBe(true);
  });

  it('sends the request with ISO days, then opens My bookings', async () => {
    await pick(day(1), day(3));
    book().click();

    const request = http.expectOne('/api/v1/bookings');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      equipmentId: 1,
      startDate: toIsoDay(day(1)),
      endDate: toIsoDay(day(3)),
    });
    request.flush({ bookingId: 9, status: 'PENDING' });
    await fixture.whenStable();

    expect(snackBar.messages).toEqual(['Booking requested. The owner will confirm it.']);
    expect(navigate).toHaveBeenCalledWith(['/my-bookings']);
  });

  it('a single picked day is sent as start = end', async () => {
    await pick(day(2), null);
    book().click();
    const body = http.expectOne('/api/v1/bookings').request.body;
    expect(body.startDate).toBe(body.endDate);
  });

  it("shows the API's reason when the booking is refused", async () => {
    await pick(day(1), day(2));
    book().click();
    http
      .expectOne('/api/v1/bookings')
      .flush(
        { status: 409, message: 'Equipment is already booked for some of these dates' },
        { status: 409, statusText: 'Conflict' },
      );
    await fixture.whenStable();

    expect(el().querySelector('[data-testid="booking-error"]')?.textContent).toContain(
      'Equipment is already booked for some of these dates',
    );
    expect(book().disabled).toBe(false);
  });

  it('lists the field problems from a 400', async () => {
    await pick(day(1), day(2));
    book().click();
    http.expectOne('/api/v1/bookings').flush(
      {
        status: 400,
        message: 'Validation failed',
        errors: { startDate: 'Start date must be today or in the future' },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(el().querySelector('[data-testid="booking-error"]')?.textContent).toContain(
      'Start date must be today or in the future',
    );
  });

  it('keeps past and booked days out of the calendar', () => {
    const filter = (fixture.componentInstance as unknown as { dateFilter(d: Date | null): boolean })
      .dateFilter;
    expect(filter(day(-1))).toBe(false);
    expect(filter(day(0))).toBe(true);
    expect(filter(day(5))).toBe(false);
    expect(filter(day(7))).toBe(true);
  });

  it('starts with the days it was given (e.g. from the assistant), with the total shown', async () => {
    fixture.componentRef.setInput('initialFrom', toIsoDay(day(1)));
    fixture.componentRef.setInput('initialTo', toIsoDay(day(3)));
    fixture.componentInstance.ngOnInit();
    await fixture.whenStable();

    expect(el().querySelector('[data-testid="quote"]')?.textContent).toMatch(/3 days/);
    expect(book().disabled).toBe(false);
  });

  it('ignores given days in the past, backwards or nonsense, and flags booked ones', async () => {
    for (const [from, to] of [
      [toIsoDay(day(-2)), toIsoDay(day(-1))],
      [toIsoDay(day(3)), toIsoDay(day(1))],
      ['soon', undefined],
    ]) {
      fixture.componentRef.setInput('initialFrom', from);
      fixture.componentRef.setInput('initialTo', to);
      fixture.componentInstance.ngOnInit();
    }
    await fixture.whenStable();
    expect(el().querySelector('[data-testid="quote"]')).toBeNull();

    fixture.componentRef.setInput('initialFrom', toIsoDay(day(5)));
    fixture.componentRef.setInput('initialTo', toIsoDay(day(5)));
    fixture.componentInstance.ngOnInit();
    await fixture.whenStable();
    expect(el().textContent).toContain('These dates include days that are already booked.');
  });
});
