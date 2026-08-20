# Public hosting

The **product** is always-on Express + SPA (match, `/ops`, and the **student exam desk** need the API). GitHub Pages is a **JSON snapshot fallback** — no accounts, no match, no ops.

`render.yaml` is still the free Render plan (sleeps ~15 min). Upgrade that service when you want the product host to stay awake. The student desk also needs a **persistent disk** (see below); the free plan’s ephemeral disk wipes accounts and files.

## 1. GitHub Pages (static snapshot)

**Public URL pattern:** `https://timus97.github.io/GovtJobsPortal/`

### What gets deployed

- Built React SPA (`client/dist`)
- Snapshot of job data under `client/public/data/` (jobs, stats, sources, pipeline reports)
- Client uses live `/api` when available; otherwise falls back to static JSON
- No student accounts or files. Keep `VITE_FEATURE_STUDENT` off unless `VITE_API_BASE` points at the API host.

### One-time setup

1. Push this repo to GitHub as **public** `timus97/GovtJobsPortal` (or your fork).
2. Repo → **Settings → Pages → Source: GitHub Actions**.
3. Push to `main` (or run workflow **Deploy GitHub Pages**).
4. Wait for the green check; open the Pages URL.

### Local build that matches Pages

```bash
npm.cmd install --ignore-scripts
npm.cmd --prefix client install
node scripts/prepareStaticData.js
set VITE_BASE=/GovtJobsPortal/
npm.cmd --prefix client run build
```

### Refreshing public data

```bash
npm.cmd run pipeline:daily
node scripts/prepareStaticData.js
git add data client/public/data
git commit -m "chore(data): refresh jobs"
git push
```

The Pages workflow rebuilds the site on every push to `main`.

---

## 2. Render.com free (full Express API + SPA)

**Use when:** you want a real Node API (same as local `npm run server`).

1. Create a free account at [render.com](https://render.com) (Gmail works).
2. **New → Blueprint** → connect this GitHub repo → apply `render.yaml`.
3. Or **New Web Service** → connect repo:
   - Build: `npm install && npm --prefix client install && node scripts/prepareStaticData.js && npm --prefix client run build && npm --prefix server install`
   - Start: `node server/src/index.js`
   - Plan: **Free**
4. After deploy: `https://<service-name>.onrender.com`

**Note:** Free web services sleep after ~15 minutes idle; first request may take 30–60s. Sleep and redeploy also wipe the ephemeral disk — see the student desk section below.

---

## Persistent disk for the student desk

Student accounts, tracker rows, mock scores, and admit/result files live on the API host, **not** in git and **not** on GitHub Pages.

| Path / service | Env | What |
|------|-----|------|
| Docker `student-db` | `STUDENT_STORE=postgres` + `STUDENT_DATABASE_URL` | Accounts, profile, desk items, file metadata, topic ticks, mock scores |
| `data/students/` | `STUDENT_STORE=json` + `STUDENT_DATA_DIR` | Same data as a JSON file (default for tests / no Docker) |
| `data/student-files/` | `STUDENT_FILES_DIR` | Private admit / result bytes (always on disk) |

**Switch the backend** with `STUDENT_STORE=json` or `STUDENT_STORE=postgres`. If `STUDENT_DATABASE_URL` is set and `STUDENT_STORE` is unset, the API uses Postgres.

Local Docker database:

```bash
npm run db:up
npm --prefix server install
npm run student:import-json
npm run server:pg
```

Default URL: `postgres://govtjobs:govtjobs@127.0.0.1:5432/govtjobs_students` (local only; change the password before any shared host). Volume `student-pg-data` keeps rows across compose restarts.

**Without a disk or a database volume**, Render free sleep/redeploy wipes student data.

Mount a disk at `/var/data` (or attach a hosted Postgres) and set:

```bash
STUDENT_STORE=postgres
STUDENT_DATABASE_URL=postgres://USER:PASS@HOST:5432/govtjobs_students
STUDENT_FILES_DIR=/var/data/student-files
SESSION_SECRET=<long random; required in production>
FEATURE_STUDENT=on
```

`render.yaml` documents these vars and keeps the **free** plan. It does **not** attach a disk (that would change the plan). After you upgrade, add the disk in the Render dashboard (mount `/var/data`) and point the two dirs there.

GitHub Pages must keep `VITE_FEATURE_STUDENT` off unless `VITE_API_BASE` points at the API host. Pages must not ship student JSON or files.

Never store students in `data/cache/portal.sqlite`. That optional cache is **catalog-only** and is discarded on rebuild / free-tier sleep.

---

## 3. Optional: Netlify / Cloudflare Pages

Same as GitHub Pages static mode:

- Build command: `npm --prefix client install && node scripts/prepareStaticData.js && npm --prefix client run build`
- Publish directory: `client/dist`
- Set env `VITE_BASE=/` (root domain) or your path prefix

---

## Secrets (alerts, ops, student desk)

Not required for public Pages browsing. For the API host / alerts / scrape CI:

| Secret / env | Purpose |
|--------------|---------|
| `SMTP_*` / `ALERT_*` | Email digests |
| `PUBLIC_SITE_URL` | Links inside alert emails |
| `SESSION_SECRET` | Required in production. HMAC for `ops_session` and `student_session`. Set in the host dashboard — do not commit. |
| `OPERATOR_PASSWORD` | Bootstrap first ops admin only |
| `FEATURE_STUDENT` | `on` for the API host desk |
| `STUDENT_STORE` | `json` (default) or `postgres` |
| `STUDENT_DATABASE_URL` | Postgres URL when `STUDENT_STORE=postgres` |
| `STUDENT_DATA_DIR` / `STUDENT_FILES_DIR` | JSON path / private file path |
| `VITE_FEATURE_STUDENT` | Off on github.io unless `VITE_API_BASE` points at the API host |

---

## Gmail verification

If a host asks for email verification (Render, Netlify, etc.), any Gmail works.  
**Do not commit** passwords or app passwords into the repo. Use host secret stores.

---

## Health checks after go-live

- Home page loads
- `/jobs` lists openings
- `/sources` lists PSU/gov URLs
- Job detail “Official site” opens external career page
- On the API host (not Pages): `/account/register` works; `/dashboard` persists across a process restart **only if** a persistent disk is mounted

## Live URLs (this project)

| What | URL |
|------|-----|
| **Public website** | https://timus97.github.io/GovtJobsPortal/ |
| **Jobs** | https://timus97.github.io/GovtJobsPortal/jobs |
| **Sources** | https://timus97.github.io/GovtJobsPortal/sources |
| **GitHub repo** | https://github.com/timus97/GovtJobsPortal |
| **Static jobs data** | https://timus97.github.io/GovtJobsPortal/data/jobs.json |

## QA reports

After deploy, run:

```bash
npm.cmd run qa:e2e
```

Reports are written to `reports/qa-report.md` and `reports/qa-report.json`.
