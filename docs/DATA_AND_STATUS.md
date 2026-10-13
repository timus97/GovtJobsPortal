# Data storage and the daily job

Status as of 2026-10-10. This document explains **where data lives**, how to tell whether a source was fetched, and how the Java daily job runs. The React client, the Express API, and the Node collectors are removed.

---

## 1. Catalog store is git JSON

The **catalog** store is **JSON files committed to git** (`data/processed/*.json`). Students are not in that JSON. The product UI is the Java host on port **8090**. GitHub Pages is not the product.

| Role | Path | Description |
|------|------|-------------|
| **Published jobs (durable SoR)** | `data/processed/jobs.json` | Exam and no-exam opportunities the host copies into Search |
| Exam calendars | `data/processed/exam_series.json` | Prepare-for rows |
| Stats | `data/processed/stats.json` | Counts by org type, sector, status, source |
| Last process run | `data/processed/run-report.json` | How many jobs published or dropped |
| Last collect run | `data/processed/collect-report.json` | Per-source outcome, records, and errors. The desk reads this file. |
| Progress while a run is going | `data/processed/collect-progress.json` | Sources done, current source, findings |
| Quarantine | `data/processed/quarantine.json` | Rows held for review |
| Snapshot for email diff | `data/processed/jobs.prev.json` | Previous jobs set |
| Last alert | `data/processed/alert-report.json` | Whether the daily email sent |
| Source registry | `data/sources/registry.json` | Portals, list URLs, enabled flag, collector |
| Human seed jobs | `data/seed/jobs.json` | Curated listings merged on process |
| Staging | `data/staging/{sourceId}/{epoch}.json` | Notices from a complete or partial run |
| Raw HTML | `data/raw/{sourceId}/{runId}/` | Archived pages |
| PDF cache | `data/raw/pdfs/` | PDF bytes and extracted text |

### Student PII and files are not git JSON

Student accounts, profiles, tracker rows, and admit/result files are **never** written to `data/processed/*.json`.

| Role | Path / env | Description |
|------|------------|-------------|
| Student SoR | Postgres `govtjobs_students` | Flyway schema. `jdbc:postgresql://127.0.0.1:5432/govtjobs_students`, user `govtjobs`. |
| Private files | `STUDENT_FILES_DIR` (default `data/student-files/`) | Admit card and result bytes. Gitignored. Owner-only PDF, JPEG, or PNG, at most 5 MB. |

**Never** put student tables in `data/cache/portal.sqlite`.

---

## 2. How to tell whether a source was fetched

`/ops/sources` lists the registry: id, name, priority, enabled, collector. It does not show the last scrape.

`/ops/fetch` shows the last `collect-report.json` row, including `outcome`.

| Outcome | Meaning |
|---------|---------|
| `complete` | Notices kept, no errors. Snapshot replaced older files. |
| `partial` | Notices kept, and something was skipped or failed. The previous non-empty snapshot stays on disk. |
| `empty` | The site was reached and nothing was kept. No snapshot was written. |
| `failed` | No notices, and the run recorded an error, a block, or a manual CAPTCHA. No snapshot was written. |
| `not_finished` | The source never started. Staging is untouched. |

`ok` is true only for `complete` and `partial`.

A source can be in the registry and still absent from an older report. The report on disk is whatever the last finished Java run wrote. There is no separate rebuild-report command. Run one source, or the daily job, to refresh it. Do not hand-edit `jobs.json`.

Live jobs are rows in `jobs.json` after `process`. The daily job runs `process` only when at least one source returned notices. A day of empty and failed sources leaves `jobs.json` unread.

---

## 3. On disk

```text
dir data\staging\psu_ntpc
type data\processed\collect-report.json
type data\processed\jobs.json
type data\processed\stats.json
```

While a daily run is going, `data/processed/collect-progress.json` has `"running": true`. The desk still reads `collect-report.json` when the run finishes.

GitHub Actions: `.github/workflows/ci.yml` is the merge gate (catalog guard, domain and collect verify, web tests on Postgres 16, boot jar). `.github/workflows/daily-collect.yml` is the scheduled crawl. `.github/workflows/codeql.yml` is Java analysis. None of these deploy the host.

---

## 4. Listings and the daily job

Published rows use `open`, `closing_soon`, and `closed`.

| Process | How to start | What it does |
|---------|----------------|--------------|
| **Daily job** | See `docs/collection-runbook.md` | Fetch, process when a source returned notices, schema-check, alert, snapshot |
| **One source** | `source <id>` | Staging only when that source kept notices |
| **Process** | `process` | Seed plus staging to `jobs.json` and `exam_series.json` |
| **Product UI** | `.\scripts\start-web.ps1` | Thymeleaf host on port 8090 |

Commands are the Java CLI after `. .\scripts\java21.ps1`. Quote `-D` arguments in PowerShell. `qa` is the same rebuild as `process`, not the schema check.

---

## 5. Data flow

```text
data/sources/registry.json
        ↓  collect daily
data/staging/{source}/…  +  data/raw/…
        ↓  process, only when a source returned notices
data/processed/jobs.json  (+ exam series, stats, reports)
        ↓  host boot
Postgres catalog schema  →  Search, Match, Prepare
```

`data/staging/ops_paste/` is read on every process. Paste does not start the daily crawl.

---

## 6. Run the collector again

Follow `docs/collection-runbook.md`. A local run leaves files on disk and does not commit them. The scheduled workflow commits `data/processed`, `data/staging`, and `data/raw/pdfs` when those paths change.

Do not dispatch a crawl from the paste screen. An empty day must not replace `jobs.json`.

---

## 7. Related docs

- `README.md` — how to run the Java host locally
- `docs/collection-runbook.md` — daily job, alert, Playwright, quarantine
- `docs/sources.md` — source tiers
- `docs/collector-fix-log.md` — collector behaviour
- `docs/classification-rules.md` — no-exam filter

---

## 8. Quick answers

| Question | Answer |
|----------|--------|
| Where is the catalog? | `data/processed/jobs.json` (git JSON). The host copies approved rows into Postgres `catalog`. |
| Where are student accounts and files? | Postgres `govtjobs_students`. Files in `STUDENT_FILES_DIR`. Not git. |
| What does a missing source in the report mean? | That id was not in the last `collect-report.json`. |
| How do I see the last fetch? | `/ops/fetch`, or `collect-report.json`. Outcomes are `complete`, `partial`, `empty`, `failed`, `not_finished`. |
| What is running now? | `collect-progress.json` while `running` is true, or the Actions tab for the scheduled job. |
| What is the public site? | The Java host. GitHub Pages is not the product. |
