# Data storage, scrape status & running jobs

This document explains **where data lives**, what **“Not scraped yet”** means on the Sources page, how to tell **scraped vs waiting**, and **what jobs/processes** exist.

---

## 1. Catalog store is git JSON (optional SQLite cache)

The **catalog** store is **JSON files committed to git** (`data/processed/*.json`). There is **no** MySQL/Postgres. `better-sqlite3` may exist on the Express host as a **catalog-only read cache rebuilt from JSON on boot** — it is not the source of record, is **not** used for student PII, and is not installed in GitHub Actions.

| Role | Path | Description |
|------|------|-------------|
| **Published jobs (durable SoR)** | `data/processed/jobs.json` | Exam + no-exam opportunities served by the API and website |
| Stats | `data/processed/stats.json` | Counts by org type, sector, status, source |
| Last process run | `data/processed/run-report.json` | How many jobs published/dropped last pipeline |
| Last collect run | `data/processed/collect-report.json` | Per-source scrape results (rows written, errors) |
| Quarantine | `data/processed/quarantine.json` | Ambiguous rows held for review |
| Snapshot for email diff | `data/processed/jobs.prev.json` | Previous jobs set (alert system) |
| Source registry | `data/sources/registry.json` | All gov/PSU portals we *intend* to scrape (URLs, enabled flag) |
| PSU catalog (from Wikipedia) | `data/sources/psu_catalog.json` | Names parsed from Wikipedia |
| Domain map | `data/sources/psu_domains.json` | Known official websites / career URLs |
| Human seed jobs | `data/seed/jobs.json` | Curated listings always merged in |
| Staging (per scrape) | `data/staging/{sourceId}/{runId}.json` | Raw-normalized scrape output before process |
| Raw HTML/PDF cache | `data/raw/...` | Audit trail of downloaded pages/PDFs (local; usually not on GitHub) |
| Public website copy | `client/public/data/*.json` | Snapshot copied for GitHub Pages static hosting |

**Live public site** reads from:

`https://timus97.github.io/GovtJobsPortal/data/jobs.json`  
(and `stats.json`, `sources.json`, etc.)

Local API reads the same logical data from `data/processed/` on your machine.

### Student PII and files are not git JSON

Student accounts, profiles, tracker rows, mock scores, and admit/result files are **never** written to `data/processed/*.json` and are **never** copied to `client/public/data/`.

| Role | Path / env | Description |
|------|------------|-------------|
| Student SoR | `STUDENT_DATA_DIR` (default `data/students/`) | Host JSON. Gitignored. |
| Private files | `STUDENT_FILES_DIR` (default `data/student-files/`) | Admit card + result per desk item. Gitignored. |

**Never** put student tables in `data/cache/portal.sqlite`. That cache is catalog-only and is discarded on rebuild / free-tier sleep. Production desk needs a persistent disk — see [HOSTING.md](HOSTING.md).

---

## 2. What “Not scraped yet” means

On the **Sources** page, each source has a **status badge**:

| Badge | Meaning |
|-------|---------|
| **Scraped · N rows** | Last collect run hit this URL and extracted **N** job-like links into staging |
| **Reached · 0 job links** | Site was contacted, but **no** job-like links were found (or page empty / blocked soft) |
| **Failed · …** | Hard error for that source (HTTP error, crash) |
| **Paused** | `enabled: false` in registry (often **no known career URL** yet) |
| **Manual only** | Human seed / CSV — not meant for automated scrape |
| **Not in last report** | Source is in the registry but **missing from the last collect-report** (collect never finished for that id, or report is stale) |
| **Has live jobs** | Even if last report is missing, `jobs.json` already has listings with this `sourceId` |

### Important distinction

- **Listed in Sources** = we *know about* the organisation (from Wikipedia + domain map).  
- **Scraped** = the daily collector actually ran against its career URL(s) and wrote staging rows.  
- **Live jobs** = after `npm run process`, jobs appear in `jobs.json` and on the site.

A source can be:

1. **In registry only** → shows until scraped  
2. **Scraped with 0 rows** → site OK but no extractable vacancies that day  
3. **Scraped with rows** → `hasExam` is a filter; exam posts are published, not dropped
4. **Paused** → no automatic scrape until a career URL is added and `enabled: true`

### Why many PSUs said “Not scraped yet”

1. Wikipedia import added **~259 PSU names** to the registry.  
2. Only sources with a known website are `enabled: true` for scraping.  
3. Status comes from **`collect-report.json`**. If that file is from an **older short run** (or a long run **crashed before writing the report**), newer PSUs look like “not scraped” even when staging folders exist.  
4. Fix: rebuild the report from staging (see below), re-run `collect:daily`, then `prepareStaticData` + deploy.

```bash
node scripts/process/rebuildCollectReport.js
node scripts/prepareStaticData.js
npm run process
```

---

## 3. How to know scraped vs in progress vs waiting

### On the website (Sources page)

- Use the **status badges** and the **legend** at the top of Sources.  
- **“Jobs live”** count = how many published jobs currently use that `sourceId`.  
- **Last collect** timestamp (from pipeline) shows when the scrape batch finished.

### On disk (authoritative)

```text
# Did we scrape this source?
dir data\staging\psu_ntpc

# What did the last collect say?
type data\processed\collect-report.json

# What is published on the site?
type data\processed\jobs.json
type data\processed\stats.json
```

### In progress

There is **no always-on progress UI** for a scrape while it runs. While `npm run collect:daily` is running:

- New folders appear under `data/staging/{sourceId}/`  
- Console prints `→ sourceId` and `wrote N records`  
- When finished, `collect-report.json` is written  

**GitHub Actions** (daily collect / Pages deploy):  
Repo → **Actions** tab → running/completed workflows.

---

## 4. What “jobs” are running currently?

“Jobs” can mean two different things:

### A) Job listings (vacancies on the site)

These are **not** background processes. They are **rows** in `jobs.json` with status:

- `open` – last date not passed  
- `closing_soon` – last date within 7 days  
- `closed` – past last date  

See them on the site: **Jobs** page, or `stats.json` (`open`, `closingSoon`, `closed`).

### B) System processes / automation

| Process | How to start | What it does |
|---------|----------------|--------------|
| **Collect (scrape)** | `npm run collect:daily` | Visits registry URLs → staging |
| **Process** | `npm run process` | staging + seed → `jobs.json` |
| **Daily pipeline** | `npm run pipeline:daily` | collect → process → QA → email → snapshot |
| **Static publish prep** | `node scripts/prepareStaticData.js` | Copies JSON into `client/public/data/` |
| **API server** | `npm run server` | Serves API + optional SPA |
| **Vite dev UI** | `npm run client` | Local React app |
| **GitHub Pages deploy** | push to `main` / Actions | Builds and publishes public site |
| **Daily collect Action** | `.github/workflows/daily-collect.yml` | Scheduled scrape (if enabled) |

**Currently running** only if you (or CI) started them. The public GitHub Pages site itself is **static** — no scraper runs in the browser.

---

## 5. Data flow (end-to-end)

```text
Wikipedia + domain map
        ↓  npm run psu:import
data/sources/registry.json
        ↓  npm run collect:daily  (scrapers)
data/staging/{source}/…  +  data/raw/…
        ↓  npm run process
data/processed/jobs.json  (+ stats, reports)
        ↓  prepareStaticData + git push
GitHub Pages  →  public website
```

---

## 6. How to re-scrape everything

```bash
# Optional: refresh PSU list from Wikipedia
npm run psu:import:fetch

# Scrape all enabled sources (long run)
npm run collect:daily

# If collect crashed after writing staging:
node scripts/process/rebuildCollectReport.js

# Build published jobs
npm run process
npm run qa:schema

# Update website data bundle
node scripts/prepareStaticData.js
git add data client/public/data
git commit -m "chore(data): refresh scrapes"
git push
```

---

## 7. Related docs

- `docs/HOSTING.md` — public URL and free hosting  
- `docs/collection-runbook.md` — how to run collectors  
- `docs/sources.md` — source tiers  
- `docs/classification-rules.md` — no-exam filter  

---

## 8. Quick answers

| Question | Answer |
|----------|--------|
| Where is the catalog? | **`data/processed/jobs.json`** (git JSON). Optional sqlite is a catalog cache only. |
| Where are student accounts/files? | Host dirs `STUDENT_DATA_DIR` / `STUDENT_FILES_DIR` — not git JSON, not Pages, not `portal.sqlite` |
| What does Not scraped yet mean? | No entry for that source in the **last collect-report** |
| How do I see scraped sources? | Badge **Scraped · N rows**, or staging folder, or live job count |
| What’s in progress? | Only while `collect:daily` / Actions is running — watch the terminal or Actions tab |
| What’s on the public Pages site? | Catalog snapshot in `client/public/data/` — no student accounts |
