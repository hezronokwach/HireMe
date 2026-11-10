// Saves the backend's OpenAPI document to openapi/hireme-api.json, then run `npm run api:generate`.
//
//   npm run api:fetch                              from a backend running on http://localhost:8080
//   npm run api:fetch -- http://localhost:9090     from a backend somewhere else
//   npm run api:fetch -- ../target/openapi.json    from the file `./mvnw test` writes (OpenApiDocsTest)
import { readFile, writeFile } from 'node:fs/promises';

const source = process.argv[2] ?? 'http://localhost:8080';
let spec;
if (/^https?:\/\//.test(source)) {
  const response = await fetch(`${source.replace(/\/$/, '')}/v3/api-docs`).catch(() => null);
  if (!response?.ok) {
    console.error(`Could not read ${source}/v3/api-docs; is the backend running?`);
    process.exit(1);
  }
  spec = await response.json();
} else {
  spec = JSON.parse(await readFile(source, 'utf8'));
}
// The generated client calls relative URLs; environment.apiUrl decides the host.
spec.servers = [{ url: '' }];
await writeFile(
  new URL('../openapi/hireme-api.json', import.meta.url),
  JSON.stringify(spec, null, 2) + '\n',
);
console.log(`Saved ${Object.keys(spec.paths).length} paths to openapi/hireme-api.json`);
