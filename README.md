# NoExam Sarkari Jobs Portal

Website for **Indian central government, PSU and government company jobs that do not require a written / competitive exam**.

Stack:

- **React + Vite** frontend (`client/`)
- **Express** API (`server/`)
- **Collect → process → publish** data pipeline (`scripts/`, `data/`)

## Quick start

```bash
# From repo root (use npm.cmd on Windows if PowerShell blocks npm.ps1)
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
- API: http://localhost:4000/api/jobs  

## Pipeline

```bash
npm run process           # seed + staging → data/processed/jobs.json
npm run collect:manual    # optional CSV import → staging
npm run collect:daily     # HTTP + Playwright + PDF extract → staging
npm run alert:email       # email digest of new open jobs (needs .env)
npm run snapshot:prev     # jobs.json → jobs.prev.json (after alerts)
npm run pipeline:daily    # collect → process → qa → alert → snapshot
npm run qa:schema         # validate published jobs
npm run qa:sample         # print sample open jobs for human check
```

### One-time browser setup

```bash
npx playwright install chromium
```

### Email alerts

Copy `.env.example` to `.env` and set SMTP + `ALERT_ENABLED=true`.  
GitHub Actions daily workflow uses repository secrets for the same variables.

### Daily schedule

- GitHub Actions: `.github/workflows/daily-collect.yml` (~06:00 IST)
- Local: Task Scheduler / cron → `npm run pipeline:daily`


### Add a job

1. Edit `data/seed/jobs.json` (set `hasExam: false`, real `officialUrl`, `selectionProcess`).
2. Run `npm run process`.
3. Refresh the site.

Or use `data/seed/manual_jobs.csv` + `npm run collect:manual` + `npm run process`.

### Important files

| Path | Role |
|------|------|
| `data/seed/jobs.json` | Curated seed listings |
| `data/sources/registry.json` | Source map |
| `data/processed/jobs.json` | Published dataset |
| `scripts/process/buildJobs.js` | Pipeline |
| `docs/collection-runbook.md` | Ops guide |

## Scope

**Included:** walk-in, interview-only, merit, contract interview, direct recruitment without exam, apprenticeships (labelled).

**Excluded:** CBT/written tests, GATE/SSC/UPSC/IBPS style exams.

## Public hosting (free)

See **[docs/HOSTING.md](docs/HOSTING.md)** for full steps.

| Host | Cost | URL style |
|------|------|-----------|
| **GitHub Pages** (default) | Free | `https://timus97.github.io/GovtJobsPortal/` |
| **Render.com** Blueprint | Free tier | `https://<name>.onrender.com` |

```bash
# Build static site for GitHub Pages
node scripts/prepareStaticData.js
set VITE_BASE=/GovtJobsPortal/
npm.cmd --prefix client run build
```

Workflow: `.github/workflows/deploy-pages.yml` deploys on every push to `main`.

## PSU coverage

Sources imported from [Wikipedia: Public Sector Undertakings in India](https://en.wikipedia.org/wiki/Public_Sector_Undertakings_in_India):

```bash
npm.cmd run psu:import:fetch   # refresh wiki list → registry
npm.cmd run collect:daily      # scrape enabled career URLs
npm.cmd run process
node scripts/prepareStaticData.js
```

## Disclaimer

Not affiliated with Government of India or any PSU. Always verify on the official notification before applying.
