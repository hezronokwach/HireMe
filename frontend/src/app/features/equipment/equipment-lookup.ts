import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, shareReplay } from 'rxjs';
import { EquipmentResponse, EquipmentService } from '../../api';

/**
 * Listings by id, fetched once and shared, for pages that only have an `equipmentId` (bookings).
 * A listing that can't be loaded (e.g. deleted) comes back as null.
 */
@Injectable({ providedIn: 'root' })
export class EquipmentLookup {
  private readonly api = inject(EquipmentService);
  private readonly cache = new Map<number, Observable<EquipmentResponse | null>>();

  get(id: number): Observable<EquipmentResponse | null> {
    let entry = this.cache.get(id);
    if (!entry) {
      entry = this.api.getEquipment(id).pipe(
        catchError(() => of(null)),
        shareReplay(1),
      );
      this.cache.set(id, entry);
    }
    return entry;
  }
}
