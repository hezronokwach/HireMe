/**
 * The page to open after logging in. Only paths inside this app are allowed, so a crafted link
 * like /login?returnUrl=https://evil.example can't send the user elsewhere.
 */
export function safeReturnUrl(url: string | null | undefined): string {
  if (
    !url ||
    !url.startsWith('/') ||
    url.startsWith('//') ||
    url.includes('\\') ||
    url.startsWith('/login')
  ) {
    return '/';
  }
  return url;
}
