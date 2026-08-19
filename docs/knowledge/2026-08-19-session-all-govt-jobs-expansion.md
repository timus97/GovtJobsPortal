# Session knowledge: All-India govt jobs expansion

| Field | Value |
| --- | --- |
| Saved | 2026-08-19 |
| Workspace | `C:\Users\Timus97\Desktop\grokAnalysis\GovtJobsPortal` |
| Repo | https://github.com/timus97/GovtJobsPortal |
| Status | Design accepted. **PR01 implemented** (2026-08-19). Next: PR02 cache or PR03/05/07 in parallel. |
| Full design | [docs/ALL_GOVT_JOBS_DESIGN.md](../ALL_GOVT_JOBS_DESIGN.md) |

Use this article to resume a new session. Do not re-litigate locked decisions. Do not start from “no-exam only” as the product goal.

---

## What this session did

1. Explained the existing NoExam Sarkari Jobs portal (collect → process → publish, JSON files, no SQL).
2. Designed the expansion to **all Indian government jobs** (exam + no-exam), eligibility matching, future-exam calendars, and an admin scrape dashboard with paste-a-URL.
3. Researched official portals (UPSC, SSC, IBPS, SBI, RRB, NCS, Employment News, state PSCs, P1 verticals).
4. Ran a write → review → revise design loop (3 review rounds; 1 critical + 8 major + 2 minor issues closed).
5. Owner answered six product questions; those answers were written into the design doc.

---

## Current product (as built today)

- React 19 + Vite 8 SPA in `client/` (`/`, `/jobs`, `/jobs/:id`, `/process`, `/sources`, `/about`).
- Express API in `server/src/index.js` on port 4000. `jobStore.js` re-reads `data/processed/jobs.json` every request.
- Client `client/src/api/jobs.js` tries `/api` then falls back to `client/public/data/*.json` (GitHub Pages).
- **No SQL.** Durable data is git JSON.
- Pipeline: `scripts/collect/runDaily.js` → `data/staging/` → `scripts/process/buildJobs.js` → `data/processed/jobs.json`.
- Collectors live in `scripts/collect/collectors/` (`becil.js`, `ncs.js`, `employmentNews.js`, `genericCareers.js`), registered from `data/sources/registry.json`.
- **Exam posts are dropped.** `shared/jobSchema.js` `EXCLUDE_PATTERNS` + `isValidJob` requires `hasExam === false`. `buildJobs.js` drops them and overwrites `hasExam: false`. `toStaging.js` marks `hasExam === true` as `needsReview`. `schemaCheck.js` and `emailNewJobs.js` also require `hasExam === false`.
- Snapshot: **627 jobs, 562 open, 586 PSU, 200 enabled sources**, last process **2026-07-11**. **548 / 627 have `lastDate: null`** (listed as open because `computeStatus(null)` → `open`).
- Registry: **264 sources, 200 enabled**, priority **P0–P3**, 154 already P3. Real P0 scrape sources today: `ncs_gov`, `employment_news`, `becil` (+ `seed_manual`).
- `data/staging/**` is **gitignored**. A clean checkout cannot reprocess the 23 dropped exam rows.
- Seed file `data/seed/jobs.json` has a fake exam row (“Management Trainee sample WITH EXAM”) that must become a test fixture, not a published job.
- GHA `daily-collect.yml` is 45 minutes, commits processed/staging/raw PDFs, does **not** install server deps / SQLite.
- `render.yaml` is free-tier (ephemeral disk, sleeps).

---

## Target product (locked)

| Surface | Behavior |
| --- | --- |
| Browse `/jobs` | Exam **and** no-exam. Default **all jobs immediately**. |
| Profile + Match | DOB, education, birth/domicile state, PwBD, **required reservation category**. Ranked currently-open apply windows with explainable reasons. Banner: not an official eligibility decision. |
| Prepare | Official calendars as `ExamSeries`. No Apply unless a linked Opportunity is open. **UGC NET listed immediately** as prepare-for, never as a vacancy. CUET excluded. |
| Admin `/ops` | Operator accounts (hashed password + session + role). Paste official URL → review → publish. Env password only bootstraps the first admin. |

Matching is rule-based only. Never invent eligibility. Age uses notification `ageAsOnDate`. Incomplete parse → unknown, not fail, plus “verify on official site.”

Product host is **always-on Express + SPA** (fullstack). GitHub Pages may remain a snapshot fallback, not the v1 product.

---

## Owner decisions (2026-08-18) — do not reopen

1. **Reservation category is required** before match (UR / EWS / OBC / SC / ST). Use only printed age-relaxation tables; never invent relaxations.
2. **Move off static-Pages-as-product** to a fullstack website. Server match is in scope (`FEATURE_SERVER_MATCH=on`).
3. **UGC NET** is a prepare-for ExamSeries **now**, not behind a hidden flag as the default UX.
4. **Real admin auth** (operator user table / hashed password / session / role), not a shared `OPERATOR_PASSWORD` as the end state. Env password may create the first admin only.
5. **`/jobs` defaults to all jobs immediately** (no 7-day no-exam grace).
6. **PDFs** stay in private raw staging, **30-day TTL**, never copied to Pages / public JSON.

---

## Architecture locks (do not reopen)

- **Git JSON is the durable store:** `data/processed/*.json` committed by GHA. SQLite (`better-sqlite3`) is an **optional Express read cache** rebuilt on boot. Never install it in the root/GHA pipeline. Never treat Render’s disk as SoR.
- Two entities: **Opportunity** (live apply window) and **ExamSeries** (calendar / prepare-for).
- `hasExam` is a filter, not a drop. `selectionProcess` stays a **string** on `jobs.json` for compat; Opportunity may add optional `selectionProcesses[]`.
- New exam codes: `cbt`, `written_multi_stage`, `interview_after_exam`, `physical`. `classifySelectionText` must map `EXCLUDE_PATTERNS` onto those codes.
- Browse: `computeStatus(null lastDate)` stays **open** (otherwise `/jobs` empties). ACTIVE match: include those rows with Status=**unknown** and chip “Last date not listed — verify.”
- Source catalog is **only** `data/sources/registry.json` (keep `listUrls`, `enabled`, `cadence`, `category`, priority P0–P3). Do **not** add `scripts/collect/registry/psc.json`.
- New collectors go in `scripts/collect/collectors/` and register like `becil` / `ncs`.
- Eligibility facts: `shared/eligibilityFacts.js`. **No NLP** of `eligibility[]` in v1. PR05 ships on **hand-built golden fixtures**. P0 scrapes start with `eligibilityParse.complete=false`.
- Education ladder must include existing codes: `below_10`, `10th`, `12th`, `iti`, `diploma`, `graduate`, `pg`/`postgraduate`, `phd`, `experience`.
- Paste-URL publish writes `data/staging/ops_paste/<stableJobId>.json` (shape `buildJobs.js` already walks), upserts local processed JSON, then commits via dedicated **`ops-ingest.yml`**. **Never** `workflow_dispatch` `pipeline:daily` to save a paste (that rebuilds from seed + empty runner staging and wipes processed JSON). No git token → state `published_local`.
- Daily collect does **not** create one `collect_jobs` row per PSU URL. Playwright stays in GHA; Render paste is HTML/metadata only.
- RRB national apply is `rrbapply.gov.in` + zonals. **`rrbcdg.gov.in` is Chandigarh, not a national API.**
- Defence: calendar/manual only. **No CAPTCHA bypass** on `joinindianarmy.nic.in`.
- Employment News: **change** `employmentNews.js` to the free highlights table only (e-paper is paid). Today it scrapes homepage + PDFs.
- Legal: rate-limit, metadata + official URLs only, disclaimer, no PDF republish.

v1 envelope: 2k–10k opportunities, ~200 exam series, match p95 &lt; 200 ms, collect fits or splits the 45-minute GHA job, processed JSON + raw &lt; 2 GB year 1.

---

## Official sources to implement first (P0)

| sourceId | Official URLs | Notes |
| --- | --- | --- |
| `ncs_gov` | https://ncs.gov.in/ | Existing Playwright collector |
| `employment_news` | https://employmentnews.gov.in/ , https://employmentnews.gov.in/newemp/Home.aspx | Narrow to free table |
| `becil` | existing | Keep |
| `upsc` | https://upsc.gov.in/ , calendar, active-exams, https://upsconline.nic.in | Calendar PDF + live windows |
| `ssc` | https://ssc.gov.in/ , https://ssc.gov.in/for-candidates/examination-calendar | JS-heavy → Playwright in GHA |
| `ibps` | https://www.ibps.in/ | Calendar listing page, not a year-stamped PDF as the forever URL |
| `sbi_careers` | https://sbi.co.in/web/careers/current-openings | Apply on recruitment.sbi.bank.in |
| `rrb_apply` | https://www.rrbapply.gov.in/ + zonals | Not rrbcdg.gov.in |

P1 later: RBI, NABARD, SEBI, India Post, KVS/NVS/DSSSB/CTET, AIIMS/ESIC/NHM, FCI, LIC, EPFO, DRDO/ISRO/BARC, GATE score + per-PSU careers, UGC NET series, defence calendars, apprenticeshipindia, eGazette/PIB discovery, existing ~200 PSU pages.

P2: one `genericPsc.js` + registry rows. Start 10: UPPSC, BPSC, MPSC, TNPSC, WBPSC, RPSC, GPSC, KPSC, Kerala PSC, APPSC. UPSC lists PSCs at https://www.upsc.gov.in/external-links/state-public-service-commissions.

---

## Implementation order (12 PRs)

```text
01 → 02 → (03 ∥ 05 ∥ 07) → 04 → 06 → 08 → 09a → (09b ∥ 09c) → 10
```

| PR | Ship |
| --- | --- |
| **01** | Stop dropping exams across the whole pipeline + UI filter. Fixture exam row. Default `hasExam=all`. |
| **02** | Optional SQLite **cache** only. |
| **03** | UPSC + SSC + `calendarPdf.js`. Registry `listUrls`. |
| **04** | IBPS + SBI + RRB. Employment News narrowing. |
| **05** | Profile, ESM `eligibilityMatch.js` + `eligibilityFacts.js`, Vite `@shared` alias, Match page, golden fixtures. |
| **06** | ExamSeries + Prepare page (deps include PR04). |
| **07** | Operator accounts + empty `/ops` dashboard. |
| **08** | Paste-URL, PATCH review, `ops_paste`, `ops-ingest.yml`. |
| **09a** | Generic PSC + 10 registry entries. |
| **09b** | Banks / regulators / post. |
| **09c** | School / health / defence calendars. |
| **10** | PwBD post-wise, unpublish, QA fixtures. |

**Next session should implement PR01** unless the user says otherwise.

PR01 files: `shared/jobSchema.js`, `shared/opportunitySchema.js`, `shared/examSeriesSchema.js`, `scripts/collect/lib/toStaging.js`, `scripts/process/buildJobs.js`, `scripts/qa/schemaCheck.js`, `scripts/alerts/emailNewJobs.js`, `data/seed/jobs.json` (move fake exam to fixture), `server/src/routes/jobs.js`, `client/src/api/jobs.js`, `client/src/components/JobFilters.jsx`, `client/src/pages/JobsPage.jsx`, `client/src/utils/labels.js`.

Exit for PR01: existing 627 still load; one fixture exam row is queryable with `hasExam=yes`. Do not require a staging reprocess.

---

## New paths (planned, not created yet)

- `shared/opportunitySchema.js`, `shared/examSeriesSchema.js`, `shared/eligibilityMatch.js` (ESM), `shared/eligibilityFacts.js`
- `client/vite.config.js` `@shared` alias
- `server/src/db/sqlite.js` (optional cache), `server/src/routes/match.js`, `server/src/routes/ops.js`, `server/src/services/collectQueue.js`
- `scripts/migrate/jsonToSqlite.js`, `scripts/collect/lib/calendarPdf.js`
- `scripts/collect/collectors/{upsc,ssc,ibps,sbi,rrb,genericPsc}.js`
- `data/processed/opportunities.json`, `exam_series.json`, `collect-jobs.json`
- `data/staging/ops_paste/`
- `.github/workflows/ops-ingest.yml`
- Client pages: `ProfilePage`, `MatchResultsPage`, `PreparePage`, `ops/OpsDashboard`, `OpsRunDetail`, `OpsReviewQueue`

---

## Resume prompt (paste into a new session)

```text
Continue GovtJobsPortal from docs/knowledge/2026-08-19-session-all-govt-jobs-expansion.md
and docs/ALL_GOVT_JOBS_DESIGN.md. Design is accepted. Implement PR01:
stop dropping exam jobs across the full pipeline, add hasExam filter,
default /jobs to all, move the fake seed exam into a test fixture.
Do not reopen locked SoR / Pages / auth / matching decisions.
```
