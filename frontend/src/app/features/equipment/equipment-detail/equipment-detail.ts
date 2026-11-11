import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { MatCardModule } from '@angular/material/card';
import { MatCalendarCellClassFunction, MatDatepickerModule } from '@angular/material/datepicker';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';
import {
  Observable,
  catchError,
  combineLatest,
  forkJoin,
  map,
  of,
  startWith,
  switchMap,
} from 'rxjs';
import { EquipmentAvailabilityResponse, EquipmentResponse, EquipmentService } from '../../../api';
import { errorMessage } from '../../../core/api-errors';
import { AuthService } from '../../../core/auth/auth.service';
import { addDays, formatRange, fromIsoDay, startOfToday, toIsoDay } from '../../../core/dates';
import { categoryIcon, categoryLabel, formatKes, statusLabel } from '../equipment-labels';

/** How far ahead the booked dates are shown (the API's default is also 90 days). */
export const AVAILABILITY_DAYS = 90;

interface Detail {
  state: 'loading' | 'ready' | 'not-found' | 'error';
  equipment?: EquipmentResponse;
  availability?: EquipmentAvailabilityResponse;
  error?: string;
}

@Component({
  selector: 'app-equipment-detail',
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatDatepickerModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  // Date pickers use plain JS Dates (kept out of the initial bundle).
  // Days written day/month/year, as in Kenya.
  providers: [provideNativeDateAdapter(), { provide: MAT_DATE_LOCALE, useValue: 'en-GB' }],
  templateUrl: './equipment-detail.html',
  styleUrl: './equipment-detail.scss',
})
export class EquipmentDetail {
  private readonly api = inject(EquipmentService);
  protected readonly auth = inject(AuthService);

  /** Route parameter. */
  readonly id = input.required<string>();

  protected readonly categoryLabel = categoryLabel;
  protected readonly categoryIcon = categoryIcon;
  protected readonly statusLabel = statusLabel;
  protected readonly formatKes = formatKes;
  protected readonly formatRange = formatRange;
  protected readonly today = startOfToday();
  protected readonly lastShownDay = addDays(this.today, AVAILABILITY_DAYS);

  protected readonly availabilityDays = AVAILABILITY_DAYS;
  /** The list page (with its filters) this page was opened from, if any. */
  private readonly router = inject(Router);
  protected readonly backLink = this.router.parseUrl(
    // Set while this page is being opened; history.state still has it after a reload.
    backToListUrl(this.router.currentNavigation()?.extras.state ?? globalThis.history?.state),
  );

  private readonly reload = signal(0);
  protected readonly detail = toSignal(
    combineLatest([toObservable(this.id), toObservable(this.reload)]).pipe(
      switchMap(([id]) => this.load(Number(id))),
    ),
    { initialValue: { state: 'loading' } as Detail },
  );

  protected readonly bookedPeriods = computed(
    () => this.detail().availability?.bookedPeriods ?? [],
  );

  /** Marks booked days on the calendar. */
  protected readonly dateClass = computed<MatCalendarCellClassFunction<Date>>(() => {
    const booked = new Set<string>();
    for (const period of this.bookedPeriods()) {
      const start = fromIsoDay(period.startDate);
      const end = fromIsoDay(period.endDate);
      for (let day = start; day && end && day <= end; day = addDays(day, 1)) {
        booked.add(toIsoDay(day));
      }
    }
    return (date, view) => (view === 'month' && booked.has(toIsoDay(date)) ? 'booked-day' : '');
  });

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  private load(id: number): Observable<Detail> {
    if (!Number.isInteger(id) || id <= 0) {
      return of({ state: 'not-found' });
    }
    return forkJoin({
      equipment: this.api.getEquipment(id),
      availability: this.api.getEquipmentAvailability(
        id,
        toIsoDay(this.today),
        toIsoDay(this.lastShownDay),
      ),
    }).pipe(
      map(({ equipment, availability }): Detail => ({ state: 'ready', equipment, availability })),
      catchError((err: unknown) =>
        of<Detail>(
          err instanceof HttpErrorResponse && err.status === 404
            ? { state: 'not-found' }
            : { state: 'error', error: errorMessage(err, 'Could not load this listing.') },
        ),
      ),
      startWith<Detail>({ state: 'loading' }),
    );
  }
}

/** The list URL stored in the navigation state by the list page; only list URLs are accepted. */
export function backToListUrl(state: unknown): string {
  const from = (state as { from?: unknown } | null)?.from;
  return typeof from === 'string' && /^\/equipment(\?|$)/.test(from) ? from : '/equipment';
}
