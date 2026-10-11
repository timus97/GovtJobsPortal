# Collection runbook

The daily job is the Java collect CLI. Load Temurin 21 before any `mvnw` command. This machine’s default `JAVA_HOME` is JDK 17.

```powershell
. .\scripts\java21.ps1
.\mvnw.cmd -pl collect -am -DskipTests exec:java "-Dexec.args=daily"
```

That command fetches enabled sources, rebuilds the catalog only when at least one source returned notices, schema-checks `data/processed/jobs.json` and `exam_series.json`, sends the new-job alert, then copies `jobs.json` to `jobs.prev.json`.

- A day with no notices exits 0 and leaves `jobs.json` unread.
- A schema failure exits 1. The alert does not send and the snapshot does not run.
- An alert transport failure exits 1 and the snapshot does not run.
- A disabled alert, missing `SMTP_HOST` or `ALERT_TO`, a missing `jobs.prev.json`, or a day with no new open jobs is not a send failure. The job writes `alert-report.json` and still snapshots.
- `qa` on the CLI is the same rebuild as `process`. It is not the schema check. The schema check runs inside `daily`.

## One source

```powershell
. .\scripts\java21.ps1
.\mvnw.cmd -pl collect -am -DskipTests exec:java "-Dexec.args=source becil"
```

A complete or partial source exits 0 and writes a snapshot only when it kept notices. An empty, failed, or unfinished source exits 1 and leaves the previous snapshot in place.

## Rebuild the catalog

```powershell
. .\scripts\java21.ps1
.\mvnw.cmd -pl collect -am -DskipTests exec:java "-Dexec.args=process"
```

Replace a source’s previously published jobs even when the new published set is smaller:

```powershell
$env:REPLACE_PUBLISHED = "1"
.\mvnw.cmd -pl collect -am -DskipTests exec:java "-Dexec.args=process"
```

The same switch is `--replace-published` on the `process` arguments. Without it, a source whose new snapshot publishes nothing keeps the jobs already in `jobs.json`.

## Schedule

- **GitHub Actions:** `.github/workflows/daily-collect.yml` (cron `30 0 * * *`, about 06:00 IST, 45 minutes, Temurin 21). `PDF_MAX_PER_RUN` is 25 there. The workflow commits `data/processed`, `data/staging`, and `data/raw/pdfs` when those paths change. `data/staging` and `data/raw` are gitignored except `ops_paste`, so a normal commit updates the tracked catalog files.
- **Windows Task Scheduler:** run the two daily-job lines above in the repo folder. Dot-source `scripts\java21.ps1` in the same PowerShell session as `mvnw.cmd`.

## Email alerts

1. Copy `.env.example` to `.env`. Values already set in the environment win over the file.
2. Set `ALERT_ENABLED=true`, `SMTP_HOST`, and `ALERT_TO`.
3. The first run skips the email unless `ALERT_ON_FIRST_RUN=true`.
4. A new job is an id that is absent from `jobs.prev.json` and whose status is `open` or `closing_soon`. The message lists at most 40.
5. Subject: `[Sarkari Jobs] N new government/PSU job(s)`.
6. `PUBLIC_SITE_URL` defaults to `http://localhost:8090`.

GitHub secrets: `ALERT_ENABLED`, `ALERT_ON_FIRST_RUN`, `ALERT_TO`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `SMTP_FROM`, `PUBLIC_SITE_URL`.

`SMTP_SECURE=true` uses SSL. Otherwise the client uses STARTTLS. The default port is 587.

## Playwright

NCS, SSC, and other browser sources need Playwright Chromium. Java always runs the browser headless.

```powershell
. .\scripts\java21.ps1
.\mvnw.cmd -pl collect -am -DskipTests exec:java "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.args=install chromium"
```

The GitHub Actions job installs Chromium with `--with-deps` after packaging `collect`.

## PDF and raw archive

- PDF bytes and extracted text: `data/raw/pdfs/{sha256}.pdf` and `.txt`
- HTML archive: `data/raw/<sourceId>/<runId>/<url-hash>.html`
- Ten PDFs per source. `PDF_MAX_PER_RUN` defaults to 200 when unset, and the scheduled workflow sets 25.
- A notice PDF is kept only when its text is recruitment evidence. A failed download does not use up the cap.

## Add a job manually

1. Edit `data/seed/jobs.json`.
2. Run `process` as above.

Paste of an official `https://` URL stays on `/ops`. Paste does not start the daily crawl.

## Quarantine

Review `data/processed/quarantine.json`. To keep a row for the next process:

```powershell
.\mvnw.cmd -pl collect -am -DskipTests exec:java "-Dexec.args=approve <id> <reason>"
```

## Reports

| File | Meaning |
|------|---------|
| `data/processed/collect-report.json` | Per-source outcome. The desk reads this file. |
| `data/processed/collect-progress.json` | Progress while a run is going. |
| `data/processed/run-report.json` | Process counts |
| `data/processed/alert-report.json` | Last email attempt |

Outcomes are `complete`, `partial`, `empty`, `failed`, and `not_finished`. `ok` is true only when the run kept notices.
