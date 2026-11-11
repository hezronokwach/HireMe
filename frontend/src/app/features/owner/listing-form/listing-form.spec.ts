import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { EquipmentResponse, provideApi } from '../../../api';
import { AuthService, Role } from '../../../core/auth/auth.service';
import { dialogProviders, fakeDialog, fakeSnackBar } from '../testing';
import { ListingForm, changedFields } from './listing-form';

const SAVED = {
  id: 7,
  ownerId: 5,
  name: 'Tractor',
  category: 'AGRICULTURAL',
  location: 'Nakuru',
  dailyRateKes: 6000,
  description: 'Old description',
  equipmentStatus: 'AVAILABLE',
} as EquipmentResponse;

describe('changedFields', () => {
  const same = {
    name: 'Tractor',
    category: 'AGRICULTURAL' as const,
    location: 'Nakuru',
    dailyRateKes: 6000,
    description: 'Old description',
    status: 'AVAILABLE' as const,
  };

  it('is empty when nothing changed (text compared trimmed)', () => {
    expect(changedFields(SAVED, { ...same, name: '  Tractor ' })).toEqual({});
  });

  it('holds only the changed fields', () => {
    expect(
      changedFields(SAVED, {
        ...same,
        dailyRateKes: 6500,
        status: 'UNDER_MAINTENANCE',
        description: '',
      }),
    ).toEqual({ dailyRateKes: 6500, status: 'UNDER_MAINTENANCE', description: '' });
  });
});

describe('ListingForm', () => {
  let http: HttpTestingController;
  let snackBar: ReturnType<typeof fakeSnackBar>;
  let harness: RouterTestingHarness;
  let session: { userId: number; role: Role };
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    snackBar = fakeSnackBar();
    session = { userId: 5, role: 'OWNER' };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(''),
        provideRouter(
          [
            { path: 'my-listings/new', component: ListingForm },
            { path: 'my-listings/:id/edit', component: ListingForm },
          ],
          withComponentInputBinding(),
        ),
        ...dialogProviders(fakeDialog(), snackBar),
        {
          provide: AuthService,
          useValue: {
            currentSession: () => session,
            hasRole: (...roles: Role[]) => roles.includes(session.role),
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
  });

  afterEach(() => http.verify());

  async function open(url: string, aiEnabled = true): Promise<HTMLElement> {
    await harness.navigateByUrl(url, ListingForm);
    http.expectOne('/api/v1/ai/status').flush({ enabled: aiEnabled });
    await settle();
    return harness.routeNativeElement as HTMLElement;
  }

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  function type(el: HTMLElement, name: string, value: string): void {
    const input = el.querySelector<HTMLInputElement | HTMLTextAreaElement>(
      `[formControlName="${name}"]`,
    )!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function pickCategory(component: ListingForm, value: string): void {
    (
      component as unknown as { form: { controls: { category: { setValue(v: string): void } } } }
    ).form.controls.category.setValue(value);
  }

  function submit(el: HTMLElement): void {
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
  }

  it('creates a listing with trimmed values, then opens My listings', async () => {
    const el = await open('/my-listings/new');
    type(el, 'name', ' Disc plough ');
    type(el, 'location', 'Eldoret ');
    type(el, 'dailyRateKes', '2500');
    pickCategory(harness.routeDebugElement!.componentInstance, 'AGRICULTURAL');
    submit(el);

    const create = http.expectOne('/api/v1/equipment');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({
      name: 'Disc plough',
      category: 'AGRICULTURAL',
      location: 'Eldoret',
      dailyRateKes: 2500,
    });
    create.flush({ ...SAVED, id: 8, name: 'Disc plough' });
    await settle();

    expect(snackBar.messages).toEqual(['“Disc plough” is listed.']);
    expect(navigate).toHaveBeenCalledWith(['/my-listings']);
  });

  it('refuses a missing name, category or location, and a rate of zero', async () => {
    const el = await open('/my-listings/new');
    type(el, 'dailyRateKes', '0');
    submit(el);
    await settle();

    expect(el.textContent).toContain('Give the equipment a name');
    expect(el.textContent).toContain('Where is it?');
    expect(el.textContent).toContain('The rate must be more than zero');
    http.expectNone('/api/v1/equipment');
  });

  it('edits by sending only the changed fields', async () => {
    const el = await open('/my-listings/7/edit');
    http.expectOne('/api/v1/equipment/7').flush(SAVED);
    await settle();
    expect(el.querySelector<HTMLInputElement>('[formControlName="name"]')!.value).toBe('Tractor');

    type(el, 'dailyRateKes', '6500');
    submit(el);

    const update = http.expectOne('/api/v1/equipment/7');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ dailyRateKes: 6500 });
    update.flush({ ...SAVED, dailyRateKes: 6500 });
    await settle();
    expect(snackBar.messages).toEqual(['Saved “Tractor”.']);
  });

  it('saving an unchanged listing sends nothing', async () => {
    const el = await open('/my-listings/7/edit');
    http.expectOne('/api/v1/equipment/7').flush(SAVED);
    await settle();

    submit(el);
    await settle();

    http.expectNone((r) => r.method === 'PUT');
    expect(snackBar.messages).toEqual(['Nothing to save: no changes.']);
  });

  it("won't edit someone else's listing", async () => {
    session = { userId: 6, role: 'OWNER' };
    const el = await open('/my-listings/7/edit');
    http.expectOne('/api/v1/equipment/7').flush(SAVED);
    await settle();

    expect(el.textContent).toContain('You can only edit your own listings.');
    expect(el.querySelector('form')).toBeNull();
  });

  it('fills a draft from the AI, which the owner accepts piece by piece', async () => {
    const el = await open('/my-listings/new');
    const write = () => el.querySelector<HTMLButtonElement>('[data-testid="write-for-me"]')!;
    expect(write().disabled).toBe(true);

    type(el, 'name', 'Massey 385');
    type(el, 'location', 'Nakuru');
    pickCategory(harness.routeDebugElement!.componentInstance, 'AGRICULTURAL');
    const notes = el.querySelector<HTMLInputElement>('.ai input')!;
    notes.value = '85 hp, 2019';
    notes.dispatchEvent(new Event('input'));
    await settle();
    write().click();

    const ai = http.expectOne('/api/v1/equipment/ai/describe');
    expect(ai.request.body).toEqual({
      name: 'Massey 385',
      category: 'AGRICULTURAL',
      location: 'Nakuru',
      notes: '85 hp, 2019',
    });
    ai.flush({
      title: 'Massey Ferguson 385 tractor, 85 hp',
      description: 'A reliable 2019 tractor.',
      suggestedDailyRateKes: 6500,
      rateReason: 'Similar tractors in Nakuru go for 6,000 to 7,500.',
      comparableListings: 3,
    });
    await settle();

    expect(el.querySelector('[data-testid="suggested-rate"]')?.textContent).toContain(
      'Similar tractors',
    );
    const draftButtons = [
      ...el.querySelectorAll<HTMLButtonElement>('[data-testid="draft"] button'),
    ];
    draftButtons.find((b) => b.textContent?.includes('Use name and description'))!.click();
    draftButtons.find((b) => b.textContent?.includes('Use KES'))!.click();
    await settle();

    expect(el.querySelector<HTMLInputElement>('[formControlName="name"]')!.value).toBe(
      'Massey Ferguson 385 tractor, 85 hp',
    );
    expect(el.querySelector<HTMLTextAreaElement>('[formControlName="description"]')!.value).toBe(
      'A reliable 2019 tractor.',
    );
    expect(el.querySelector<HTMLInputElement>('[formControlName="dailyRateKes"]')!.value).toBe(
      '6500',
    );
  });

  it('says why when the AI cannot write a draft', async () => {
    const el = await open('/my-listings/new');
    type(el, 'name', 'Tent');
    pickCategory(harness.routeDebugElement!.componentInstance, 'EVENT_SETUP');
    await settle();
    el.querySelector<HTMLButtonElement>('[data-testid="write-for-me"]')!.click();
    http
      .expectOne('/api/v1/equipment/ai/describe')
      .flush(
        { status: 429, message: 'Too many AI requests. Try again in 30 seconds.' },
        { status: 429, statusText: 'Too Many Requests' },
      );
    await settle();
    expect(el.querySelector('.ai [role="alert"]')?.textContent).toContain(
      'Try again in 30 seconds',
    );
  });

  it('hides the helper when AI is off', async () => {
    const el = await open('/my-listings/new', false);
    expect(el.querySelector('[data-testid="write-for-me"]')).toBeNull();
  });
});
