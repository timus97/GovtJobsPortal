# GovtJobsPortal — All-India government jobs, match, prepare, and exam desk

Public fullstack site for **currently open Indian government and PSU applications** (exam and no-exam), plus **prepare-for calendars**, an explainable **eligibility match** (reservation category required), **ops paste-URL**, and a **student exam desk**.

Not affiliated with the Government of India or any board or PSU. Every listing is a pointer to an official URL. Always verify dates, eligibility, and fees on the notification before applying.

**Repo:** https://github.com/timus97/GovtJobsPortal  
**Default branch:** `master`  
**Design (accepted):** [docs/ALL_GOVT_JOBS_DESIGN.md](docs/ALL_GOVT_JOBS_DESIGN.md) (PR01–PR10) · [docs/STUDENT_COACHING_DESIGN.md](docs/STUDENT_COACHING_DESIGN.md) (Stage 7 PR11–PR16)

---

## What this product is

India’s recruitment surface is split across UPSC, SSC, IBPS, SBI, RRB, state PSCs, PSU career pages, regulators, and school/health boards. Candidates otherwise hunt those sites one by one, then guess whether they are eligible.

This portal is a **catalog + matcher + exam desk**, not a gazette and not an apply engine:

| Surface | Who it is for | What it does |
| --- | --- | --- |
| **Browse `/jobs`** | Anyone looking for an open window | Lists live applications. Exam and no-exam. Default filter is **all jobs**. |
| **Profile `/profile` + Match `/match`** | A candidate who will state DOB, education, state, and reservation category | Ranks currently open opportunities with pass / fail / unknown reasons. Not an official decision. Reservation category is required. |
| **Prepare `/prepare`** | Someone starting UPSC / SSC / IBPS / NET / GATE / CTET study | Official calendar rows (ExamSeries). No “Apply” unless a linked vacancy is actually open. |
| **Ops `/ops`** | Site operators only | Sign in, paste an official HTTPS URL, review extracted facts, publish or unpublish. |
| **Student exam desk** | A student on the API host | Register, server profile, dashboard tracker, days-left, private admit/result files, unofficial syllabus/plan, unofficial mocks. |

### In scope

- Central, PSU, autonomous, state-PSC, school, health, science, regulator, and post openings that have an official HTTPS page.
- Exam-bearing posts (UPSC, SSC, IBPS, SBI, RRB, PSC, and similar) **and** walk-in / interview / merit / contract / apprenticeship posts.
- Recurring exams as **ExamSeries** (prepare-for): UPSC, SSC, IBPS, SBI, RRB, **UGC NET**, **GATE**, **CTET**.
- Rule-based eligibility match: age as-on the notification date, education ladder, domicile, gender, PwBD (post-wise when the notification lists posts), reserved-only only when that fact is structured.

### Out of scope (locked)

- Student accounts on GitHub Pages. Accounts exist only on the always-on API host. No email verify, password reset, or OAuth in v1.
- Putting student PII or admit/result files in git, Pages, or `data/cache/portal.sqlite`.
- Republishing official PDFs, hall tickets, or full gazette text.
- Applying on the candidate’s behalf, OAuth to board sites, or fee payment.
- CAPTCHA bypass on defence portals (`joinindianarmy.nic.in` and similar). Those sources are **manual / calendar only**.
- **CUET** (admissions, not a job).
- Treating **UGC NET / GATE / CTET** as vacancies. They are prepare-for series.
- Inventing age relaxations or NLP-parsing free-text `eligibility[]`. Never “you are eligible.”
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
5. `/prepare` lists calendars. Apply only appears when a linked opportunity is open. **Track** adds the calendar to the desk.
6. API host only: `/account/register` then `/dashboard`. Track jobs (or **I applied**), add a custom exam, see days-left. `/desk/:id` holds one private admit card and one result.
7. `/desk/:id/plan` and `/desk/:id/mock` are unofficial syllabus/plan and unofficial timed mocks. Always verify the official site.

Persistent copy on match and desk: **“Not an official eligibility decision.”** Mocks and syllabus are always labelled **unofficial**.

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
GitHub Actions (45 min)          Git (catalog SoR)              Always-on Express + SPA + disk
----------------------           -----------------              -----------------------
runDaily.js collectors  -->      data/processed/jobs.json       GET /api/jobs
calendar PDF parser              data/processed/exam_series.json POST /api/match
buildJobs.js (seed+staging)      data/sources/registry.json     /ops paste-URL queue
prepareStaticData.js (Pages)     data/staging/ops_paste/        optional SQLite catalog cache
                                 (never student PII)            student JSON + files on disk
```

| Layer | Path | Notes |
| --- | --- | --- |
| SPA | `client/` | React 19 + Vite 8. Routes in `client/src/App.jsx`. |
| API | `server/src/` | Express on `:4000`. CORS + credentials. |
| Shared rules | `shared/` | Job / opportunity / exam-series schemas; `eligibilityMatch.js` + `eligibilityFacts.js`. |
| Collect | `scripts/collect/` | Registry-driven. Special collectors + `genericPsc` / `genericBoard` / `genericCareers`. |
| Process | `scripts/process/buildJobs.js` | Rebuilds published JSON from **seed + staging**. Does not drop exams. |
| Optional cache | `server/src/db/sqlite.js` | Catalog-only. Rebuilt by `scripts/migrate/jsonToSqlite.js` on boot. Never student tables. |
| Student SoR | `STUDENT_DATA_DIR` + `STUDENT_FILES_DIR` | Host JSON + private files. Gitignored. **Never** `portal.sqlite`. |

**Catalog SoR is git JSON** (`data/processed/*.json`). Student SoR is host JSON under `STUDENT_DATA_DIR` (default `data/students/`) and files under `STUDENT_FILES_DIR` (default `data/student-files/`). SQLite is a catalog cache discarded on free Render sleep — do not store students there.

### SPA routes

| Path | Role |
| --- | --- |
| `/jobs`, `/jobs/:id` | Browse openings (exam + no-exam) |
| `/profile`, `/match` | Eligibility profile + match (reservation required) |
| `/prepare` | ExamSeries calendars |
| `/account/register`, `/account/login` | Student account (API host) |
| `/dashboard` | Exam desk tracker |
| `/desk/:id` | One tracked item + private files |
| `/desk/:id/plan` | Unofficial syllabus / even-split plan |
| `/desk/:id/mock` | Unofficial timed mock |
| `/ops` | Operator paste-URL (separate cookie) |

### Public API routes

| Method | Path | Role |
| --- | --- | --- |
| GET | `/api/jobs`, `/api/jobs/:id` | Browse. `hasExam=all\|yes\|no`. Closed hidden unless `status=` is set. |
| GET | `/api/exam-series`, `/api/exam-series/:id` | Prepare-for calendars. |
| GET | `/api/stats`, `/api/sources`, `/api/pipeline`, `/api/health` | Counts, registry projection, last run, `sqliteCache`. |
| POST | `/api/match` | Body = profile (anonymous). 400 if `reservationCategory` missing. Logged-in match uses the server profile. |

### Student routes (`student_session`, `FEATURE_STUDENT`)

`POST /api/account/register` · `login` · `logout` · `GET /api/account/me` · `GET/PUT /api/me/profile` · `GET/POST /api/me/items` · `PATCH/DELETE /api/me/items/:id` · file upload/download · `GET /api/coaching/syllabus/:seriesId` · `GET /api/coaching/mocks/:seriesId` · `POST /api/me/mocks/:seriesId/attempts`

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

Anonymous profile (`sarkari.profile.v1` in `localStorage`). Logged-in students use the server profile:

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
| `FEATURE_STUDENT` | on (API host) | Student register/login + desk APIs |
| `VITE_FEATURE_STUDENT` | on for API host; **off** on github.io without `VITE_API_BASE` | Account / desk nav |
| `SQLITE_CACHE` | on if `better-sqlite3` is installed | Express **catalog** read cache only |

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

Copy `.env.example` to `.env` for SMTP alerts, `SESSION_SECRET` (required in production), `OPERATOR_PASSWORD`, optional `OPS_INGEST_TOKEN`, and student dirs (`STUDENT_DATA_DIR`, `STUDENT_FILES_DIR`).

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
node tests/pr11-student-account.js
node tests/pr12-tracker.js
node tests/pr13-files.js
```

PR14 syllabus/plan and PR15 unofficial mocks are specified in [docs/STUDENT_COACHING_DESIGN.md](docs/STUDENT_COACHING_DESIGN.md) (`tests/pr14-plan.js`, `tests/pr15-mocks.js`).

---

## Hosting

| Host | Role | Notes |
| --- | --- | --- |
| **Always-on Express** (Render or equivalent) | **Product host** | Match + ops + student desk. **Persistent disk required** for accounts/files. `render.yaml` is still the free plan (sleeps; ephemeral disk wipes the desk) until upgraded. |
| **GitHub Pages** | Snapshot without accounts | `client/public/data/*.json`. No match/ops/desk. Keep `VITE_FEATURE_STUDENT` off unless `VITE_API_BASE` points at the API host. |
| **GitHub Actions** | Daily collect | `.github/workflows/daily-collect.yml` (~06:00 IST, 45 min). Commits processed JSON. Does **not** install SQLite. |

See [docs/HOSTING.md](docs/HOSTING.md) and [docs/DATA_AND_STATUS.md](docs/DATA_AND_STATUS.md).

---

## Student desk (Stage 7)

On the API host, students can **register** (email + password), save a **server profile**, and use an **exam desk**:

- Dashboard tracker: official calendars, applied jobs, and custom exams. Statuses `watching` → `done`. Days-left from exam date, else last/apply date, else “add a date.”
- **Track** / **I applied** from Prepare and Job detail.
- Private admit card + result files (PDF/JPEG/PNG ≤ 5 MB, owner-only).
- Unofficial syllabus + even-split study plan; unofficial timed mocks (score + review). Never official papers.

This is **not** a board account. Aggregator only — verify the official site. The matcher never says “you are eligible.”

- Design: [docs/STUDENT_COACHING_DESIGN.md](docs/STUDENT_COACHING_DESIGN.md) · UX: [docs/STUDENT_DESK_UX.md](docs/STUDENT_DESK_UX.md)
- Routes: `/account/register`, `/account/login`, `/dashboard`, `/desk/:id`, `/desk/:id/plan`, `/desk/:id/mock`, `/profile`
- Student SoR: host JSON under `STUDENT_DATA_DIR` and files under `STUDENT_FILES_DIR` (both gitignored). **Never** `portal.sqlite`.
- Production needs a **persistent disk**. Free Render sleep/redeploy wipes those dirs. See [docs/HOSTING.md](docs/HOSTING.md).
- GitHub Pages is a catalog snapshot without accounts.

## Design status

Catalog expansion (PR01–PR10) is specified and on `master`. Stage 7 (PR11–PR16) is specified: **PR11–PR13** accounts / tracker / files are done; **PR14–PR15** unofficial syllabus/plan and mocks are in tree; **PR16** is this docs/hosting/brand landing. Next work is operational (live collect, PDF TTL, always-on host + disk), not another design PR. Session handoff: [docs/knowledge/2026-08-19-session-all-govt-jobs-expansion.md](docs/knowledge/2026-08-19-session-all-govt-jobs-expansion.md).

---

## Disclaimer

Aggregator only. Not affiliated with Government of India, UPSC, SSC, IBPS, SBI, RRB, any PSC, or any PSU. Always confirm eligibility, last date, and selection process on the official notification before applying.
