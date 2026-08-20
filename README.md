# GovtJobsPortal — All-India government jobs, match, and prepare

Public site for **currently open Indian government and PSU applications** (exam and no-exam), plus **prepare-for calendars** and an explainable **eligibility match** against a local profile.

Not affiliated with the Government of India or any board or PSU. Every listing is a pointer to an official URL. Always verify dates, eligibility, and fees on the notification before applying.

**Repo:** https://github.com/timus97/GovtJobsPortal  
**Default branch:** `master`  
**Design (accepted, implementation complete):** [docs/ALL_GOVT_JOBS_DESIGN.md](docs/ALL_GOVT_JOBS_DESIGN.md)

---

## What this product is

India’s recruitment surface is split across UPSC, SSC, IBPS, SBI, RRB, state PSCs, PSU career pages, regulators, and school/health boards. Candidates otherwise hunt those sites one by one, then guess whether they are eligible.

This portal is a **catalog + matcher**, not a gazette and not an apply engine:

| Surface | Who it is for | What it does |
| --- | --- | --- |
| **Browse `/jobs`** | Anyone looking for an open window | Lists live applications. Exam and no-exam. Default filter is **all jobs**. |
| **Profile `/profile` + Match `/match`** | A candidate who will state DOB, education, state, and reservation category | Ranks currently open opportunities with pass / fail / unknown reasons. Not an official decision. |
| **Prepare `/prepare`** | Someone starting UPSC / SSC / IBPS / NET / GATE / CTET study | Official calendar rows (ExamSeries). No “Apply” unless a linked vacancy is actually open. |
| **Ops `/ops`** | Site operators only | Sign in, paste an official HTTPS URL, review extracted facts, publish or unpublish. |

### In scope

- Central, PSU, autonomous, state-PSC, school, health, science, regulator, and post openings that have an official HTTPS page.
- Exam-bearing posts (UPSC, SSC, IBPS, SBI, RRB, PSC, and similar) **and** walk-in / interview / merit / contract / apprenticeship posts.
- Recurring exams as **ExamSeries** (prepare-for): UPSC, SSC, IBPS, SBI, RRB, **UGC NET**, **GATE**, **CTET**.
- Rule-based eligibility match: age as-on the notification date, education ladder, domicile, gender, PwBD (post-wise when the notification lists posts), reserved-only only when that fact is structured.

### Out of scope (locked)

- Public candidate accounts or any server-side PII. Profile lives in `localStorage` only.
- Republishing official PDFs, hall tickets, or full gazette text.
- Applying on the candidate’s behalf, OAuth to board sites, or fee payment.
- CAPTCHA bypass on defence portals (`joinindianarmy.nic.in` and similar). Those sources are **manual / calendar only**.
- **CUET** (admissions, not a job).
- Treating **UGC NET / GATE / CTET** as vacancies. They are prepare-for series.
- Inventing age relaxations or NLP-parsing free-text `eligibility[]`.
- Using `rrbcdg.gov.in` as the national RRB host (it is Chandigarh zonal). National apply is `rrbapply.gov.in`.

---

## Current snapshot (after design PR10, 2026-08-20)

| Metric | Now | v1 envelope in the design |
| --- | --- | --- |
| Published jobs | 628 (563 open) | 2,000–10,000 opportunities |
| Exam series | 25 | ~200 capacity |
| Registry sources | 307 / 241 enabled | P0 boards + P1 verticals + existing PSU pages |
| Match p95 (10k synthetic) | ~71 ms | < 200 ms |
| Processed JSON + raw | ~1.8 MB | < 2 GB year 1 |
| Last process run in git | 2026-07-11 | Daily collect on GHA |

Collectors for UPSC, SSC, IBPS, SBI, RRB, 10 PSCs, and P1 boards **are registered**. The published `jobs.json` is still the pre-expansion snapshot plus one exam fixture until the next successful daily collect + process.

---

## How a candidate uses it

1. Open `/jobs`. Filter by exam / no-exam, org type, sector, last date.
2. Open a card → official URL. The portal never hosts the notification PDF.
3. Optional: `/profile` — date of birth, highest education, **reservation category (required)**, birth/domicile state, optional gender and PwBD.
4. `/match` calls `POST /api/match`. Each row has chips (age, education, PwBD, category, last date). Unknown facts lower confidence; they do not fail the row.
5. `/prepare` lists calendars. Apply only appears when a linked opportunity is open.

Persistent copy on match: **“Not an official eligibility decision.”**

---

## How an operator uses it

1. `/ops/login` — individual username + password. `OPERATOR_PASSWORD` creates the first `admin` only when the operator table is empty, then cannot sign in.
2. Paste an `https://` official URL (`.gov.in` / `.nic.in` or the extra-host allowlist).
3. Queue states: `pending` → `needs_review` / `valid` / `invalid`. Edit facts, publish, reject, or **unpublish**.
4. Publish writes `data/staging/ops_paste/<id>.json` and upserts local `jobs.json`. With `OPS_INGEST_TOKEN` the staging file is committed via `.github/workflows/ops-ingest.yml`. Without a token the row is `published_local` (API host only) until someone commits the file.
5. **Never** trigger `pipeline:daily` from ops. That rebuilds from seed + empty runner staging and can wipe published jobs.

---

## Architecture

```
GitHub Actions (45 min)          Git (durable SoR)              Always-on Express + SPA
----------------------           -----------------              -----------------------
runDaily.js collectors  -->      data/processed/jobs.json       GET /api/jobs
calendar PDF parser              data/processed/exam_series.json POST /api/match
buildJobs.js (seed+staging)      data/sources/registry.json     /ops paste-URL queue
prepareStaticData.js (Pages)     data/staging/ops_paste/        optional SQLite cache
```

| Layer | Path | Notes |
| --- | --- | --- |
| SPA | `client/` | React 19 + Vite 8. Routes in `client/src/App.jsx`. |
| API | `server/src/` | Express on `:4000`. CORS + credentials. |
| Shared rules | `shared/` | Job / opportunity / exam-series schemas; `eligibilityMatch.js` + `eligibilityFacts.js`. |
| Collect | `scripts/collect/` | Registry-driven. Special collectors + `genericPsc` / `genericBoard` / `genericCareers`. |
| Process | `scripts/process/buildJobs.js` | Rebuilds published JSON from **seed + staging**. Does not drop exams. |
| Optional cache | `server/src/db/sqlite.js` | Rebuilt by `scripts/migrate/jsonToSqlite.js` on boot. JSON stays SoR. |

**Git JSON is the source of record.** A restarted API and the next collect share `data/processed/*.json`. SQLite is discarded on a free Render sleep.

### Public routes

| Method | Path | Role |
| --- | --- | --- |
| GET | `/api/jobs`, `/api/jobs/:id` | Browse. `hasExam=all\|yes\|no`. Closed hidden unless `status=` is set. |
| GET | `/api/exam-series`, `/api/exam-series/:id` | Prepare-for calendars. |
| GET | `/api/stats`, `/api/sources`, `/api/pipeline`, `/api/health` | Counts, registry projection, last run, `sqliteCache`. |
| POST | `/api/match` | Body = profile. 400 if `reservationCategory` missing. Profile is **not** stored. |

### Ops routes (session cookie)

`POST /api/ops/login` · `GET /api/ops/me` · `POST /api/ops/operators` (admin) · `POST /api/ops/collect` · review `PATCH` / publish / reject / **unpublish**.

---

## Data model (product language)

- **Opportunity** — a live apply window (what `/jobs` and match score).
- **ExamSeries** — a recurring calendar row (what `/prepare` shows). `applyNever` on NET / GATE / CTET.
- **Source** — one row in `data/sources/registry.json` (`sourceId`, `listUrls`, `priority` P0–P3, `enabled`, `collector`).
- **CollectJob** — paste-URL review state in `data/processed/collect-jobs.json` (not catalog SoR).

Published files: `data/processed/jobs.json`, `exam_series.json`, `stats.json`. `prepareStaticData.js` copies them to `client/public/data/` for the Pages snapshot.

---

## Collectors and sources

Registered like `becil` / `ncs` through `scripts/collect/runDaily.js` + `registry.json`.

| Priority | What | Collector |
| --- | --- | --- |
| P0 | NCS, Employment News (free highlights table only), BECIL, UPSC, SSC, IBPS, SBI, RRB | dedicated + `employmentNews` |
| P1 | RBI, NABARD, SEBI, India Post, FCI, LIC, EPFO | `genericBoard` |
| P1 | KVS, NVS, DSSSB, AIIMS, ESIC, NHM, DRDO, ISRO, BARC | `genericCareers` / `genericPsc` |
| P1 | GATE / CTET / UGC NET calendars | `genericPsc` (`*_calendar` → ExamSeries, not a vacancy) |
| P1 | Army / Navy / Air Force | `method: manual`, `enabled: false` |
| P2 | 10 state PSCs (UPPSC, BPSC, MPSC, TNPSC, WBPSC, RPSC, GPSC, KPSC, Kerala PSC, APPSC) | one `genericPsc.js` |
| P1–P3 | Existing PSU career pages | `genericCareers` |

Playwright runs in GitHub Actions (SSC and other JS lists). The Render / local paste path is **HTML + metadata only**.

---

## Eligibility match (rules, not NLP)

Profile (`sarkari.profile.v1` in `localStorage`):

- Required: `dob`, `highestEducation`, `reservationCategory` (UR\|EWS\|OBC\|SC\|ST), birth state, at least one domicile state.
- Optional: discipline, gender, PwBD `{ hasDisability, category: VH\|HH\|OH\|others }`.

Rules: status (missing last date → **unknown**, still included), age on `ageAsOnDate` with **printed** relaxation only, education ladder (`below_10` … `phd`; `experience` is not a rung), domicile, gender, PwBD, reserved-only.

```
confidence = coveredRules / applicableRules   // unknown does not count as covered
score      = 100 * confidence * (fails === 0 ? 1 : 0)
```

Golden fixtures: `tests/fixtures/golden-opportunities.json` (≥ 20 real-notification shapes).

---

## Feature flags

| Flag | Default | Meaning |
| --- | --- | --- |
| `FEATURE_SERVER_MATCH` | on | `POST /api/match` is the product path |
| `VITE_FEATURE_PROFILE_MATCH` | on (off on github.io without `VITE_API_BASE`) | Profile / Match nav |
| `VITE_FEATURE_PREPARE` | on | Prepare nav |
| `VITE_FEATURE_OPS` | on for API host; off on Pages | `/ops` |
| `FEATURE_UNPUBLISH` | **on** | `POST /api/ops/review/:id/unpublish` |
| `SQLITE_CACHE` | on if `better-sqlite3` is installed | Express read cache |

---

## Quick start

```bash
# From repo root (use npm.cmd on Windows if PowerShell blocks npm.ps1)
npm.cmd install --ignore-scripts
npm.cmd --prefix server install
npm.cmd --prefix client install
npm.cmd run process
npm.cmd run server
```

In another terminal:

```bash
npm.cmd run client
```

- Website (dev): http://localhost:5173
- API: http://localhost:4000/api/health · `/api/jobs` · `POST /api/match`

Copy `.env.example` to `.env` for SMTP alerts, `SESSION_SECRET`, `OPERATOR_PASSWORD`, and optional `OPS_INGEST_TOKEN`.

```bash
npx playwright install chromium   # one-time; GHA and local JS collects
```

---

## Pipeline commands

```bash
npm run process           # seed + staging → data/processed/jobs.json + exam_series.json
npm run collect:daily     # registry collectors → staging (does not create ops collect_jobs)
npm run collect:manual    # optional CSV import → staging
npm run qa:schema         # validate published jobs + exam series
npm run pipeline:daily    # collect → process → qa → alert → snapshot
npm run cache:sqlite      # rebuild optional Express cache from JSON
npm run static:data       # copy processed JSON to client/public/data
```

### Add a job without ops

1. Edit `data/seed/jobs.json` (`hasExam`, `officialUrl`, `selectionProcess`).
2. `npm run process`.
3. Refresh the site.

Or paste an official URL in `/ops` after signing in.

**Keep published jobs:** `buildJobs.js` keeps the current `jobs.json` unless `REPLACE_PUBLISHED=1`. Do not run process against empty staging if you intend to replace the set.

---

## Tests

```bash
node tests/pr01-schema.js
node tests/pr02-sqlite.js
node tests/pr03-collectors.js
node tests/pr04-collectors.js
node tests/pr05-match.js
node tests/pr06-prepare.js
node tests/pr07-ops-auth.js
node tests/pr08-ops-paste.js
node tests/pr09a-psc.js
node tests/pr09b-boards.js
node tests/pr09c-calendars.js
node tests/pr10-hardening.js
```

---

## Hosting

| Host | Role | Notes |
| --- | --- | --- |
| **Always-on Express** (Render or equivalent) | **v1 product** | Match + ops + live JSON. `render.yaml` is still the free plan (sleeps) until upgraded. |
| **GitHub Pages** | Snapshot fallback | `client/public/data/*.json`. No match/ops. Workflow: `.github/workflows/deploy-pages.yml`. |
| **GitHub Actions** | Daily collect | `.github/workflows/daily-collect.yml` (~06:00 IST, 45 min). Commits processed JSON. Does **not** install SQLite. |

See [docs/HOSTING.md](docs/HOSTING.md) and [docs/DATA_AND_STATUS.md](docs/DATA_AND_STATUS.md).

---

## Student desk (Stage 7)

Students can **create an email + password account** on the API host, save a server profile, and (next PRs) track exams, upload admit cards/results, and run unofficial mocks.

- Design: [docs/STUDENT_COACHING_DESIGN.md](docs/STUDENT_COACHING_DESIGN.md)
- Routes: `/account/register`, `/account/login`, `/dashboard`, `/desk/:id`, `/profile`
- Student data is **gitignored** (`data/students/`, `data/student-files/`). Use a **persistent disk** in production.
- GitHub Pages does not host accounts.

## Design status

Catalog expansion (PR01–PR10) is on `master`. Student desk / coaching is specified in Stage 7; **PR11 (accounts + server profile) is in tree**. Session handoff: [docs/knowledge/2026-08-19-session-all-govt-jobs-expansion.md](docs/knowledge/2026-08-19-session-all-govt-jobs-expansion.md).

---

## Disclaimer

Aggregator only. Not affiliated with Government of India, UPSC, SSC, IBPS, SBI, RRB, any PSC, or any PSU. Always confirm eligibility, last date, and selection process on the official notification before applying.
