import { Component, ElementRef, computed, input, signal, viewChild } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { photoSrc } from '../../../core/images';

/**
 * A listing's photos: swipe or scroll sideways (CSS scroll snapping, so it feels native on phones), or use
 * the arrows, the dots, the thumbnails or the arrow keys.
 */
@Component({
  selector: 'app-photo-carousel',
  imports: [MatIconModule],
  templateUrl: './photo-carousel.html',
  styleUrl: './photo-carousel.scss',
})
export class PhotoCarousel {
  readonly photos = input.required<string[]>();
  /** The listing's name, for the photos' alt text. */
  readonly name = input<string | null | undefined>('');

  protected readonly srcs = computed(() => this.photos().map(photoSrc));
  protected readonly index = signal(0);
  private readonly track = viewChild.required<ElementRef<HTMLElement>>('track');

  protected go(index: number): void {
    const count = this.srcs().length;
    const next = (index + count) % count; // wraps around at both ends
    this.index.set(next);
    const track = this.track().nativeElement;
    track.scrollTo?.({ left: next * track.clientWidth, behavior: 'smooth' });
  }

  /** Keeps the dots and counter in step when the user swipes or scrolls. */
  protected onScroll(): void {
    const track = this.track().nativeElement;
    if (track.clientWidth > 0) {
      this.index.set(Math.round(track.scrollLeft / track.clientWidth));
    }
  }

  protected onKey(event: KeyboardEvent): void {
    if (event.key === 'ArrowRight') {
      event.preventDefault();
      this.go(this.index() + 1);
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault();
      this.go(this.index() - 1);
    }
  }
}
