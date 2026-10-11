# Collector fix log

Developer notes for the Java registry collector. List URLs stay in `docs/collector-verified-urls.md` and `data/sources/registry.json`.

## 2026-10-10 — Last good staging, and empty / partial / failed

A source row now has `outcome`: `complete`, `partial`, `empty`, `failed`, or `not_finished`. `ok` is true only when the run kept notices (`complete` or `partial`).

- `complete`: write `data/staging/<sourceId>/<epoch>.json` and delete older `.json` and `.jsonl` files in that directory.
- `partial`: write the new snapshot, keep the newest previous non-empty file, and delete empty snapshots. An error alongside kept notices, including a skipped PDF, is enough to make a run partial. Process reads the new file. The previous file is the recovery copy. It is not merged into Search.
- `empty` and `failed`: write nothing and delete nothing. The attempt is only in `data/processed/collect-report.json`.
- `not_finished`: the slot never ran. Staging is untouched.
- Defence and manual rows stay `failed` with `blocked or CAPTCHA page; not solved` and do not gain a staging file.
- `collect daily` rebuilds the catalog only when at least one source is `complete` or `partial`. A day of empty and failed sources leaves `jobs.json` unread.
- Java `collect process` skips a newer empty snapshot and reads the newest file that still has notices. `ops_paste` still reads every paste file. A snapshot whose rows are all quarantined does not, by itself, remove that source’s previously published jobs. A snapshot that publishes at least one job still replaces that source’s older published rows.
- A snapshot that an older run already deleted cannot be restored.

The daily job is the Java pipeline. An empty or failed source still writes nothing. Process still skips an empty snapshot.

To recover a thin partial: move the newer file out of `data/staging/<sourceId>/`, then rebuild the catalog. Do not hand-edit `jobs.json`.

## 2026-10-10 — Calendar PDFs and notice PDF evidence

- IBPS, UPSC, SSC, and `*_calendar` sources discover up to five calendar PDF links and download at most two. The page URL, or the link itself, has to say calendar, programme, schedule, or timetable. Job-link scoring is not used, so a title such as "Annual Calendar 2026 (PDF)" is still opened.
- Rows from that file use `calendar-v1`. `officialUrl` is the listing page. A year-stamped PDF is still refused as a `listUrl`, and it is not stored as the official link.
- A timeout, an HTTP error, or a file that is not a PDF records `calendar PDF parse failed` and does not invent exam rows. If nothing else was kept, the run is `failed` and the previous snapshot stays.
- A notice PDF is staged only when its text is recruitment evidence (`pdf-v1`). A tender, a manual, a timeout, a failed fetch, an unreadable file, or the ten-PDF cap is `pdf skipped`, and that anchor is not published. Calendar downloads use that same cap.

## 2026-10-10 — Java daily job

The daily job is Java. Each source has 120 seconds. Process runs only when at least one source returned notices. The job then schema-checks `jobs.json` and `exam_series.json`, sends the new-job alert, and copies `jobs.json` to `jobs.prev.json`.

- A day with no notices exits 0 and leaves `jobs.json` unread. A schema failure or an alert transport failure exits 1 and does not snapshot. A disabled alert, missing SMTP settings, a missing previous snapshot, and a day with no new open jobs still snapshot.
- `PUBLIC_SITE_URL` defaults to `http://localhost:8090`.
- HTML calendar rows use `calendar-v1` and keep the listing page as `officialUrl`. SBI advertisement blocks are parsed before generic links. An empty PSC or board list fails with the paste-URL message.
- Raw HTML is archived under `data/raw/<sourceId>/<runId>/`. PDF bytes and text are cached under `data/raw/pdfs/`. Document-like links are probed before they are treated as PDFs. Each source may download 10 PDFs. `PDF_MAX_PER_RUN` defaults to 200, and the scheduled workflow sets 25.
- `data/processed/collect-progress.json` is written during the run. The desk still reads `collect-report.json`.
- `.github/workflows/daily-collect.yml` runs this job on Temurin 21. The GitHub Pages deploy workflow is removed. The React client, the Express server, and the Node collectors are removed.

This cutover did not run a live crawl and does not claim the published counts match an older Node run.
