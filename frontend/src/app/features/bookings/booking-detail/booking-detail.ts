import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { Observable, catchError, combineLatest, filter, map, of, startWith, switchMap } from 'rxjs';
import { BookingResponse, BookingsService, EquipmentResponse } from '../../../api';
import { errorMessage } from '../../../core/api-errors';
import { formatDay, formatRange } from '../../../core/dates';
import { confirmAction } from '../../../shared/confirm-dialog';
import { EquipmentLookup } from '../../equipment/equipment-lookup';
import { formatKes } from '../../equipment/equipment-labels';
import {
  bookingDays,
  bookingStatusLabel,
  clientCanCancel,
  clientStatusHelp,
} from '../booking-labels';

interface Detail {
  state: 'loading' | 'ready' | 'not-found' | 'error';
  booking?: BookingResponse;
  equipment?: EquipmentResponse | null;
  error?: string;
}

/** One of the client's bookings: what, when, how much, what its status means, and cancel. */
@Component({
  selector: 'app-booking-detail',
  imports: [RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule],
  templateUrl: './booking-detail.html',
  styleUrl: './booking-detail.scss',
})
export class BookingDetail {
  private readonly api = inject(BookingsService);
  private readonly lookup = inject(EquipmentLookup);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  /** Route parameter. */
  readonly id = input.required<string>();

  protected readonly bookingStatusLabel = bookingStatusLabel;
  protected readonly clientStatusHelp = clientStatusHelp;
  protected readonly clientCanCancel = clientCanCancel;
  protected readonly bookingDays = bookingDays;
  protected readonly formatRange = formatRange;
  protected readonly formatDay = formatDay;
  protected readonly formatKes = formatKes;

  private readonly reload = signal(0);
  protected readonly cancelling = signal(false);
  protected readonly detail = toSignal(
    combineLatest([toObservable(this.id), toObservable(this.reload)]).pipe(
      switchMap(([id]) => this.load(Number(id))),
    ),
    { initialValue: { state: 'loading' } as Detail },
  );

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  protected cancel(): void {
    const booking = this.detail().booking!;
    confirmAction(this.dialog, {
      title: 'Cancel this booking?',
      message: `${this.detail().equipment?.name ?? 'This booking'}, ${formatRange(booking.startDate!, booking.endDate!)}.`,
      confirm: 'Cancel booking',
      danger: true,
    })
      .pipe(filter(Boolean))
      .subscribe(() => {
        this.cancelling.set(true);
        this.api.cancelBooking(booking.bookingId!).subscribe({
          next: () => {
            this.cancelling.set(false);
            this.snackBar.open('Booking cancelled.', 'OK', { duration: 4000 });
            this.reload.update((n) => n + 1);
          },
          error: (err: unknown) => {
            this.cancelling.set(false);
            this.snackBar.open(errorMessage(err), 'OK', { duration: 8000 });
            this.reload.update((n) => n + 1);
          },
        });
      });
  }

  private load(id: number): Observable<Detail> {
    if (!Number.isInteger(id) || id <= 0) {
      return of({ state: 'not-found' });
    }
    return this.api.getBooking(id).pipe(
      switchMap((booking) =>
        this.lookup
          .get(booking.equipmentId!)
          .pipe(map((equipment): Detail => ({ state: 'ready', booking, equipment }))),
      ),
      catchError((err: unknown) =>
        of<Detail>(
          // 403: someone else's booking; treated like a missing one so ids don't leak.
          err instanceof HttpErrorResponse && (err.status === 404 || err.status === 403)
            ? { state: 'not-found' }
            : { state: 'error', error: errorMessage(err, 'Could not load this booking.') },
        ),
      ),
      startWith<Detail>({ state: 'loading' }),
    );
  }
}
