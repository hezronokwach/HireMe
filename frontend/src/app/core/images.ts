import { environment } from '../../environments/environment';

/** Longest side of an uploaded photo: plenty for a full-width carousel, small enough to load fast. */
export const MAX_PHOTO_SIDE = 1600;
const JPEG_QUALITY = 0.82;

/**
 * Shrinks a photo before upload: phone photos are often 4000+ px and several MB, and they are stored in the
 * database. Returns a JPEG of at most {@link MAX_PHOTO_SIDE} px on its longest side. If the browser can't
 * decode or re-encode it, the original file is returned and the API decides.
 */
export async function shrinkPhoto(file: Blob): Promise<Blob> {
  try {
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, MAX_PHOTO_SIDE / Math.max(bitmap.width, bitmap.height));
    const width = Math.round(bitmap.width * scale);
    const height = Math.round(bitmap.height * scale);
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d');
    if (!context) {
      return file;
    }
    // A white backdrop, so transparent PNGs don't turn black as JPEGs.
    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, width, height);
    context.drawImage(bitmap, 0, 0, width, height);
    bitmap.close();
    const blob = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob(resolve, 'image/jpeg', JPEG_QUALITY),
    );
    return blob && blob.size < file.size ? blob : file;
  } catch {
    return file;
  }
}

/** Photo URLs from the API are paths ("/api/v1/..."); prefix them when the API is on another origin. */
export function photoSrc(url: string): string {
  return url.startsWith('/') ? environment.apiUrl + url : url;
}

/** The photo id at the end of a photo URL ("/api/v1/equipment/5/photos/12" → 12). */
export function photoId(url: string): number {
  return Number(url.split('/').pop());
}
