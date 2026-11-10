/** A JWT-shaped string with the given subject. The signature is fake; the app never verifies it. */
export function fakeJwt(sub: string): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS256' })}.${encode({ sub, role: 'CLIENT' })}.signature`;
}
