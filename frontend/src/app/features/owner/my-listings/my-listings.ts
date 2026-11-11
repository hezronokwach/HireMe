import { Component, inject, signal } from '@angular/core';
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
  map,
  of,
  scan,
  startWith,
  switchMap,
} from 'rxjs';
import {
  EquipmentResponse,
  EquipmentService,
  PageResponseEquipmentResponse,
  UpdateEquipmentRequestStatusEnum,
} from '../../../api';
import { errorMessage } from '../../../core/api-errors';
import { confirmAction } from '../../../shared/confirm-dialog';
import {
  categoryIcon,
  categoryLabel,
  formatKes,
  statusLabel,
} from '../../equipment/equipment-labels';

export const MY_LISTINGS_PAGE_SIZE = 12;

interface Listings {
  state: 'loading' | 'ready' | 'error';
  /** While loading, the previous page stays so the list doesn't flash empty. */
  page?: PageResponseEquipmentResponse;
  error?: string;
}

/** The owner's own listings, with edit, pause/resume and delete. */
@Component({
  selector: 'app-my-listings',
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
  ],
  templateUrl: './my-listings.html',
  styleUrl: './my-listings.scss',
})
export class MyListings {
  private readonly api = inject(EquipmentService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly categoryLabel = categoryLabel;
  protected readonly categoryIcon = categoryIcon;
  protected readonly statusLabel = statusLabel;
  protected readonly formatKes = formatKes;
  protected readonly pageSize = MY_LISTINGS_PAGE_SIZE;

  protected readonly pageIndex = signal(0);
  private readonly reload = signal(0);
  /** Ids of listings with a change on the way, so their buttons are disabled meanwhile. */
  protected readonly busy = signal<ReadonlySet<number>>(new Set());

  protected readonly listings = toSignal(
    combineLatest([toObservable(this.pageIndex), toObservable(this.reload)]).pipe(
      switchMap(([page]) => this.load(page)),
      scan((previous, next) =>
        next.state === 'loading' ? { ...next, page: previous.page } : next,
      ),
    ),
    { initialValue: { state: 'loading' } as Listings },
  );

  protected changePage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  /** Stops or restarts new bookings; existing bookings are not touched. */
  protected toggleBookings(item: EquipmentResponse): void {
    const pause = item.equipmentStatus === 'AVAILABLE';
    const status = pause
      ? UpdateEquipmentRequestStatusEnum.UNDER_MAINTENANCE
      : UpdateEquipmentRequestStatusEnum.AVAILABLE;
    this.run(item, this.api.updateEquipment(item.id!, { status }), () =>
      pause
        ? `“${item.name}” no longer takes new bookings.`
        : `“${item.name}” takes bookings again.`,
    );
  }

  protected remove(item: EquipmentResponse): void {
    confirmAction(this.dialog, {
      title: 'Delete this listing?',
      message: `“${item.name}” will be removed for good. Past bookings stay in your records.`,
      confirm: 'Delete',
      danger: true,
    })
      .pipe(filter(Boolean))
      .subscribe(() =>
        this.run(
          item,
          this.api.deleteEquipment(item.id!),
          () => `“${item.name}” was deleted.`,
          () => {
            // The last listing on a later page is gone: show the page before it.
            if (this.listings().page?.content?.length === 1 && this.pageIndex() > 0) {
              this.pageIndex.update((page) => page - 1);
              return true;
            }
            return false;
          },
        ),
      );
  }

  private run(
    item: EquipmentResponse,
    request: Observable<unknown>,
    done: () => string,
    /** Returns true when it already triggered a new load. */
    afterSuccess: () => boolean = () => false,
  ): void {
    const id = item.id!;
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
        this.snackBar.open(done(), 'OK', { duration: 4000 });
        if (!afterSuccess()) {
          this.reload.update((n) => n + 1);
        }
      },
      error: (err: unknown) => {
        finish();
        this.snackBar.open(errorMessage(err), 'OK', { duration: 8000 });
      },
    });
  }

  private load(page: number): Observable<Listings> {
    return this.api.getMyEquipment(page, this.pageSize).pipe(
      map((result): Listings => ({ state: 'ready', page: result })),
      catchError((err: unknown) =>
        of<Listings>({ state: 'error', error: errorMessage(err, 'Could not load your listings.') }),
      ),
      startWith<Listings>({ state: 'loading' }),
    );
  }
}
