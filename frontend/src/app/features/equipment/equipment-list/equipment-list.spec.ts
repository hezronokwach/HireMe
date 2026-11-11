import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { EquipmentResponse, PageResponseEquipmentResponse, provideApi } from '../../../api';
import { EquipmentList } from './equipment-list';

const TRACTOR: EquipmentResponse = {
  id: 1,
  name: 'Tractor A Massey Ferguson',
  category: 'AGRICULTURAL' as EquipmentResponse['category'],
  location: 'Nakuru',
  dailyRateKes: 6000,
  equipmentStatus: 'AVAILABLE' as EquipmentResponse['equipmentStatus'],
};
const TENT: EquipmentResponse = {
  ...TRACTOR,
  id: 2,
  name: 'Wedding tent',
  category: 'EVENT_SETUP' as EquipmentResponse['category'],
  equipmentStatus: 'UNDER_MAINTENANCE' as EquipmentResponse['equipmentStatus'],
};

function page(
  content: EquipmentResponse[],
  totalElements = content.length,
): PageResponseEquipmentResponse {
  return { content, page: 0, size: 12, totalElements, totalPages: Math.ceil(totalElements / 12) };
}

describe('EquipmentList', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(''),
        provideRouter([{ path: 'equipment', component: EquipmentList }]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  async function open(url: string, aiEnabled = true): Promise<HTMLElement> {
    await harness.navigateByUrl(url, EquipmentList);
    http.expectOne('/api/v1/ai/status').flush({ enabled: aiEnabled });
    await harness.fixture.whenStable();
    return harness.routeNativeElement as HTMLElement;
  }

  function searchRequest(): TestRequest {
    return http.expectOne((r) => r.url === '/api/v1/equipment' && r.method === 'GET');
  }

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  it('sends the URL filters to the API and shows the results as cards', async () => {
    const el = await open(
      '/equipment?category=AGRICULTURAL&location=Nakuru&maxRate=8000&sort=dailyRateKes,asc',
    );

    const request = searchRequest();
    const params = request.request.params;
    expect(params.get('category')).toBe('AGRICULTURAL');
    expect(params.get('location')).toBe('Nakuru');
    expect(params.get('maxRate')).toBe('8000');
    expect(decodeURIComponent(params.get('sort')!)).toBe('dailyRateKes,asc');
    expect(params.get('page')).toBe('0');
    expect(params.get('size')).toBe('12');
    request.flush(page([TRACTOR, TENT]));
    await settle();

    const cards = el.querySelectorAll('.card');
    expect(cards.length).toBe(2);
    expect(cards[0].textContent).toContain('Tractor A Massey Ferguson');
    expect(cards[0].textContent).toMatch(/KES\s?6,000/);
    expect(cards[0].getAttribute('href')).toBe('/equipment/1');
    expect(cards[1].textContent).toContain('Under maintenance');
    expect(el.textContent).toContain('2 listings');
    expect([...el.querySelectorAll('mat-chip')].map((c) => c.textContent?.trim())).toEqual([
      expect.stringContaining('Agricultural'),
      expect.stringContaining('In Nakuru'),
      expect.stringMatching(/Up to KES\s?8,000\/day/),
    ]);
  });

  it('removing a chip drops that filter from the URL', async () => {
    const el = await open('/equipment?category=AGRICULTURAL&location=Nakuru');
    searchRequest().flush(page([TRACTOR]));
    await settle();

    el.querySelector<HTMLButtonElement>('button[aria-label="Remove In Nakuru"]')!.click();
    await settle();

    expect(TestBed.inject(Router).url).toBe('/equipment?category=AGRICULTURAL');
    expect(searchRequest().request.params.has('location')).toBe(false);
  });

  it('applying the filter form puts the filters in the URL, back on page one', async () => {
    const el = await open('/equipment?page=2');
    searchRequest().flush(page([TRACTOR], 30));
    await settle();

    const location = el.querySelector<HTMLInputElement>('input[formControlName="location"]')!;
    location.value = ' Eldoret ';
    location.dispatchEvent(new Event('input'));
    const min = el.querySelector<HTMLInputElement>('input[formControlName="minRate"]')!;
    min.value = '9000';
    min.dispatchEvent(new Event('input'));
    const max = el.querySelector<HTMLInputElement>('input[formControlName="maxRate"]')!;
    max.value = '2000';
    max.dispatchEvent(new Event('input'));
    el.querySelector<HTMLFormElement>('form#filters')!.dispatchEvent(new Event('submit'));
    await settle();

    // A backwards price range is swapped rather than rejected.
    expect(TestBed.inject(Router).url).toBe(
      '/equipment?location=Eldoret&minRate=2000&maxRate=9000',
    );
    searchRequest().flush(page([]));
  });

  it('hides the hire assistant when AI is off', async () => {
    const el = await open('/equipment', false);
    searchRequest().flush(page([]));
    await settle();
    expect(el.querySelector('app-assistant-panel')).toBeNull();
    expect(el.querySelector('h1')?.textContent).toContain('Find equipment');
  });

  it('shows the hire assistant when AI is on, above the filters', async () => {
    const el = await open('/equipment', true);
    searchRequest().flush(page([]));
    await settle();
    expect(el.querySelector('app-assistant-panel')).not.toBeNull();
  });

  it('shows an error with a retry button, and an empty state with a way out', async () => {
    const el = await open('/equipment?location=Nowhere');
    searchRequest().flush(
      { message: 'Database down' },
      { status: 500, statusText: 'Server Error' },
    );
    await settle();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Database down');
    el.querySelector<HTMLButtonElement>('.state button')!.click();
    await settle();
    searchRequest().flush(page([]));
    await settle();

    expect(el.textContent).toContain('No equipment matches these filters.');
    el.querySelector<HTMLButtonElement>('.state button')!.click();
    await settle();
    expect(TestBed.inject(Router).url).toBe('/equipment');
    searchRequest().flush(page([]));
    await settle();
    expect(el.textContent).toContain('No equipment has been listed yet.');
  });

  it('ignores junk in the URL', async () => {
    await open('/equipment?category=BOATS&size=5000&sort=password,asc');
    const params = searchRequest().request.params;
    expect(params.has('category')).toBe(false);
    expect(params.get('size')).toBe('12');
    expect(decodeURIComponent(params.get('sort')!)).toBe('createdAt,desc');
  });
});
