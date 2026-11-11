import { provideDayFirstDates } from '../../../core/day-first-dates';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, catchError, combineLatest, map, of, scan, startWith, switchMap } from 'rxjs';
import {
  AIService,
  AiSearchResponseInterpretedByEnum,
  EquipmentService,
  PageResponseEquipmentResponse,
} from '../../../api';
import { errorMessage } from '../../../core/api-errors';
import { addDays, fromIsoDay, startOfToday, toIsoDay } from '../../../core/dates';
import {
  CATEGORIES,
  Category,
  categoryIcon,
  categoryLabel,
  formatKes,
  statusLabel,
} from '../equipment-labels';
import {
  EquipmentQuery,
  FilterKey,
  PAGE_SIZES,
  SORT_OPTIONS,
  SortOption,
  filterChips,
  fromCriteria,
  parseQuery,
  toParams,
  withoutFilter,
  withoutFilters,
} from '../equipment-query';

interface Results {
  state: 'loading' | 'ready' | 'error';
  /** While loading, the previous page stays here so the list doesn't flash empty. */
  page?: PageResponseEquipmentResponse;
  error?: string;
}

/** What the last AI search did, shown above the results until the user changes the filters. */
interface AiOutcome {
  query: string;
  interpretedBy: AiSearchResponseInterpretedByEnum | undefined;
  notice: string | null;
}

@Component({
  selector: 'app-equipment-list',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
  ],
  // Day-first date pickers (kept out of the initial bundle).
  providers: [provideDayFirstDates()],
  templateUrl: './equipment-list.html',
  styleUrl: './equipment-list.scss',
})
export class EquipmentList {
  private readonly equipmentApi = inject(EquipmentService);
  private readonly aiApi = inject(AIService);
  protected readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly categories = CATEGORIES;
  protected readonly sortOptions = SORT_OPTIONS;
  protected readonly pageSizes = PAGE_SIZES;
  protected readonly today = startOfToday();
  protected readonly lastBookableDay = addDays(this.today, 365);
  protected readonly categoryLabel = categoryLabel;
  protected readonly categoryIcon = categoryIcon;
  protected readonly statusLabel = statusLabel;
  protected readonly formatKes = formatKes;

  /** The search in the URL. */
  protected readonly query = toSignal(this.route.queryParams.pipe(map(parseQuery)), {
    initialValue: parseQuery(this.route.snapshot.queryParams),
  });
  protected readonly chips = computed(() => filterChips(this.query()));
  private readonly reload = signal(0);

  protected readonly results = toSignal(
    combineLatest([toObservable(this.query), toObservable(this.reload)]).pipe(
      switchMap(([query]) => this.search(query)),
      scan((previous, next) =>
        next.state === 'loading' ? { ...next, page: previous.page } : next,
      ),
    ),
    { initialValue: { state: 'loading' } as Results },
  );

  protected readonly aiEnabled = toSignal(
    this.aiApi.getAiStatus().pipe(
      map((status) => status.enabled === true),
      catchError(() => of(false)),
    ),
    { initialValue: true },
  );
  protected readonly aiSearching = signal(false);
  protected readonly aiOutcome = signal<AiOutcome | null>(null);
  protected readonly aiError = signal<string | null>(null);
  protected readonly aiForm = this.fb.group({
    text: ['', Validators.maxLength(500)],
  });

  protected readonly showFilters = signal(
    globalThis.matchMedia?.('(min-width: 960px)').matches ?? true,
  );
  protected readonly filterForm = this.fb.group({
    category: this.fb.control<Category | ''>(''),
    location: [''],
    minRate: this.fb.control<number | null>(null, Validators.min(0)),
    maxRate: this.fb.control<number | null>(null, Validators.min(0)),
    q: [''],
    from: this.fb.control<Date | null>(null),
    to: this.fb.control<Date | null>(null),
    sort: this.fb.control<SortOption>('createdAt,desc'),
  });

  constructor() {
    // Keep the form showing what the URL says (after AI searches, chip removals, back button).
    effect(() => {
      const q = this.query();
      this.filterForm.reset(
        {
          category: q.category ?? '',
          location: q.location ?? '',
          minRate: q.minRate,
          maxRate: q.maxRate,
          q: q.q ?? '',
          from: fromIsoDay(q.from),
          to: fromIsoDay(q.to),
          sort: q.sort,
        },
        { emitEvent: false },
      );
    });
  }

  protected applyFilters(): void {
    if (this.filterForm.invalid) {
      this.filterForm.markAllAsTouched();
      return;
    }
    const v = this.filterForm.getRawValue();
    let [minRate, maxRate] = [v.minRate, v.maxRate];
    if (minRate !== null && maxRate !== null && minRate > maxRate) {
      [minRate, maxRate] = [maxRate, minRate];
    }
    this.aiOutcome.set(null);
    this.go({
      ...this.query(),
      category: v.category || null,
      location: v.location.trim() || null,
      minRate,
      maxRate,
      q: v.q.trim() || null,
      from: v.from ? toIsoDay(v.from) : null,
      to: v.to ? toIsoDay(v.to) : v.from ? toIsoDay(v.from) : null,
      sort: v.sort,
      page: 0,
    });
  }

  protected changeSort(sort: SortOption): void {
    this.go({ ...this.query(), sort, page: 0 });
  }

  protected removeChip(key: FilterKey): void {
    this.go(withoutFilter(this.query(), key));
  }

  protected clearFilters(): void {
    this.aiOutcome.set(null);
    this.go(withoutFilters(this.query()));
  }

  protected changePage(event: PageEvent): void {
    const size = event.pageSize;
    this.go({ ...this.query(), page: size === this.query().size ? event.pageIndex : 0, size });
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  /**
   * Sends the sentence to the AI search. The filters it works out go into the URL like any other
   * filters, so they show as chips and in the filter form, where the user can change them.
   */
  protected searchWithAi(): void {
    const text = this.aiForm.controls.text.value.trim();
    if (!text || this.aiSearching()) {
      return;
    }
    this.aiSearching.set(true);
    this.aiError.set(null);
    const current = this.query();
    this.aiApi.searchEquipmentWithAi({ query: text }, 0, current.size, current.sort).subscribe({
      next: (response) => {
        this.aiSearching.set(false);
        this.aiOutcome.set({
          query: response.query ?? text,
          interpretedBy: response.interpretedBy,
          notice: response.notice ?? null,
        });
        this.go(fromCriteria(response.filters, current));
      },
      error: (err: unknown) => {
        this.aiSearching.set(false);
        this.aiError.set(
          errorMessage(err, 'The search did not work. Try again, or use the filters below.'),
        );
      },
    });
  }

  private go(query: EquipmentQuery): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: toParams(query) });
  }

  private search(query: EquipmentQuery): Observable<Results> {
    return this.equipmentApi
      .searchEquipment(
        query.category ?? undefined,
        query.location ?? undefined,
        query.minRate ?? undefined,
        query.maxRate ?? undefined,
        query.q ?? undefined,
        query.from ?? undefined,
        query.to ?? undefined,
        query.page,
        query.size,
        query.sort,
      )
      .pipe(
        map((page): Results => ({ state: 'ready', page })),
        catchError((err: unknown) =>
          of<Results>({ state: 'error', error: errorMessage(err, 'Could not load equipment.') }),
        ),
        startWith<Results>({ state: 'loading' }),
      );
  }
}
