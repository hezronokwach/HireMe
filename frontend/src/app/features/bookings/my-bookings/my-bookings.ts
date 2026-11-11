import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import {
  Observable,
  catchError,
  combineLatest,
  filter,
  forkJoin,
  map,
  of,
  scan,
  startWith,
  switchMap,
} from 'rxjs';
import {
  BookingResponse,
  BookingsService,
  EquipmentResponse,
  PageResponseBookingResponse,
} from '../../../api';
import { errorMessage } from '../../../core/api-errors';
import { formatDay, formatRange } from '../../../core/dates';
import { confirmAction } from '../../../shared/confirm-dialog';
import { EquipmentLookup } from '../../equipment/equipment-lookup';
import { formatKes } from '../../equipment/equipment-labels';
import { bookingDays, bookingStatusLabel, clientCanCancel } from '../booking-labels';

export const MY_BOOKINGS_PAGE_SIZE = 20;

interface Bookings {
  state: 'loading' | 'ready' | 'error';
  page?: PageResponseBookingResponse;
  /** The listings on this page, by id (null when one was deleted). */
  equipment?: Map<number, EquipmentResponse | null>;
  error?: string;
}

/** The client's bookings, newest request first, with cancel where the API allows it. */
@Component({
  selector: 'app-my-bookings',
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
  ],
  templateUrl: './my-bookings.html',
  styleUrl: './my-bookings.scss',
})
export class MyBookings {
  private readonly api = inject(BookingsService);
  private readonly lookup = inject(EquipmentLookup);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly bookingStatusLabel = bookingStatusLabel;
  protected readonly bookingDays = bookingDays;
  protected readonly clientCanCancel = clientCanCancel;
  protected readonly formatRange = formatRange;
  protected readonly formatDay = formatDay;
  protected readonly formatKes = formatKes;
  protected readonly pageSize = MY_BOOKINGS_PAGE_SIZE;

  protected readonly pageIndex = signal(0);
  private readonly reload = signal(0);
  protected readonly busy = signal<ReadonlySet<number>>(new Set());

  protected readonly bookings = toSignal(
    combineLatest([toObservable(this.pageIndex), toObservable(this.reload)]).pipe(
      switchMap(([page]) => this.load(page)),
      scan((previous, next) =>
        next.state === 'loading'
          ? { ...next, page: previous.page, equipment: previous.equipment }
          : next,
      ),
    ),
    { initialValue: { state: 'loading' } as Bookings },
  );

  protected readonly name = computed(() => {
    const equipment = this.bookings().equipment;
    return (id: number | undefined) => {
      const item = id === undefined ? undefined : equipment?.get(id);
      return item?.name ?? (item === null ? 'Removed listing' : `Listing #${id}`);
    };
  });

  protected changePage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  protected cancel(booking: BookingResponse): void {
    const id = booking.bookingId!;
    confirmAction(this.dialog, {
      title: 'Cancel this booking?',
      message: `${this.name()(booking.equipmentId)}, ${formatRange(booking.startDate!, booking.endDate!)}.`,
      confirm: 'Cancel booking',
      danger: true,
    })
      .pipe(filter(Boolean))
      .subscribe(() => {
        this.busy.update((ids) => new Set(ids).add(id));
        const finish = () =>
          this.busy.update((ids) => {
            const next = new Set(ids);
            next.delete(id);
            return next;
          });
        this.api.cancelBooking(id).subscribe({
          next: () => {
            finish();
            this.snackBar.open('Booking cancelled.', 'OK', { duration: 4000 });
            this.reload.update((n) => n + 1);
          },
          error: (err: unknown) => {
            finish();
            this.snackBar.open(errorMessage(err), 'OK', { duration: 8000 });
            this.reload.update((n) => n + 1);
          },
        });
      });
  }

  private load(page: number): Observable<Bookings> {
    return this.api.getMyBookings(page, this.pageSize).pipe(
      switchMap((result) => {
        const ids = [...new Set((result.content ?? []).map((b) => b.equipmentId!))];
        const listings = ids.length
          ? forkJoin(ids.map((id) => this.lookup.get(id).pipe(map((e) => [id, e] as const))))
          : of([] as (readonly [number, EquipmentResponse | null])[]);
        return listings.pipe(
          map((entries): Bookings => ({
            state: 'ready',
            page: result,
            equipment: new Map(entries),
          })),
        );
      }),
      catchError((err: unknown) =>
        of<Bookings>({ state: 'error', error: errorMessage(err, 'Could not load your bookings.') }),
      ),
      startWith<Bookings>({ state: 'loading' }),
    );
  }
}
