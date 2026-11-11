import { provideDayFirstDates } from '../../../core/day-first-dates';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { BookedPeriod, BookingsService, EquipmentResponse } from '../../../api';
import { errorMessage, fieldErrors } from '../../../core/api-errors';
import { addDays, fromIsoDay, startOfToday, toIsoDay } from '../../../core/dates';
import { formatKes } from '../../equipment/equipment-labels';
import { bookedDaySet, bookingDays } from '../booking-labels';

export interface Quote {
  days: number;
  total: number;
}

/** What the API will charge: the daily rate times the days, both the first and last day counted. */
export function quote(
  dailyRateKes: number | undefined,
  start: Date | null,
  end: Date | null,
): Quote | null {
  if (dailyRateKes == null || !start || !end || end < start) {
    return null;
  }
  const days = bookingDays(toIsoDay(start), toIsoDay(end));
  return { days, total: dailyRateKes * days };
}

/** Rejects a range that runs over a day someone already has. */
export function freeRange(booked: () => Set<string>): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const { start, end: picked } = group.value as { start: Date | null; end: Date | null };
    const end = picked ?? start;
    if (!start || !end || end < start) {
      return null;
    }
    const days = booked();
    for (let day = start; day <= end; day = addDays(day, 1)) {
      if (days.has(toIsoDay(day))) {
        return { overlapsBooking: true };
      }
    }
    return null;
  };
}

/** Picks dates for a listing, shows the total, and sends the booking request. */
@Component({
  selector: 'app-booking-form',
  imports: [ReactiveFormsModule, MatButtonModule, MatDatepickerModule, MatFormFieldModule],
  providers: [provideDayFirstDates()],
  templateUrl: './booking-form.html',
  styleUrl: './booking-form.scss',
})
export class BookingForm implements OnInit {
  private readonly api = inject(BookingsService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  readonly equipment = input.required<EquipmentResponse>();
  /** Confirmed or active bookings; their days can't be picked. */
  readonly bookedPeriods = input<BookedPeriod[]>([]);
  /** The last day the booked periods are known for. */
  readonly lastDay = input.required<Date>();
  /** Days to start with (ISO), e.g. the ones the assistant priced. Past or invalid days are ignored. */
  readonly initialFrom = input<string | undefined>();
  readonly initialTo = input<string | undefined>();

  protected readonly today = startOfToday();
  protected readonly formatKes = formatKes;
  private readonly booked = computed(() => bookedDaySet(this.bookedPeriods()));

  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly form = this.fb.group(
    {
      start: this.fb.control<Date | null>(null),
      end: this.fb.control<Date | null>(null),
    },
    { validators: freeRange(() => this.booked()) },
  );
  private readonly value = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });
  protected readonly quote = computed(() =>
    // Only the first day picked so far means a one-day booking.
    quote(
      this.equipment().dailyRateKes,
      this.value().start ?? null,
      this.value().end ?? this.value().start ?? null,
    ),
  );

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const start = fromIsoDay(this.initialFrom());
    const end = fromIsoDay(this.initialTo()) ?? start;
    if (start && end && start >= this.today && end >= start && end <= this.lastDay()) {
      this.form.setValue({ start, end });
      // Show right away if those days are taken meanwhile.
      this.form.markAllAsTouched();
    }
  }

  /** Booked days and past days can't be chosen in the calendar. */
  protected readonly dateFilter = (date: Date | null): boolean =>
    !!date && date >= this.today && !this.booked().has(toIsoDay(date));

  protected submit(): void {
    const { start, end } = this.form.getRawValue();
    if (!start || this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.api
      .createBooking({
        equipmentId: this.equipment().id!,
        startDate: toIsoDay(start),
        // One day picked: the booking is for that day.
        endDate: toIsoDay(end ?? start),
      })
      .subscribe({
        next: () => {
          this.snackBar.open('Booking requested. The owner will confirm it.', 'OK', {
            duration: 6000,
          });
          void this.router.navigate(['/my-bookings']);
        },
        error: (err: unknown) => {
          const fields = Object.values(fieldErrors(err));
          this.error.set(
            fields.length ? fields.join(' ') : errorMessage(err, 'Could not book these dates.'),
          );
          this.submitting.set(false);
        },
      });
  }
}
