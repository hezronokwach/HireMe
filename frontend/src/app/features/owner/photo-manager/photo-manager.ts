import { Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { firstValueFrom } from 'rxjs';
import { EquipmentService } from '../../../api';
import { errorMessage } from '../../../core/api-errors';
import { photoId, photoSrc, shrinkPhoto } from '../../../core/images';

/** Same limits as the API (EquipmentPhotoService). */
export const MAX_PHOTOS = 6;
const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp'];

export interface PhotoItem {
  /** What the <img> shows: the API URL, or a local preview before upload. */
  src: string;
  /** The API's photo URL once uploaded. */
  url?: string;
  /** Picked on a new listing: uploaded after the listing is created. */
  file?: Blob;
  uploading?: boolean;
}

/**
 * Adds, removes and orders a listing's photos. On an existing listing every change is saved straight away.
 * On a new one the photos wait here until the listing exists; the form then calls {@link uploadPending}.
 */
@Component({
  selector: 'app-photo-manager',
  imports: [MatButtonModule, MatIconModule, MatProgressBarModule],
  templateUrl: './photo-manager.html',
  styleUrl: './photo-manager.scss',
})
export class PhotoManager {
  private readonly api = inject(EquipmentService);

  /** The listing, when it exists already. */
  readonly equipmentId = input<number | undefined>();
  /** Its photos as loaded, cover first. */
  readonly photoUrls = input<string[] | undefined>();

  protected readonly photos = signal<PhotoItem[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = computed(() => this.photos().some((p) => p.uploading));
  protected readonly full = computed(() => this.photos().length >= MAX_PHOTOS);
  protected readonly max = MAX_PHOTOS;

  constructor() {
    effect(() => {
      const urls = this.photoUrls() ?? [];
      this.photos.set(urls.map((url) => ({ src: photoSrc(url), url })));
    });
    // Local previews hold memory until released.
    inject(DestroyRef).onDestroy(() =>
      this.photos().forEach((p) => p.file && URL.revokeObjectURL(p.src)),
    );
  }

  /** Photos picked before the listing existed. */
  hasPending(): boolean {
    return this.photos().some((p) => p.file);
  }

  protected async pick(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = ''; // so picking the same file again still fires
    this.error.set(null);
    const room = MAX_PHOTOS - this.photos().length;
    if (files.length > room) {
      this.error.set(`A listing can have ${MAX_PHOTOS} photos; the first ${room} were added.`);
    }
    for (const file of files.slice(0, Math.max(room, 0))) {
      if (!ACCEPTED.includes(file.type)) {
        this.error.set(`“${file.name}” isn't a JPEG, PNG or WebP photo.`);
        continue;
      }
      const shrunk = await shrinkPhoto(file);
      const id = this.equipmentId();
      if (id === undefined) {
        this.photos.update((list) => [...list, { src: URL.createObjectURL(shrunk), file: shrunk }]);
      } else {
        await this.upload(id, shrunk);
      }
    }
  }

  protected async remove(index: number): Promise<void> {
    const photo = this.photos()[index];
    const id = this.equipmentId();
    this.error.set(null);
    if (photo.url && id !== undefined) {
      try {
        await firstValueFrom(this.api.deleteEquipmentPhoto(id, photoId(photo.url)));
      } catch (err) {
        this.error.set(errorMessage(err, 'Could not remove the photo.'));
        return;
      }
    }
    if (photo.file) {
      URL.revokeObjectURL(photo.src);
    }
    this.photos.update((list) => list.filter((_, i) => i !== index));
  }

  protected async makeCover(index: number): Promise<void> {
    const photo = this.photos()[index];
    const id = this.equipmentId();
    this.error.set(null);
    if (photo.url && id !== undefined) {
      try {
        await firstValueFrom(this.api.makeEquipmentPhotoCover(id, photoId(photo.url)));
      } catch (err) {
        this.error.set(errorMessage(err, 'Could not change the cover.'));
        return;
      }
    }
    this.photos.update((list) => [photo, ...list.filter((_, i) => i !== index)]);
  }

  /** Uploads the photos picked for a new listing, in order. Returns how many failed. */
  async uploadPending(equipmentId: number): Promise<number> {
    let failed = 0;
    for (const photo of this.photos().filter((p) => p.file)) {
      if (!(await this.upload(equipmentId, photo.file!, photo))) {
        failed++;
      }
    }
    return failed;
  }

  private async upload(equipmentId: number, file: Blob, existing?: PhotoItem): Promise<boolean> {
    const item: PhotoItem = existing ?? { src: URL.createObjectURL(file), file };
    item.uploading = true;
    this.photos.update((list) => (existing ? [...list] : [...list, item]));
    try {
      const saved = await firstValueFrom(this.api.uploadEquipmentPhoto(equipmentId, file));
      URL.revokeObjectURL(item.src);
      this.replace(item, { src: photoSrc(saved.url!), url: saved.url! });
      return true;
    } catch (err) {
      this.error.set(errorMessage(err, 'Could not upload the photo.'));
      URL.revokeObjectURL(item.src);
      this.photos.update((list) => list.filter((p) => p !== item));
      return false;
    }
  }

  private replace(old: PhotoItem, next: PhotoItem): void {
    this.photos.update((list) => list.map((p) => (p === old ? next : p)));
  }
}
