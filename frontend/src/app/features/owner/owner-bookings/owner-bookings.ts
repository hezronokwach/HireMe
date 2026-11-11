import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  Observable,
  catchError,
  combineLatest,
  filter,
  map,
  of,
  scan,
  startWith,
  switchMap,
} from 'rxjs';
import {
  BookingResponse,
  BookingsService,
  EquipmentService,
  PageResponseBookingResponse,
} from '../../../api';
import { errorMessage } from '../../../core/api-errors';
import { formatDay, formatRange } from '../../../core/dates';
import { ConfirmOptions, confirmAction } from '../../../shared/confirm-dialog';
import {
  BOOKING_STATUSES,
  BookingStatus,
  OwnerAction,
  bookingDays,
  bookingStatusLabel,
  isBookingStatus,
  ownerActions,
} from '../../bookings/booking-labels';
import { formatKes } from '../../equipment/equipment-labels';

export const OWNER_BOOKINGS_PAGE_SIZE = 20;

interface Bookings {
  state: 'loading' | 'ready' | 'error';
  page?: PageResponseBookingResponse;
  error?: string;
}

const ACTIONS: Record<
  OwnerAction,
  {
    label: string;
    icon: string;
    done: string;
    confirm?: (b: BookingResponse, name: string) => ConfirmOptions;
  }
> = {
  confirm: {
    label: 'Confirm',
    icon: 'check_circle',
    done: 'Booking confirmed. Other pending requests for those days were declined.',
  },
  decline: {
    label: 'Decline',
    icon: 'block',
    done: 'Request declined.',
    confirm: (b, name) => ({
      title: 'Decline this request?',
      message: `The client asked for ${name}, ${formatRange(b.startDate!, b.endDate!)}.`,
      confirm: 'Decline',
      danger: true,
    }),
  },
  complete: {
    label: 'Complete',
    icon: 'task_alt',
    done: 'Booking marked as completed.',
    confirm: (b, name) => ({
      title: 'Mark as completed?',
      message: `${name}, ${formatRange(b.startDate!, b.endDate!)}. Do this once the equipment is back.`,
      confirm: 'Complete',
    }),
  },
  cancel: {
    label: 'Cancel',
    icon: 'cancel',
    done: 'Booking cancelled.',
    confirm: (b, name) => ({
      title: 'Cancel this booking?',
      message: `${name}, ${formatRange(b.startDate!, b.endDate!)}. The client loses the booking and the days become free.`,
      confirm: 'Cancel booking',
      danger: true,
    }),
  },
};

/** Bookings on the owner's equipment, filtered by status, with confirm / decline / complete / cancel. */
@Component({
  selector: 'app-owner-bookings',
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
  ],
  templateUrl: './owner-bookings.html',
  styleUrl: './owner-bookings.scss',
})
export class OwnerBookings {
  private readonly bookingsApi = inject(BookingsService);
  private readonly equipmentApi = inject(EquipmentService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly statuses = BOOKING_STATUSES;
  protected readonly actions = ACTIONS;
  protected readonly ownerActions = ownerActions;
  protected readonly bookingStatusLabel = bookingStatusLabel;
  protected readonly bookingDays = bookingDays;
  protected readonly formatRange = formatRange;
  protected readonly formatDay = formatDay;
  protected readonly formatKes = formatKes;
  protected readonly pageSize = OWNER_BOOKINGS_PAGE_SIZE;

  /** The status filter lives in the URL (`?status=PENDING`), so a link can open the requests. */
  protected readonly status = toSignal(
    this.route.queryParamMap.pipe(
      map((params) => {
        const value = params.get('status');
        return isBookingStatus(value) ? value : null;
      }),
    ),
    { initialValue: null as BookingStatus | null },
  );
  protected readonly pageIndex = signal(0);
  private readonly reload = signal(0);
  protected readonly busy = signal<ReadonlySet<number>>(new Set());

  protected readonly bookings = toSignal(
    combineLatest([
      toObservable(this.status),
      toObservable(this.pageIndex),
      toObservable(this.reload),
    ]).pipe(
      switchMap(([status, page]) => this.load(status, page)),
      scan((previous, next) =>
        next.state === 'loading' ? { ...next, page: previous.page } : next,
      ),
    ),
    { initialValue: { state: 'loading' } as Bookings },
  );

  /** Names of the owner's listings, so rows can show them (bookings only carry the id). */
  private readonly names = toSignal(
    this.equipmentApi.getMyEquipment(0, 100, 'name,asc').pipe(
      map((page) => new Map((page.content ?? []).map((e) => [e.id!, e.name ?? '']))),
      catchError(() => of(new Map<number, string>())),
    ),
    { initialValue: new Map<number, string>() },
  );
  protected readonly equipmentName = computed(() => {
    const names = this.names();
    return (id: number | undefined) => (id !== undefined && names.get(id)) || `Listing #${id}`;
  });

  protected filterBy(status: BookingStatus | null): void {
    this.pageIndex.set(0);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: status ? { status } : {},
    });
  }

  protected changePage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  protected act(booking: BookingResponse, action: OwnerAction): void {
    const spec = ACTIONS[action];
    const ask = spec.confirm
      ? confirmAction(this.dialog, spec.confirm(booking, this.equipmentName()(booking.equipmentId)))
      : of(true);
    ask.pipe(filter(Boolean)).subscribe(() => this.send(booking, action));
  }

  private send(booking: BookingResponse, action: OwnerAction): void {
    const id = booking.bookingId!;
    const request =
      action === 'confirm'
        ? this.bookingsApi.confirmBooking(id)
        : action === 'complete'
          ? this.bookingsApi.completeBooking(id)
          : this.bookingsApi.cancelBooking(id);
    this.busy.update((ids) => new Set(ids).add(id));
    const finish = () =>
      this.busy.update((ids) => {
        const next = new Set(ids);
        next.delete(id);
        return next;
      });
    request.subscribe({
      next: () => {
        finish();
        this.snackBar.open(ACTIONS[action].done, 'OK', { duration: 5000 });
        this.reload.update((n) => n + 1);
      },
      error: (err: unknown) => {
        finish();
        this.snackBar.open(errorMessage(err), 'OK', { duration: 8000 });
        // The booking may have changed meanwhile (e.g. confirmed elsewhere): show its real state.
        this.reload.update((n) => n + 1);
      },
    });
  }

  private load(status: BookingStatus | null, page: number): Observable<Bookings> {
    return this.bookingsApi
      .getBookingsForMyEquipment(status ?? undefined, page, this.pageSize, 'startDate,asc')
      .pipe(
        map((result): Bookings => ({ state: 'ready', page: result })),
        catchError((err: unknown) =>
          of<Bookings>({ state: 'error', error: errorMessage(err, 'Could not load bookings.') }),
        ),
        startWith<Bookings>({ state: 'loading' }),
      );
  }
}
