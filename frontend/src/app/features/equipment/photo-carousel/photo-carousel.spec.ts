import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PhotoCarousel } from './photo-carousel';

describe('PhotoCarousel', () => {
  let fixture: ComponentFixture<PhotoCarousel>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [PhotoCarousel] });
    fixture = TestBed.createComponent(PhotoCarousel);
    fixture.componentRef.setInput('name', 'Tractor A');
  });

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  it('shows every photo with alt text, and moves with the arrows, wrapping round', async () => {
    fixture.componentRef.setInput('photos', ['/p/1', '/p/2', '/p/3']);
    await fixture.whenStable();

    const slides = el().querySelectorAll('[data-testid="slide"] img');
    expect(slides.length).toBe(3);
    expect(slides[1].getAttribute('alt')).toBe('Tractor A, photo 2');
    expect(el().querySelector('[data-testid="counter"]')?.textContent?.trim()).toBe('1 / 3');

    el().querySelector<HTMLButtonElement>('[data-testid="next"]')!.click();
    await fixture.whenStable();
    expect(el().querySelector('[data-testid="counter"]')?.textContent?.trim()).toBe('2 / 3');

    el().querySelector<HTMLButtonElement>('[data-testid="prev"]')!.click();
    el().querySelector<HTMLButtonElement>('[data-testid="prev"]')!.click();
    await fixture.whenStable();
    expect(el().querySelector('[data-testid="counter"]')?.textContent?.trim()).toBe('3 / 3');
  });

  it('jumps to a photo from its thumbnail, and follows the arrow keys', async () => {
    fixture.componentRef.setInput('photos', ['/p/1', '/p/2', '/p/3']);
    await fixture.whenStable();

    el().querySelectorAll<HTMLButtonElement>('.thumb')[2].click();
    await fixture.whenStable();
    expect(el().querySelectorAll('.thumb')[2].getAttribute('aria-selected')).toBe('true');

    el()
      .querySelector('.track')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    await fixture.whenStable();
    expect(el().querySelector('[data-testid="counter"]')?.textContent?.trim()).toBe('1 / 3');
  });

  it('with one photo, shows no arrows, dots or thumbnails', async () => {
    fixture.componentRef.setInput('photos', ['/p/1']);
    await fixture.whenStable();

    expect(el().querySelector('[data-testid="next"]')).toBeNull();
    expect(el().querySelector('.thumbs')).toBeNull();
    expect(el().querySelectorAll('[data-testid="slide"]').length).toBe(1);
  });
});
