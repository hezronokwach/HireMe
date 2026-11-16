import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { equipmentIcon } from '../equipment-labels';

/**
 * The "product shot" for a listing: its kind of equipment drawn large on a warm yellow tile with a soft
 * light behind it. Each category gets its own shade so a grid of cards doesn't look like one block.
 */
@Component({
  selector: 'app-equipment-visual',
  imports: [MatIconModule],
  template: `<mat-icon aria-hidden="true">{{ icon() }}</mat-icon>`,
  styleUrl: './equipment-visual.scss',
  host: {
    '[class]': "'tone-' + tone() + ' size-' + size()",
    'aria-hidden': 'true',
  },
})
export class EquipmentVisual {
  readonly name = input<string | null | undefined>();
  readonly category = input<string | null | undefined>();
  /** card: in a grid; hero: the top of a listing page; thumb: a small square in a list row. */
  readonly size = input<'card' | 'hero' | 'thumb'>('card');

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
