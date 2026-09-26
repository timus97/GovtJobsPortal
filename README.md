# GovtJobsPortal — All-India government jobs, match, prepare, and exam desk

Public fullstack site for **currently open Indian government and PSU applications** (exam and no-exam), plus **prepare-for calendars**, an explainable **eligibility match** (reservation category required), **ops paste-URL**, and a **student exam desk**.

Not affiliated with the Government of India or any board or PSU. Every listing is a pointer to an official URL. Always verify dates, eligibility, and fees on the notification before applying.

**Repo:** https://github.com/timus97/GovtJobsPortal  
**Default branch:** `master`  
**Design (accepted):** [docs/ALL_GOVT_JOBS_DESIGN.md](docs/ALL_GOVT_JOBS_DESIGN.md) (PR01–PR10) · desk accounts in [docs/STUDENT_COACHING_DESIGN.md](docs/STUDENT_COACHING_DESIGN.md) (PR11–PR16). Coaching packs in that doc are **out of scope**.

**Java 21 product host:** [docs/JAVA21_REWRITE.md](docs/JAVA21_REWRITE.md). Spring Boot + Thymeleaf (HTML/CSS). Node remains for collectors until the UI is done.

This machine’s default `JAVA_HOME` is JDK 17. Temurin 21 is stored in `.grok/java-home.txt`. Always start the host with that JDK:

```powershell
.\scripts\start-web.ps1
```

That loads Temurin 21, starts Postgres 16 (`docker compose` `student-db`), packages `web` if needed, and binds **8090** (8080 is taken here). Open http://localhost:8090 — register, profile, match, jobs, prepare, desk, ops.

Set `SESSION_SECRET` yourself before starting. The start script falls back to a shared local value when it is unset. Leave `RESEND_API_KEY` empty unless you have created your own key. `COOKIE_SECURE=true` is required before any public HTTPS host, or the forgot-password page prints the reset link.

Student accounts live in Postgres. Password reset uses SMTP/Resend when configured; locally it prints a one-time link on `/account/forgot` (`mail=dev` on `/health`).

For any other Maven/Java command:

```powershell
. .\scripts\java21.ps1
.\mvnw.cmd test
```

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
| **Student exam desk** | A student on the Java host | Register, server profile, dashboard tracker, days-left, private admit/result files. Syllabus, study plan, and mocks are **out of scope**. |

### In scope

- Central, PSU, autonomous, state-PSC, school, health, science, regulator, and post openings that have an official HTTPS page.
- Exam-bearing posts (UPSC, SSC, IBPS, SBI, RRB, PSC, and similar) **and** walk-in / interview / merit / contract / apprenticeship posts.
- Recurring exams as **ExamSeries** (prepare-for): UPSC, SSC, IBPS, SBI, RRB, **UGC NET**, **GATE**, **CTET**.
- Rule-based eligibility match: age as-on the notification date, education ladder, domicile, gender, PwBD (post-wise when the notification lists posts), reserved-only only when that fact is structured.

### Out of scope (locked)

- **Coaching** (syllabus packs, study plans, timed mocks). Four sample packs remain under `data/coaching/` and are not being extended.
- **Hosting.** GitHub Pages is not the product. Deploy the Java host later, when the UI is complete. Do not refresh `client/public/data/` for Pages.
- Email verify and OAuth. Password reset exists on the Java host (dev link locally; SMTP or Resend when configured).
- Putting student PII or admit/result files in git, Pages, or `data/cache/portal.sqlite`.
- Republishing official PDFs, hall tickets, or full gazette text.
- Applying on the candidate’s behalf, OAuth to board sites, or fee payment.
- CAPTCHA bypass on defence portals (`joinindianarmy.nic.in` and similar). Those sources are **manual / calendar only**.
- **CUET** (admissions, not a job).
- Treating **UGC NET / GATE / CTET** as vacancies. They are prepare-for series.
- Inventing age relaxations or NLP-parsing free-text `eligibility[]`. Never “you are eligible.”
- Using `rrbcdg.gov.in` as the national RRB host (it is Chandigarh zonal). National apply is `rrbapply.gov.in`.

---

## Where the project is (2026-09-26)

**Now:** build the Java UI and its design. **Next:** migrate collectors from Node to Java. **Later:** host the finished app. Coaching stays out.

| Area | State |
| --- | --- |
| Java UI: landing, `/jobs`, `/prepare`, `/profile`, `/match`, desk, ops paste | Usable locally on `:8090`. This is the work in progress. |
| Student desk | Postgres 16, Flyway `V1__student_desk.sql`, signed `student_session` / `ops_session`, forgot/reset, files on disk |
| Coaching | Out of scope. Packs exist for `upsc-cse`, `ssc-cgl`, `ibps-po`, `ugc-net` only. |
| Ops | Local paste / review / publish. No source edit, no git ingest. Publish writes `jobs.json` and does not update `opportunities.json`, which Match prefers when that file is non-empty. |
| Catalog the UI reads | Postgres schema `catalog`, approved rows only. Local boot seeds 5 sample notices and 3 calendars when the table is empty (`govtjobs.catalog.seed-dummy`). A `needs_review` row stays hidden. Git JSON remains the collector inbox until the collector port. |
| Collectors | Still Node (`scripts/collect/runDaily.js`). Java `collect process` only checks that the JSON files exist. |
| Hosting | Not in progress. Ignore https://timus97.github.io/GovtJobsPortal/ until we choose a host. |

Node `client/`, `server/`, and `scripts/collect/` stay in the tree until collector migration and cutover. Do not delete them in UI work.

---

## How a candidate uses it

1. Open `/jobs`. Filter by exam / no-exam, org type, sector, last date.
2. Open a card → official URL. The portal never hosts the notification PDF.
3. Optional: `/profile` — date of birth, highest education, **reservation category (required)**, birth/domicile state, optional gender and PwBD.
4. `/match` calls `POST /api/match`. Each row has chips (age, education, PwBD, category, last date). Unknown facts lower confidence; they do not fail the row.
5. `/prepare` lists calendars. Apply only appears when a linked opportunity is open. **Track** adds the calendar to the desk.
6. API host only: `/account/register` then `/dashboard`. Track jobs (or **I applied**), add a custom exam, see days-left. `/desk/:id` holds one private admit card and one result.
Persistent copy on match and desk: **“Not an official eligibility decision.”** Desk plan and mock routes still exist from earlier work; they are not part of the current UI.

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
Node collectors (until the UI is done)     Git (catalog SoR)              Java 21 host (local)
-------------------------------------      -----------------              -------------------
scripts/collect/runDaily.js          -->   data/processed/jobs.json       Thymeleaf pages :8090
scripts/process/buildJobs.js               exam_series.json               Match, desk, ops
                                           data/sources/registry.json     Postgres 16 students
                                           (never student PII)            admit/result files on disk
```

| Layer | Path | Notes |
| --- | --- | --- |
| Product UI | `web/` | Spring Boot 3.4 + Thymeleaf. This is what we are designing. |
| Rules | `domain/` | Schemas, match, desk rules. Port of `shared/`. |
| Collect CLI | `collect/` | `process` does not rebuild the catalog yet. |
| Collectors | `scripts/collect/` | Still the daily crawl. Migrate to Java after the UI. |
| Previous UI | `client/` + `server/` | React + Express. Keep until cutover. Not the host. |
| Student SoR | Postgres `govtjobs_students` | Flyway `web/src/main/resources/db/migration/`. Files under `STUDENT_FILES_DIR`. Never `portal.sqlite`. |

**Catalog SoR is git JSON** (`data/processed/*.json`). Student rows are Postgres. Admit/result bytes stay on disk.

### Java pages

| Path | Role |
| --- | --- |
| `/`, `/account/login`, `/account/register`, `/account/forgot`, `/account/reset` | Sign in, create account, reset |
| `/jobs`, `/jobs/{id}` | Browse openings |
| `/prepare` | ExamSeries calendars |
| `/profile`, `/match` | Signed-in profile and match |
| `/dashboard`, `/desk/{id}` | Tracker and one item, including private files |
| `/ops`, `/ops/login` | Operator paste-URL |

`/desk/{id}/plan` and `/desk/{id}/mock` are leftover coaching routes. Do not design or extend them.

### Previous SPA routes (Node, kept until cutover)

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

### Student routes on the old Express API (`student_session`)

`POST /api/account/register` · `login` · `logout` · `GET /api/account/me` · `GET/PUT /api/me/profile` · `GET/POST /api/me/items` · `PATCH/DELETE /api/me/items/:id` · file upload/download. Coaching routes under `/api/coaching/` and `/api/me/mocks/` are out of scope.

### Ops routes (session cookie)

`POST /api/ops/login` · `GET /api/ops/me` · `POST /api/ops/operators` (admin) · `POST /api/ops/collect` · review `PATCH` / publish / reject / **unpublish**.

---

## Data model (product language)

- **Opportunity** — a live apply window (what `/jobs` and match score).
- **ExamSeries** — a recurring calendar row (what `/prepare` shows). `applyNever` on NET / GATE / CTET.
- **Source** — one row in `data/sources/registry.json` (`sourceId`, `listUrls`, `priority` P0–P3, `enabled`, `collector`).
- **CollectJob** — paste-URL review state in `data/processed/collect-jobs.json` (not catalog SoR).

Published files: `data/processed/jobs.json`, `exam_series.json`, `stats.json`, `opportunities.json`. Do not copy these to `client/public/data/` for GitHub Pages.

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

Copy `.env.example` to `.env` for SMTP alerts, `SESSION_SECRET` (required in production), `OPERATOR_PASSWORD`, optional `OPS_INGEST_TOKEN`, and student store settings.

**Student database (Docker Postgres, optional):**

```bash
npm run db:up
npm run student:import-json    # one-time copy of data/students/students.json
npm run server:pg              # STUDENT_STORE=postgres
```

Switch back with `STUDENT_STORE=json` (default) or unset `STUDENT_DATABASE_URL`. Admit/result files stay on disk (`STUDENT_FILES_DIR`). The catalog stays git JSON.

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

Not in this sprint. Run the Java host locally with `.\scripts\start-web.ps1`. GitHub Pages and the old Render blueprint are not the product. Daily collect stays in `.github/workflows/daily-collect.yml` until the Java pipeline replaces it.

Catalog files on disk are described in [docs/DATA_AND_STATUS.md](docs/DATA_AND_STATUS.md).

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
- Student rows are in Postgres. Admit and result files stay on disk under `STUDENT_FILES_DIR`.

## Design status

Catalog design is [docs/ALL_GOVT_JOBS_DESIGN.md](docs/ALL_GOVT_JOBS_DESIGN.md). The Java host plan is [docs/JAVA21_REWRITE.md](docs/JAVA21_REWRITE.md). Next sprint is the crawl, build, and publish pipeline. Coaching stays out of scope.

---

## Disclaimer

Aggregator only. Not affiliated with Government of India, UPSC, SSC, IBPS, SBI, RRB, any PSC, or any PSU. Always confirm eligibility, last date, and selection process on the official notification before applying.
