import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { photoSrc } from '../../../core/images';
import { equipmentIcon } from '../equipment-labels';

/**
 * The picture for a listing: its cover photo when it has one, otherwise its kind of equipment drawn large on
 * a warm yellow tile with a soft light behind it. Each category gets its own shade, so a grid of cards
 * without photos doesn't look like one block.
 */
@Component({
  selector: 'app-equipment-visual',
  imports: [MatIconModule],
  template: `
    @if (src(); as src) {
      <img
        [src]="src"
        alt=""
        [attr.loading]="size() === 'hero' ? 'eager' : 'lazy'"
        decoding="async"
      />
    } @else {
      <mat-icon aria-hidden="true">{{ icon() }}</mat-icon>
    }
  `,
  styleUrl: './equipment-visual.scss',
  host: {
    '[class]': "'tone-' + tone() + ' size-' + size() + (src() ? ' has-photo' : '')",
    'aria-hidden': 'true',
  },
})
export class EquipmentVisual {
  readonly name = input<string | null | undefined>();
  readonly category = input<string | null | undefined>();
  /** The cover photo's URL, if the listing has photos. */
  readonly photo = input<string | null | undefined>();
  /** card: in a grid; hero: the top of a listing page; thumb: a small square in a list row. */
  readonly size = input<'card' | 'hero' | 'thumb'>('card');

  protected readonly src = computed(() => {
    const photo = this.photo();
    return photo ? photoSrc(photo) : null;
  });
  protected readonly icon = computed(() => equipmentIcon(this.name(), this.category()));
  protected readonly tone = computed(() => {
    switch (this.category()) {
      case 'HEAVY_MACHINERY':
        return 'heavy';
      case 'AGRICULTURAL':
        return 'farm';
      case 'EVENT_SETUP':
        return 'event';
      default:
        return 'plain';
    }
  });
}
