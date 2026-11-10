import { safeReturnUrl } from './return-url';

describe('safeReturnUrl', () => {
  it('keeps paths inside the app', () => {
    expect(safeReturnUrl('/bookings?page=2')).toBe('/bookings?page=2');
  });

  it('falls back to home for anything else', () => {
    for (const url of [
      undefined,
      null,
      '',
      'https://evil.example',
      '//evil.example',
      '/\\evil.example',
      '/login?returnUrl=/x',
    ]) {
      expect(safeReturnUrl(url)).toBe('/');
    }
  });
});
