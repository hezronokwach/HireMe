# Deploying HireMe

HireMe runs on three hosted services:

| Part | Where | What runs there |
|---|---|---|
| Database | [Neon](https://neon.tech) | PostgreSQL. Flyway creates and updates the tables when the API starts. |
| API | [Render](https://render.com) | The Spring Boot app, built from the root `Dockerfile` (`render.yaml`). |
| Web app | [Vercel](https://vercel.com) | The Angular app (`frontend/`, `frontend/vercel.json`). |

```
browser ──▶ Vercel: Angular app
              │  /api/*  (rewrite)
              ▼
            Render: hireme-api (Docker, Java 25) ──SSL──▶ Neon: PostgreSQL
```

The browser only ever talks to the Vercel address. Vercel forwards every `/api/...` request to Render, so the app keeps calling `/api` on its own origin, as it does in development (`environment.apiUrl` is empty).

Follow the steps in order: **database → API → web app → connect them → check**. Plan on about 30 minutes, most of it waiting for the first builds.

---

## 0. Before you start

### Accounts and access

- **Accounts:** GitHub (with this repo), Neon, Render and Vercel. All three hosts can sign in with GitHub.
- **Groq key (optional):** from https://console.groq.com/keys, only if you want the AI features.
- **Admin rights on the repo:** Render and Vercel each install a GitHub app to read the repo and deploy on push.

### Which branch goes live

`render.yaml` deploys the **`main`** branch, and Vercel deploys its production branch (normally `main`).

- **Production:** merge `staging` into `main` first (the usual `staging → main` PR).
- **Trial run from `staging`:** change `branch: main` to `branch: staging` in `render.yaml`, and set Vercel's production branch to `staging`.

### Check these before the app is public

- [ ] **Anyone can register as an admin.** `POST /api/v1/auth/register` accepts any `role`, including `ADMIN`. That's fine for a demo, but fix it before real users arrive: only allow `CLIENT` and `OWNER` at registration, and create admins another way.
- [ ] **No secrets in git.** Searching the repo for a Groq key (they start with `gsk` and an underscore) should find nothing. The JWT secret, database password and Groq key live only in Render.
- [ ] **CI is green on the branch you deploy** (`ci-pr.yml`, `frontend.yml`).

---

## 1. Database on Neon

1. **Create a project:** in Neon, click **New Project**.
   - **Name:** `hireme`.
   - **Postgres version:** the newest offered (16 or later; the app is tested on 16).
   - **Region:** **AWS Europe Central 1 (Frankfurt)**, the same region as the Render service in `render.yaml`. Every query crosses between the two, so keep them close.
2. **Use the default database.** Neon creates the database `neondb` and an owner role (e.g. `neondb_owner`), and you can use them as they are. The app doesn't need the database to be called `equipment_hiring`.
3. **Get the connection details:** click **Connect** on the project dashboard.
   - **Branch:** `production` (the default branch), **database:** `neondb`, **role:** the owner role.
   - **Turn "Connection pooling" off.** You want the **direct** host, `ep-…​.eu-central-1.aws.neon.tech`, not the one with `-pooler` in it.
     - **Why:** the pooled endpoint runs PgBouncer in transaction mode, which breaks Flyway's migration lock and server-side prepared statements.
     - **Why it costs nothing:** the app already pools its own connections (HikariCP, 5 connections on Render).
   - Neon shows a URL like this:
     ```
     postgresql://neondb_owner:<password>@ep-cool-name-123456.eu-central-1.aws.neon.tech/neondb?sslmode=require
     ```
4. **Turn it into the three values the API needs.** Spring wants the JDBC form, with the user and password kept separate:

   | Variable | Value |
   |---|---|
   | `DATABASE_URL` | `jdbc:postgresql://ep-cool-name-123456.eu-central-1.aws.neon.tech/neondb?sslmode=require` |
   | `DB_USER` | `neondb_owner` |
   | `DB_PASSWORD` | the password from the URL |

   Keep `?sslmode=require`, because Neon only accepts encrypted connections. Leave out `channel_binding=require` if Neon's snippet includes it. The JDBC driver doesn't need it, and older drivers reject it.

5. **Optional: try it from your machine** before going near Render. It shows the database settings work, and it runs the migrations once:
   ```bash
   ./mvnw -q package -DskipTests
   SPRING_PROFILES_ACTIVE=docker \
   DATABASE_URL='jdbc:postgresql://<direct-host>/neondb?sslmode=require' \
   DB_USER=neondb_owner DB_PASSWORD='<password>' \
   JWT_SECRET="$(openssl rand -hex 32)" \
   java -jar target/HireMe-0.0.1-SNAPSHOT.jar
   ```
   - **Expected:** Flyway logs `Successfully applied 5 migrations` and the app starts on port 8080.
   - **Tables:** Neon's **Tables** page then shows the schemas `app_user`, `app_equipment` and `app_booking` (one per module), plus `flyway_schema_history` and `event_publication` in `public`.
   - Stop it with Ctrl+C.

**Free plan behaviour:**

- **Idle pause:** the compute pauses after about 5 idle minutes. The first query after that waits a moment while it wakes, and the app retries the connection by itself.
- **Storage:** about 0.5 GB, far more than this app needs for a long time.
- **Branches for testing:** Neon lets you make copy-on-write **branches** of the database, so a `staging` branch gives a staging API its own data. Create it from **Branches → New branch**. It gets its own host, which goes in that environment's `DATABASE_URL`.

---

## 2. API on Render

`render.yaml` in the repo root is a Render **Blueprint**: it describes the `hireme-api` web service and its settings.

1. **Start the Blueprint:** in Render, click **New → Blueprint**, connect GitHub if asked, and pick this repository. Render reads `render.yaml` and shows the `hireme-api` service.
2. **Fill in the values it asks for** (marked `sync: false` in `render.yaml`, so they're never stored in git):

   | Variable | Value |
   |---|---|
   | `DATABASE_URL` | the JDBC URL from step 1.4 |
   | `DB_USER` | e.g. `neondb_owner` |
   | `DB_PASSWORD` | the Neon password |
   | `FRONTEND_URL` | the Vercel address with no trailing slash, e.g. `https://hireme.vercel.app`. Before step 3 you don't know it yet: put `https://example.com` and change it in step 4. |
   | `HIREME_AI_ENABLED` | `true` to turn on the AI features, otherwise `false` |
   | `GROQ_API_KEY` | your Groq key (leave empty when AI is off). Paste only the key itself. The app trims a trailing newline, but an older build fails every AI call with `Unexpected char 0x0a … in Authorization value`. |
   | `HIREME_DEMO_OWNER_PASSWORD` | optional: a password (8+ characters) to log in as the demo owner, `demo-owner@hireme.example`, and see the bookings on the demo listings. Leave it empty and nobody can log in as the demo owner. |

   The Blueprint sets the rest:

   | Variable | Value | Why |
   |---|---|---|
   | `SPRING_PROFILES_ACTIVE` | `docker` | This profile reads the database settings from the environment. |
   | `PORT` | `8080` | Tells Render which port the app listens on. |
   | `JWT_SECRET` | generated by Render | Signs the login tokens. Changing it logs everyone out. |
   | `HIREME_DEMO_LISTINGS` | `true` | On the first start, adds a demo owner and 15 listings across all categories and towns, so a client who signs up straight away has equipment to browse, book and ask the assistant about. It's added once only: nothing is added when the demo owner already exists. Set it to `false` once real owners have listed their equipment. To remove the demo listings, log in as the demo owner and delete them. |
   | `SPRING_JPA_SHOW_SQL` | `false` | Otherwise every SQL statement goes to the logs. |
   | `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | `5` | Plenty for one instance, and well inside Neon's connection limits. |
   | `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75` | Keeps Java inside a small instance's memory. |

3. **Apply it.** The first build compiles the app inside Docker, which takes a few minutes. Then watch the service's **Logs** for:
   - `Successfully applied … migrations` (or `Schema "public" is up to date` if you ran step 1.5), then
   - `Started HireMeApplication`.
4. **Check the health endpoint:** `https://hireme-api.onrender.com/actuator/health` should return `{"status":"UP"}`.
   - **Note the actual URL.** If the name `hireme-api` was taken, Render gives the service a different address (shown at the top of the service page), and step 3 needs it.

**Free plan behaviour:**

- **Sleep:** the service sleeps after 15 minutes without requests. The next request wakes it, and a Spring app takes about a minute to start, so the first page load after a quiet spell is slow. The paid **Starter** plan stays awake.
- **Resources:** free instances have 512 MB of memory and little CPU, so startup is slow but works. If the logs show `OutOfMemoryError`, move to Starter.

**Optional settings** (Environment tab; any property from the README's Configuration table works as an environment variable):

- `SPRINGDOC_SWAGGER_UI_ENABLED=false` and `SPRINGDOC_API_DOCS_ENABLED=false` hide Swagger UI and the OpenAPI spec in production.
- `HIREME_AI_MODEL` picks a different Groq model if the default is retired.

---

## 3. Web app on Vercel

`frontend/vercel.json` holds the build settings and the rewrites.

1. **Point it at the repo:** in Vercel, click **Add New → Project** and import this repository.
2. **Set Root Directory to `frontend`.** This one is important. Vercel then picks up `frontend/vercel.json`, which sets:
   - framework **Angular**, build command `npm run build`, output directory `dist/frontend/browser`;
   - `/api/*` forwarded to `https://hireme-api.onrender.com/api/*`;
   - any other path that isn't a file served `index.html`, so links like `/login` or `/equipment/5` work on a refresh;
   - `index.html` never cached, so a new deploy shows up right away.
3. **Set the Node version:** open **Settings → Build and Deployment → Node.js Version** and pick **24.x**, the same as CI. No environment variables are needed on Vercel.
4. **If Render's URL isn't `hireme-api.onrender.com`,** edit the destination in `frontend/vercel.json`, commit and push.
5. **Deploy.** The build takes about a minute. Note the production address, for example `https://hireme.vercel.app`.

Want your own domain? Add it under **Settings → Domains**, then use that address as `FRONTEND_URL` in the next step.

---

## 4. Connect them

1. **Update `FRONTEND_URL`:** in Render, open **hireme-api → Environment** and set `FRONTEND_URL` to the exact Vercel production address. Use `https://`, no trailing slash, and the custom domain if you added one.
2. **Redeploy:** save, and let Render redeploy (or use **Manual Deploy → Deploy latest commit**).

**Why it matters:**

- **Vercel passes the browser's `Origin` along.** When it forwards a request, the browser's `Origin` header (the Vercel address) goes to Render with it.
- **The API checks that origin.** It accepts exactly one origin (`FRONTEND_URL`), so a mismatch shows up as `403 Invalid CORS request`.
- **Previews aren't allowed.** Vercel **preview** deployments have other addresses (`hireme-git-…​.vercel.app`), so their API calls are refused. Allowing them would need `FRONTEND_URL` to take several origins or a pattern, which the API doesn't support yet.

---

## 5. Check it end to end

Open the Vercel address in a private window:

1. **As an owner:** register, then create a listing.
2. **As a client:** register in another private window, find the listing, and request some dates.
3. **Back as the owner:** under **Bookings on my equipment**, the request shows the client's name and phone. Confirm it.
4. **Check the network calls:** open the browser's network tab. The calls go to `https://<app>.vercel.app/api/...` and return `200`/`201`.
5. **If AI is on:** ask **Ask HireMe** something like "a tractor in Nakuru next weekend".
6. **Check the data:** Neon's **Tables** page shows the new rows.

### When something is wrong

| What you see | Likely cause and fix |
|---|---|
| The first API call takes ~1 minute, or gives `502`/`504` | Render was asleep. Wait and retry; Starter plan avoids it. |
| `403 Invalid CORS request` | `FRONTEND_URL` doesn't match the address in the browser exactly (`https`, no trailing slash), or this is a preview deployment. |
| `404` on `/api/...` | The `vercel.json` destination doesn't match the Render service URL, or the Root Directory isn't `frontend`. |
| `404` when refreshing `/login` | Vercel isn't using `frontend/vercel.json`: set Root Directory to `frontend`. |
| Render log: `Connection to … refused` / `The connection attempt failed` | Wrong host in `DATABASE_URL`, or it isn't in JDBC form (`jdbc:postgresql://…`). |
| Render log: `password authentication failed` | `DB_USER`/`DB_PASSWORD` don't match Neon. Reset the password in Neon (**Roles**) and update Render. |
| Render log: `SSL … required` or `no pg_hba.conf entry` | `?sslmode=require` is missing from `DATABASE_URL`. |
| Render log: Flyway lock or `prepared statement "S_1" already exists` | `DATABASE_URL` uses the `-pooler` host. Use the direct host. |
| Render log: `Could not resolve placeholder 'JWT_SECRET'` | The variable is missing. Apply the Blueprint again, or add it by hand (`openssl rand -hex 32`). |
| Everyone was logged out after a deploy | `JWT_SECRET` changed. |
| Render log: `Unexpected char 0x0a … in Authorization value` | `GROQ_API_KEY` was pasted with a trailing newline. Re-enter it in Render without the line break (newer builds trim it). |
| "The AI is unavailable" / "busy" | `HIREME_AI_ENABLED` isn't `true`, `GROQ_API_KEY` is missing, or Groq's free per-minute limit was reached. |

---

## 6. After the first deploy

### New versions

- **Automatic (the default):** merging into `main` redeploys both sides. Render rebuilds the Docker image and Flyway applies any new migration when the app starts. Vercel rebuilds the frontend.
- **Only after CI passes:** turn off **Auto-Deploy** in Render (service **Settings**) and copy the service's **Deploy Hook** URL into a GitHub secret named `RENDER_DEPLOY_HOOK`. Then replace the placeholder in the `deploy-production` job of `.github/workflows/cd-pipeline.yml` with:
  ```yaml
      - name: Deploy to Render
        run: curl -fsS -X POST "${{ secrets.RENDER_DEPLOY_HOOK }}"
  ```

### Database changes

- **Add migrations:** add a new `V<n>__description.sql` under `src/main/resources/db/migration/common` (or `postgresql`). Never edit one that has already run: Flyway checks their checksums and refuses to start.
- **Test risky ones first:** try a migration on a Neon **branch** of the production database before merging.
- **Backups:** Neon keeps a restore window (point-in-time restore from **Backup & Restore**; the free plan keeps a short history). For anything that matters, also take a regular `pg_dump`:
  ```bash
  pg_dump "postgresql://neondb_owner:<password>@<direct-host>/neondb?sslmode=require" > hireme-$(date +%F).sql
  ```

### Secrets

| Secret | How to change it | Effect |
|---|---|---|
| `DB_PASSWORD` | Reset it in Neon (**Roles**), then update it in Render | Render redeploys and reconnects |
| `JWT_SECRET` | Edit it in Render | Everyone has to log in again |
| `GROQ_API_KEY` | Create a new key in Groq, update it in Render, delete the old key | Nothing else changes |

### Logs and monitoring

- **Logs:** the API's are in Render (**hireme-api → Logs**), the frontend build logs in Vercel, and the query stats in Neon (**Monitoring**).
- **Uptime:** an external uptime check on `/actuator/health` warns you when the API is down. On the free plan, a check every few minutes also keeps it from sleeping.

---

## Costs and limits on the free plans

| Service | Free plan | When to pay |
|---|---|---|
| Neon | Pauses when idle, about 0.5 GB of storage, short restore history | More storage, or a longer restore history |
| Render | Sleeps after 15 idle minutes, 512 MB of memory, slow start | Starter: always on, faster |
| Vercel | Hobby plan: personal, non-commercial use | Pro, for a business |
| Groq | A few thousand tokens a minute, about one or two assistant turns a minute | A paid tier for real traffic |

Check each provider's pricing page; their limits change.
