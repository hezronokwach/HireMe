import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideApi } from '../../../api';
import { MAX_PHOTOS, PhotoManager } from './photo-manager';

// jsdom can't decode images, so shrinking falls back to the original file; that's what these tests send.
function photo(name = 'photo.jpg', type = 'image/jpeg'): File {
  return new File([new Uint8Array([0xff, 0xd8, 0xff, 1, 2, 3])], name, { type });
}

describe('PhotoManager', () => {
  let fixture: ComponentFixture<PhotoManager>;
  let http: HttpTestingController;

  beforeEach(async () => {
    globalThis.URL.createObjectURL ??= () => 'blob:preview';
    globalThis.URL.revokeObjectURL ??= () => undefined;
    TestBed.configureTestingModule({
      imports: [PhotoManager],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi('')],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PhotoManager);
  });

  afterEach(() => http.verify());

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  async function pick(...files: File[]): Promise<void> {
    const input = el().querySelector<HTMLInputElement>('[data-testid="photo-input"]')!;
    Object.defineProperty(input, 'files', { value: files, configurable: true });
    input.dispatchEvent(new Event('change'));
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  }

  async function settle(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  }

  it('on an existing listing, uploads each picked photo straight away', async () => {
    fixture.componentRef.setInput('equipmentId', 5);
    fixture.componentRef.setInput('photoUrls', ['/api/v1/equipment/5/photos/1']);
    await fixture.whenStable();
    expect(el().querySelectorAll('[data-testid="photo"]').length).toBe(1);
    expect(el().textContent).toContain('Cover');

    await pick(photo());
    const upload = http.expectOne('/api/v1/equipment/5/photos');
    expect(upload.request.method).toBe('POST');
    expect(upload.request.body instanceof FormData).toBe(true);
    upload.flush({ id: 2, url: '/api/v1/equipment/5/photos/2' });
    await settle();

    const srcs = [...el().querySelectorAll('[data-testid="photo"] img')].map((i) =>
      i.getAttribute('src'),
    );
    expect(srcs).toEqual(['/api/v1/equipment/5/photos/1', '/api/v1/equipment/5/photos/2']);
  });

  it('on a new listing, keeps the photos until the listing exists, then uploads them in order', async () => {
    await fixture.whenStable();
    await pick(photo('a.jpg'), photo('b.png', 'image/png'));
    http.expectNone('/api/v1/equipment/undefined/photos');
    expect(fixture.componentInstance.hasPending()).toBe(true);
    expect(el().querySelectorAll('[data-testid="photo"]').length).toBe(2);

    const done = fixture.componentInstance.uploadPending(9);
    await settle();
    http
      .expectOne('/api/v1/equipment/9/photos')
      .flush({ id: 1, url: '/api/v1/equipment/9/photos/1' });
    await settle();
    http
      .expectOne('/api/v1/equipment/9/photos')
      .flush({ id: 2, url: '/api/v1/equipment/9/photos/2' });

    expect(await done).toBe(0);
    expect(fixture.componentInstance.hasPending()).toBe(false);
  });

  it('refuses files that are not photos, and stops at the limit', async () => {
    await fixture.whenStable();
    await pick(photo('notes.pdf', 'application/pdf'));
    expect(el().querySelector('[role="alert"]')?.textContent).toContain(
      "isn't a JPEG, PNG or WebP",
    );

    await pick(...Array.from({ length: MAX_PHOTOS + 2 }, (_, i) => photo(`p${i}.jpg`)));
    expect(el().querySelectorAll('[data-testid="photo"]').length).toBe(MAX_PHOTOS);
    expect(el().querySelector('[data-testid="photo-input"]')).toBeNull();
    expect(el().querySelector('[role="alert"]')?.textContent).toContain(`${MAX_PHOTOS} photos`);
  });

  it('removes a photo from the API, and makes another the cover', async () => {
    fixture.componentRef.setInput('equipmentId', 5);
    fixture.componentRef.setInput('photoUrls', [
      '/api/v1/equipment/5/photos/1',
      '/api/v1/equipment/5/photos/2',
      '/api/v1/equipment/5/photos/3',
    ]);
    await fixture.whenStable();

    el().querySelector<HTMLButtonElement>('[aria-label="Make photo 3 the cover"]')!.click();
    const cover = http.expectOne('/api/v1/equipment/5/photos/3/cover');
    expect(cover.request.method).toBe('PUT');
    cover.flush(null);
    await settle();
    expect(el().querySelector('[data-testid="photo"] img')?.getAttribute('src')).toBe(
      '/api/v1/equipment/5/photos/3',
    );

    el().querySelector<HTMLButtonElement>('[aria-label="Remove photo 2"]')!.click();
    const removal = http.expectOne('/api/v1/equipment/5/photos/1');
    expect(removal.request.method).toBe('DELETE');
    removal.flush(null);
    await settle();
    expect(el().querySelectorAll('[data-testid="photo"]').length).toBe(2);
  });
});
