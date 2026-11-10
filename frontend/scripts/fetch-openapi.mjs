// Saves the running backend's OpenAPI document to openapi/hireme-api.json.
// Usage: npm run api:fetch [-- http://localhost:8080]   then   npm run api:generate
import { writeFile } from 'node:fs/promises';

const base = process.argv[2] ?? 'http://localhost:8080';
const response = await fetch(`${base}/v3/api-docs`);
if (!response.ok) {
  console.error(`GET ${base}/v3/api-docs returned ${response.status}; is the backend running?`);
  process.exit(1);
}
const spec = await response.json();
// The generated client calls relative URLs; environment.apiUrl decides the host.
spec.servers = [{ url: '' }];
await writeFile(
  new URL('../openapi/hireme-api.json', import.meta.url),
  JSON.stringify(spec, null, 2) + '\n',
);
console.log(`Saved ${Object.keys(spec.paths).length} paths to openapi/hireme-api.json`);
