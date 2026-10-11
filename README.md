# GovtJobsPortal — All-India government jobs, match, prepare, and exam desk

Public fullstack site for **currently open Indian government and PSU applications** (exam and no-exam), plus **prepare-for calendars**, an explainable **eligibility match** (reservation category required), **ops paste-URL**, and a **student exam desk**.

Not affiliated with the Government of India or any board or PSU. Every listing is a pointer to an official URL. Always verify dates, eligibility, and fees on the notification before applying.

**Repo:** https://github.com/timus97/GovtJobsPortal  
**Default branch:** `master`  
**Design (accepted):** [docs/ALL_GOVT_JOBS_DESIGN.md](docs/ALL_GOVT_JOBS_DESIGN.md) (PR01–PR10) · desk accounts in [docs/STUDENT_COACHING_DESIGN.md](docs/STUDENT_COACHING_DESIGN.md) (PR11–PR16). Coaching packs in that doc are **out of scope**.

**Java 21 product host:** [docs/JAVA21_REWRITE.md](docs/JAVA21_REWRITE.md). Spring Boot 4.1.1 + Thymeleaf (HTML/CSS). Collectors and the daily job are Java as of 2026-10-10.

This machine’s default `JAVA_HOME` is JDK 17. Temurin 21 is stored in `.grok/java-home.txt`. Always start the host with that JDK:

```powershell
.\scripts\start-web.ps1
```

That loads Temurin 21, starts Postgres 16 (`docker compose` `student-db`), packages `web` if needed, and binds **8090** (8080 is taken here). Open http://localhost:8090 — register, profile, match, jobs, prepare, desk, ops.

Set `SESSION_SECRET` yourself before starting. The start script falls back to a shared local value when it is unset. Leave `RESEND_API_KEY` empty unless you have created your own key. `COOKIE_SECURE=true` is required before any public HTTPS host. With no mailer, the forgot-password page shows the one-time reset link only for a loopback request.

Student accounts live in Postgres, published on `127.0.0.1:5432`. Password reset uses SMTP or Resend when configured. On this machine, loopback `/health` reports `mail=dev` until a mailer is set; any other client sees `mail=unconfigured`.

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
- **Hosting.** GitHub Pages is not the product. Deploy the Java host later, when the UI is complete.
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

## Where the project is (2026-10-10)

**Now:** the Java UI, and the Java daily job. **Later:** host the finished app. Coaching stays out.

| Area | State |
| --- | --- |
| Java UI: landing, `/jobs`, `/prepare`, `/profile`, `/match`, desk, ops paste | Usable locally on `:8090`. This is the work in progress. |
| Student desk | Postgres 16, Flyway `V1__student_desk.sql`, signed `student_session` / `ops_session`, forgot/reset, files on disk |
| Coaching | Out of scope. Packs exist for `upsc-cse`, `ssc-cgl`, `ibps-po`, `ugc-net` only. |
| Ops | Local paste / review / publish. No source edit, no git ingest. Publish writes `jobs.json` and does not update `opportunities.json`, which Match prefers when that file is non-empty. |
| Catalog the UI reads | Postgres schema `catalog`, approved rows only. Local boot seeds 5 sample notices and 3 calendars when the table is empty (`govtjobs.catalog.seed-dummy`). A `needs_review` row stays hidden. Collect publishes git JSON (`data/processed/jobs.json`); the host copies it at boot. |
| Collectors | Java daily job. See `docs/collection-runbook.md`. |
| Hosting | Not in progress. GitHub Pages is not the product. |

---

## How a candidate uses it

1. Open `/jobs`. Filter by exam / no-exam, org type, sector, last date.
2. Open a card → official URL. The portal never hosts the notification PDF.
3. Optional: `/profile` — date of birth, highest education, **reservation category (required)**, birth/domicile state, optional gender and PwBD.
4. `/match` ranks currently open rows from the saved profile. Each row has chips (age, education, PwBD, category, last date). Unknown facts lower confidence; they do not fail the row.
5. `/prepare` lists calendars. Apply only appears when a linked opportunity is open. **Track** adds the calendar to the desk.
6. `/account/register`, then `/dashboard`. Track jobs (or **I applied**), add a custom exam, see days-left. `/desk/{id}` holds one private admit card and one result.
Persistent copy on match and desk: **“Not an official eligibility decision.”** Desk plan and mock routes still exist from earlier work; they are not part of the current UI.

---

## How an operator uses it

1. `/ops/login` — individual username + password. `OPERATOR_PASSWORD` creates the first `admin` only when the operator table is empty, then cannot sign in.
2. Paste an `https://` official URL (`.gov.in` / `.nic.in` or the extra-host allowlist).
3. Queue states: `pending` → `needs_review` / `valid` / `invalid`. Edit facts, publish, reject, or **unpublish**.
4. Publish writes `data/staging/ops_paste/<id>.json` and upserts local `jobs.json`. With `OPS_INGEST_TOKEN` the staging file is committed via `.github/workflows/ops-ingest.yml`. Without a token the row is `published_local` on this host until someone commits the file.
5. Paste does not start the daily crawl. The desk daily command runs the Java daily job. A day with no notices leaves `jobs.json` unread.

---

## Architecture

```
Java collect daily                         Git (catalog SoR)              Java 21 host (local)
------------------                         -----------------              -------------------
fetch, then process when a source          data/processed/jobs.json       Thymeleaf pages :8090
returned notices                           exam_series.json               Match, desk, ops
                                           data/sources/registry.json     Postgres 16 students
                                           (never student PII)            admit/result files on disk
```

| Layer | Path | Notes |
| --- | --- | --- |
| Product UI | `web/` | Spring Boot 4.1.1 + Thymeleaf. |
| Rules | `domain/` | Schemas, match, desk rules. |
| Collect | `collect/` | Daily job: fetch, process, schema-check, alert, snapshot. |
| Student SoR | Postgres `govtjobs_students` | Flyway `web/src/main/resources/db/migration/`. Files under `STUDENT_FILES_DIR`. Never `portal.sqlite`. |

The React client and the Express server were removed on 2026-10-10.

**Catalog SoR is git JSON** (`data/processed/*.json`). Student rows are Postgres. Admit/result bytes stay on disk.

### Java pages

| Path | Role |
| --- | --- |
| `/`, `/account/login`, `/account/register`, `/account/forgot`, `/account/reset` | Sign in, create account, reset |
| `/jobs`, `/jobs/{id}` | Browse openings |
| `/prepare` | ExamSeries calendars |
| `/profile`, `/match` | Signed-in profile and match |
| `/dashboard`, `/desk/{id}` | Tracker and one item, including private files |
| `/ops`, `/ops/login`, `/ops/fetch`, `/ops/sources` | Operator paste, daily fetch, registry |

`/desk/{id}/plan` and `/desk/{id}/mock` are leftover coaching routes. Do not design or extend them.

---

## Data model (product language)

- **Opportunity** — a live apply window (what `/jobs` and match score).
- **ExamSeries** — a recurring calendar row (what `/prepare` shows). `applyNever` on NET / GATE / CTET.
- **Source** — one row in `data/sources/registry.json` (`sourceId`, `listUrls`, `priority` P0–P3, `enabled`, `collector`).
- **CollectJob** — paste-URL review state in `data/processed/collect-jobs.json` (not catalog SoR).

Published files: `data/processed/jobs.json`, `exam_series.json`, `stats.json`, `opportunities.json`. GitHub Pages is not the product.

---

## Collectors and sources

Registered in `data/sources/registry.json`. The Java collector reads `collector` and `sourceId`.

| Priority | What | Collector |
| --- | --- | --- |
| P0 | NCS, Employment News (free highlights table only), BECIL, UPSC, SSC, IBPS, SBI, RRB | dedicated + `employmentNews` |
| P1 | RBI, NABARD, SEBI, India Post, FCI, LIC, EPFO | `genericBoard` |
| P1 | KVS, NVS, DSSSB, AIIMS, ESIC, NHM, DRDO, ISRO, BARC | `genericCareers` / `genericPsc` |
| P1 | GATE / CTET / UGC NET calendars | `genericPsc` (`*_calendar` → ExamSeries, not a vacancy) |
| P1 | Army / Navy / Air Force | `method: manual`, `enabled: false` |
| P2 | 10 state PSCs (UPPSC, BPSC, MPSC, TNPSC, WBPSC, RPSC, GPSC, KPSC, Kerala PSC, APPSC) | `genericPsc` |
| P1–P3 | Existing PSU career pages | `genericCareers` |

Playwright Chromium runs for NCS, SSC, and other browser sources. Paste on `/ops` extracts the official page. It does not start the daily crawl.

---

## Eligibility match (rules, not NLP)

Signed-in students use the server profile:

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
| `FEATURE_STUDENT` | true | Student register, login, and desk |
| server match | true | `/match` uses the signed-in student's profile |
| prepare | true | Prepare calendars |
| `FEATURE_UNPUBLISH` | true | Operator unpublish |

---

## Quick start

The product UI starts with `.\scripts\start-web.ps1` after Temurin 21 is loaded (see the top of this file). Open http://localhost:8090.

Student accounts use Postgres. `start-web.ps1` runs `docker compose up -d student-db`. Flyway owns the schema. Admit and result files stay on disk (`STUDENT_FILES_DIR`). The catalog stays git JSON.

Copy `.env.example` to `.env` for SMTP alerts, `SESSION_SECRET` (required before a public host), `OPERATOR_PASSWORD`, and optional `OPS_INGEST_TOKEN`.

Playwright Chromium, once, for browser sources:

```powershell
. .\scripts\java21.ps1
.\mvnw.cmd -pl collect -am -DskipTests exec:java "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.args=install chromium"
```

The daily job, one source, process, and quarantine approval are in [docs/collection-runbook.md](docs/collection-runbook.md).

### Add a job without ops

1. Edit `data/seed/jobs.json` (`hasExam`, `officialUrl`, `selectionProcess`).
2. Run `process` from the runbook.
3. Refresh the site.

Or paste an official URL in `/ops` after signing in.

**Keep published jobs:** process keeps the current `jobs.json` rows for a source whose new snapshot publishes nothing, unless `REPLACE_PUBLISHED=1` or `--replace-published`. Do not set that flag when you mean to keep the previous catalog.

---

## Tests

```powershell
. .\scripts\java21.ps1
.\mvnw.cmd -pl domain,collect -am verify
.\mvnw.cmd -pl web -am test
```

Quote `-Dtest=...` in PowerShell. Golden fixtures stay at `tests/fixtures/golden-opportunities.json`.

---

## Hosting

Not in this sprint. Run the Java host locally with `.\scripts\start-web.ps1`. GitHub Pages is not the product. The scheduled collect is `.github/workflows/daily-collect.yml` (Temurin 21, the Java daily job).

Catalog files on disk are described in [docs/DATA_AND_STATUS.md](docs/DATA_AND_STATUS.md).

---

## Student desk

On this host, students can **register** (email + password), save a **server profile**, and use an **exam desk**:

- Dashboard tracker: official calendars, applied jobs, and custom exams. Statuses `watching` → `done`. Days-left from exam date, else last/apply date, else “add a date.”
- **Track** / **I applied** from Prepare and Job detail.
- Private admit card + result files (PDF/JPEG/PNG ≤ 5 MB, owner-only).

Plan and mock routes are leftover coaching pages. Do not extend them.

This is **not** a board account. Aggregator only — verify the official site. The matcher never says “you are eligible.”

- Design: [docs/STUDENT_COACHING_DESIGN.md](docs/STUDENT_COACHING_DESIGN.md) · UX: [docs/STUDENT_DESK_UX.md](docs/STUDENT_DESK_UX.md)
- Routes: `/account/register`, `/account/login`, `/dashboard`, `/desk/{id}`, `/profile`
- Student rows are in Postgres. Admit and result files stay on disk under `STUDENT_FILES_DIR`. **Never** `portal.sqlite`.

## Design status

Catalog design is [docs/ALL_GOVT_JOBS_DESIGN.md](docs/ALL_GOVT_JOBS_DESIGN.md). The Java host plan is [docs/JAVA21_REWRITE.md](docs/JAVA21_REWRITE.md). Collectors and the daily job are Java. Next is the desk UI, then hosting. Coaching stays out of scope.

---

## Disclaimer

Aggregator only. Not affiliated with Government of India, UPSC, SSC, IBPS, SBI, RRB, any PSC, or any PSU. Always confirm eligibility, last date, and selection process on the official notification before applying.
