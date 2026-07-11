# Collection runbook

## Daily pipeline (recommended)

```bash
# Install once
npm.cmd install
npx playwright install chromium

# Full daily: scrape → process → schema QA → email alert → snapshot
npm.cmd run pipeline:daily
```

Or step by step:

```bash
npm.cmd run collect:daily
npm.cmd run process
npm.cmd run qa:schema
npm.cmd run alert:email
npm.cmd run snapshot:prev
```

Single source:

```bash
node scripts/collect/runDaily.js --source becil
```

## Schedule

- **GitHub Actions:** `.github/workflows/daily-collect.yml` (cron `30 0 * * *` ≈ 06:00 IST)
- **Windows Task Scheduler:** run `npm.cmd run pipeline:daily` in the repo folder daily

## Email alerts

1. Copy `.env.example` → `.env`
2. Set `ALERT_ENABLED=true` and SMTP fields
3. First run: either accept no email (`ALERT_ON_FIRST_RUN=false`) or set `true` once
4. After `snapshot:prev`, only **new** open job ids trigger mail

GitHub secrets: `ALERT_ENABLED`, `ALERT_TO`, `SMTP_*`, `PUBLIC_SITE_URL`

## Playwright

Used for NCS (`ncs_gov`) and any registry source with `"render": "browser"`.

If Chromium missing: `npx playwright install chromium`

## PDF extraction

- New PDFs cached under `data/raw/pdfs/{hash}.pdf` + `.txt`
- Cap: `PDF_MAX_PER_RUN` (default 25)
- Full text used for classification; site only stores summary + official URL

## Add a job manually

1. Edit `data/seed/jobs.json` or CSV + `npm run collect:manual`
2. `npm run process`

## Quarantine

Review `data/processed/quarantine.json` for ambiguous scraped rows.

## Reports

| File | Meaning |
|------|---------|
| `data/processed/collect-report.json` | Scrape metrics |
| `data/processed/run-report.json` | Process metrics |
| `data/processed/alert-report.json` | Last email attempt |
