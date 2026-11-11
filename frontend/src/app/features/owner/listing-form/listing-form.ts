import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { Observable, catchError, map, of, startWith, switchMap } from 'rxjs';
import {
  AIService,
  CreateEquipmentRequest,
  DescribeEquipmentRequest,
  EquipmentDescriptionResponse,
  EquipmentResponse,
  EquipmentService,
  UpdateEquipmentRequest,
} from '../../../api';
import { errorMessage, fieldErrors } from '../../../core/api-errors';
import { AuthService } from '../../../core/auth/auth.service';
import {
  CATEGORIES,
  Category,
  EquipmentStatus,
  STATUS_LABELS,
  formatKes,
} from '../../equipment/equipment-labels';

/** Limits shared with the API's validation. */
export const LIMITS = {
  name: 255,
  location: 255,
  description: 1000,
  notes: 500,
  aiName: 100,
} as const;

function positive(control: AbstractControl<number | null>): ValidationErrors | null {
  const value = control.value;
  return value === null || value > 0 ? null : { positive: true };
}

export interface ListingValues {
  name: string;
  category: Category | '';
  location: string;
  dailyRateKes: number | null;
  description: string;
  status: EquipmentStatus;
}

/**
 * The fields of an edit that differ from the saved listing, so only those are sent (the API
 * changes only the fields it receives). Text is compared trimmed.
 */
export function changedFields(
  original: EquipmentResponse,
  values: ListingValues,
): UpdateEquipmentRequest {
  const changes: UpdateEquipmentRequest = {};
  const name = values.name.trim();
  const location = values.location.trim();
  const description = values.description.trim();
  if (name !== (original.name ?? '')) changes.name = name;
  if (values.category && values.category !== original.category) {
    changes.category = values.category as UpdateEquipmentRequest['category'];
  }
  if (location !== (original.location ?? '')) changes.location = location;
  if (description !== (original.description ?? '')) changes.description = description;
  if (values.dailyRateKes !== null && values.dailyRateKes !== original.dailyRateKes) {
    changes.dailyRateKes = values.dailyRateKes;
  }
  if (values.status !== original.equipmentStatus) {
    changes.status = values.status as UpdateEquipmentRequest['status'];
  }
  return changes;
}

interface Loaded {
  state: 'loading' | 'ready' | 'not-found' | 'forbidden' | 'error';
  equipment?: EquipmentResponse;
  error?: string;
}

type Field = 'name' | 'category' | 'location' | 'dailyRateKes' | 'description';

@Component({
  selector: 'app-listing-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
  ],
  templateUrl: './listing-form.html',
  styleUrl: './listing-form.scss',
})
export class ListingForm {
  private readonly equipmentApi = inject(EquipmentService);
  private readonly aiApi = inject(AIService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly fb = inject(NonNullableFormBuilder);

  /** Route parameter; absent when creating a listing. */
  readonly id = input<string>();
  protected readonly editing = computed(() => this.id() !== undefined);

  protected readonly categories = CATEGORIES;
  protected readonly statuses = Object.entries(STATUS_LABELS) as [EquipmentStatus, string][];
  protected readonly limits = LIMITS;
  protected readonly formatKes = formatKes;

  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(LIMITS.name)]],
    category: this.fb.control<Category | ''>('', Validators.required),
    location: ['', [Validators.required, Validators.maxLength(LIMITS.location)]],
    dailyRateKes: this.fb.control<number | null>(null, [Validators.required, positive]),
    description: ['', Validators.maxLength(LIMITS.description)],
    status: this.fb.control<EquipmentStatus>('AVAILABLE'),
  });
  protected readonly notes = this.fb.control('', Validators.maxLength(LIMITS.notes));

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly serverErrors = signal<Partial<Record<Field, string>>>({});

  /** The listing being edited (always 'ready' when creating). */
  protected readonly loaded = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) => (id === undefined ? of<Loaded>({ state: 'ready' }) : this.load(id))),
    ),
    { initialValue: { state: 'loading' } as Loaded },
  );

  protected readonly aiEnabled = toSignal(
    this.aiApi.getAiStatus().pipe(
      map((status) => status.enabled === true),
      catchError(() => of(false)),
    ),
    { initialValue: false },
  );
  protected readonly drafting = signal(false);
  protected readonly draft = signal<EquipmentDescriptionResponse | null>(null);
  protected readonly draftError = signal<string | null>(null);

  constructor() {
    effect(() => {
      const equipment = this.loaded().equipment;
      if (equipment) {
        this.form.reset({
          name: equipment.name ?? '',
          category: (equipment.category as Category | undefined) ?? '',
          location: equipment.location ?? '',
          dailyRateKes: equipment.dailyRateKes ?? null,
          description: equipment.description ?? '',
          status: (equipment.equipmentStatus as EquipmentStatus | undefined) ?? 'AVAILABLE',
        });
      }
    });
  }

  /** The AI needs at least a name (up to 100 characters) and a category. */
  protected canDraft(): boolean {
    const { name, category } = this.form.getRawValue();
    return (
      !!name.trim() &&
      name.trim().length <= LIMITS.aiName &&
      !!category &&
      this.notes.valid &&
      !this.drafting()
    );
  }

  protected writeForMe(): void {
    if (!this.canDraft()) {
      return;
    }
    const v = this.form.getRawValue();
    this.drafting.set(true);
    this.draftError.set(null);
    this.aiApi
      .describeEquipment({
        name: v.name.trim(),
        category: v.category as DescribeEquipmentRequest['category'],
        location: v.location.trim().slice(0, 100) || undefined,
        notes: this.notes.value.trim() || undefined,
      })
      .subscribe({
        next: (draft) => {
          this.drafting.set(false);
          this.draft.set(draft);
        },
        error: (err: unknown) => {
          this.drafting.set(false);
          this.draftError.set(errorMessage(err, 'Could not write a draft. Try again in a moment.'));
        },
      });
  }

  /** Copies the AI's title and description into the form, where the owner can still edit them. */
  protected useDraftText(): void {
    const draft = this.draft();
    if (!draft) return;
    this.form.patchValue({
      name: (draft.title ?? this.form.controls.name.value).slice(0, LIMITS.name),
      description: (draft.description ?? '').slice(0, LIMITS.description),
    });
    this.form.markAsDirty();
  }

  protected useSuggestedRate(): void {
    const rate = this.draft()?.suggestedDailyRateKes;
    if (rate != null) {
      this.form.patchValue({ dailyRateKes: rate });
      this.form.markAsDirty();
    }
  }

  /** The API leaves the rate out (null) when there are too few similar listings to compare. */
  protected hasRate(draft: EquipmentDescriptionResponse): boolean {
    return typeof draft.suggestedDailyRateKes === 'number';
  }

  protected dismissDraft(): void {
    this.draft.set(null);
  }

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const values = this.form.getRawValue();
    const original = this.loaded().equipment;
    let request: Observable<EquipmentResponse>;
    if (original) {
      const changes = changedFields(original, values);
      if (Object.keys(changes).length === 0) {
        this.snackBar.open('Nothing to save: no changes.', 'OK', { duration: 3000 });
        void this.router.navigate(['/my-listings']);
        return;
      }
      request = this.equipmentApi.updateEquipment(original.id!, changes);
    } else {
      request = this.equipmentApi.createEquipment({
        name: values.name.trim(),
        category: values.category as CreateEquipmentRequest['category'],
        location: values.location.trim(),
        dailyRateKes: values.dailyRateKes!,
        description: values.description.trim() || undefined,
      });
    }
    this.saving.set(true);
    this.error.set(null);
    this.serverErrors.set({});
    request.subscribe({
      next: (saved) => {
        this.snackBar.open(
          original ? `Saved “${saved.name}”.` : `“${saved.name}” is listed.`,
          'OK',
          {
            duration: 4000,
          },
        );
        void this.router.navigate(['/my-listings']);
      },
      error: (err: unknown) => {
        const fields = fieldErrors(err);
        this.serverErrors.set(fields);
        this.error.set(
          Object.keys(fields).length
            ? 'Please fix the highlighted fields.'
            : errorMessage(err, 'Could not save the listing.'),
        );
        this.saving.set(false);
      },
    });
  }

  private load(id: string): Observable<Loaded> {
    const numericId = Number(id);
    if (!Number.isInteger(numericId) || numericId <= 0) {
      return of({ state: 'not-found' });
    }
    return this.equipmentApi.getEquipment(numericId).pipe(
      map((equipment): Loaded => {
        const session = this.auth.currentSession();
        const mine = equipment.ownerId === session?.userId || this.auth.hasRole('ADMIN');
        return mine ? { state: 'ready', equipment } : { state: 'forbidden' };
      }),
      catchError((err: unknown) =>
        of<Loaded>(
          err instanceof HttpErrorResponse && err.status === 404
            ? { state: 'not-found' }
            : { state: 'error', error: errorMessage(err, 'Could not load this listing.') },
        ),
      ),
      startWith<Loaded>({ state: 'loading' }),
    );
  }
}
