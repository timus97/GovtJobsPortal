# Crawl / collector review

## Summary

The live crawl (`PDF_MAX_PER_RUN=40 COLLECT_HEADLESS=true node scripts/collect/runDaily.js`, `runId` `2026-08-21T14-15-19-049Z`) planned **240** scrape sources, started **67**, finished **66**, and **died on source 67 `psu_hcl`** (Hindustan Copper) with an uncaught undici `AssertionError: assert(!this.paused)` on `TLSSocket` end. That error is outside the per-source `try/catch`, so **`writeCollectReport` never ran** — `data/processed/collect-report.json` is still the **2026-07-11 `rebuilt-from-staging`** file. Staging from this run: **66 files, 702 records** (43 sources with rows, **23 zeros**, **0 `FAILED:` lines**). `pdf-parse` flooded the log with **4846** "Ran out of space in font private use area" plus **10** "TT: undefined function". After `buildJobs.js`: `inputStaging 711`, **published 544**, **quarantine 24**, **deduped 163**, **keptPublished 0**. Quality filters still let chrome through: **442 / 544** have no `lastDate` (all `open`), **409 PDF / 135 HTML**, **0 / 544** fail `isGarbageJob`, and published titles still include IOCL `Skip to main content` / `हिंदी`, NALCO `Ingots`, AAI `Updated On …` (48), and the NCS employer-registration PDF.

## Issue list

### Issue 1 -- Severity: bug
- Area: `scripts/collect/lib/http.js` + `scripts/collect/runDaily.js` (uncaught undici crash)
- Evidence: Crawl log ends at `→ psu_hcl (Hindustan Copper (HCL) (HCL) Careers)` then:
  `AssertionError [ERR_ASSERTION]: assert(!this.paused)` at `undici/lib/dispatcher/client-h1.js:367` / `TLSSocket.onHttpSocketEnd`. Node v24.18.0. **0 `FAILED:` lines** before death — the per-source `try/catch` never saw it. `psu_hcl` has **8 `listUrls`**. `fetchText` / `fetchBuffer` abort via `AbortController` after 20s/30s.
- Why it is a problem: An HTTP/2–HTTP/1 socket-end race in undici kills the **whole process**. 173 of 240 sources never ran (no P1 boards/PSCs, no later PSUs). Staging exists; there is no collect-report and no resume.
- Fix: Isolate each source in a child process **or** attach `process.on('uncaughtException')` that records the current `sourceId` and continues. Wrap `fetch` so abort/timeout cannot throw on the socket later. On abort, write a partial collect-report (`sourcesAttempted` so far + `abortedAt` + `abortedSourceId`) before exit. Retry `psu_hcl` with fewer `listUrls`.

### Issue 2 -- Severity: bug
- Area: `scripts/collect/runDaily.js` (`writeCollectReport` only after the full loop)
- Evidence: `collect-report.json` is still `runId: rebuilt-from-staging`, `startedAt: 2026-07-11`, `pdfsProcessed: null`. This run wrote 66 staging files and 702 rows but no report. `main().catch` only handles the async `main()` rejection, not undici's socket assertion.
- Why it is a problem: Ops cannot see which 66 sources ran, which 23 were zero, Playwright vs HTTP, or PDF budget use. A rebuilt-from-staging report also historically drops `errors` / method metrics.
- Fix: Write/update `collect-report.json` after **every** source (or on `finally` / signal / uncaughtException). Never leave the July rebuilt file as the only report after a live run.

### Issue 3 -- Severity: bug
- Area: `scripts/collect/lib/jobLinkQuality.js` (`NOTICE_HREF_RE`, `scoreJobLink`, `isGarbageJob`)
- Evidence: Every PDF still gets `notice_href` (+2) because `NOTICE_HREF_RE` includes `\.pdf`, plus `document` (+2) → score 4. `opening` matches listing/return URLs. **0 / 544** published jobs fail `isGarbageJob`. Still keepable and **published**:
  - `Ingots` → `https://nalcoindia.com/wp-content/uploads/2019/04/INGOT-Specification.pdf` (`psu_nalco`, 50 product-spec PDFs)
  - `Skip to main content` → `https://iocl.com/latest-job-opening`
  - `हिंदी` → `https://iocl.com/Language?ReturnUrl=%2Flatest-job-opening&handler=Return`
  - 48× `Updated On …` (`psu_aai`)
- Why it is a problem: Filters exist but do not drop product specs, brochures, language switchers, or "Know More" CSR tiles. Login/Tender **exact** titles were 0 this run — that part of the regex works; the PDF/`opening` bonus is the leak.
- Fix: Remove `\.pdf` from `NOTICE_HREF_RE`. Require `NOTICE_TEXT_RE` on the **title** (or parsed PDF text) before `document` can make a link keepable. Stop treating `opening` / `circular` path fragments as sufficient.

### Issue 4 -- Severity: bug
- Area: `scripts/collect/lib/collectNotices.js` + `scripts/collect/lib/pdfExtract.js`
- Evidence: This run used `PDF_MAX_PER_RUN=40`. Staging: **540 PDF / 162 HTML**, but only **19 `pdf-v1` vs 674 `scrape-v1`**. Published: **16 `pdf-v1` / 516 `scrape-v1`**. NALCO ingots and IOCL brochures are still `scrape-v1` with `lastDate: null` — `extractPdf` failed or hit the cap, then `recordFromNoticeLink` wrote the bare `<a>`.
- Why it is a problem: `isRecruitmentPdfText` only runs after a successful parse. Unparsed PDFs become jobs; later real ads are never opened.
- Fix: If `tryPdf` is true and `extractPdf` returns `ok: false` (`skipped` or parse failure), **do not** emit a record. Log `pdf_skipped` / `pdf_parse_failed`. Only publish a PDF after `isJobNotice === true`.

### Issue 5 -- Severity: bug
- Area: `scripts/collect/runDaily.js` (`PDF_MAX_PER_RUN`) + `pdfExtract.extractPdf` counter
- Evidence: Cap was raised to **40** for this run (default is still 25). One global counter for 240 sources. 540 PDF links in this-run staging vs 19 parsed. Counter only increments when a new file is written. July report still has `pdfsProcessed: null`.
- Why it is a problem: After ~40 new downloads the rest of the crawl cannot read last dates or reject product/tender PDFs. GRSE 50-notice pages stay dateless (`48 / 50` no lastDate) even though they are real ads.
- Fix: Per-source cap (3–8) plus a higher global cap (200–400). Increment on each attempted download. Surface `pdfsProcessed` / `pdfsSkipped` in collect-report.

### Issue 6 -- Severity: bug
- Area: `scripts/process/buildJobs.js` (`enrichRecord` needsReview clear) + `scripts/collect/lib/toStaging.js`
- Evidence: `toStaging` defaults unknown selection to `interview_only` and sets `needsReview` when there is no lastDate. `buildJobs` then **clears** review for any scrape row whose code is already in `SELECTION_PROCESSES`. After this process run:
  - **442 / 544** missing `lastDate`, all `status: "open"`
  - **501 / 544** `interview_only`; only **16** `hasExam: true`
  - `stats.json`: `open: 443`, `closed: 99`
- Why it is a problem: Nav chrome and product PDFs publish as open no-exam vacancies. Users see a fake ~443-open catalog.
- Fix: Do not treat the `interview_only` fallback as a classified selection. Quarantine unless title/PDF matched a selection pattern **and** a lastDate/walk-in date was extracted. Never publish scrape rows with neither date.

### Issue 7 -- Severity: bug
- Area: `scripts/collect/lib/jobLinkQuality.js` (`GARBAGE_TITLE_RE` is `^…$`)
- Evidence: Exact-only titles. This catalog still has **39** click/skip/know/discover titles and **48** `Updated On …`. Staging this run also has 11× OIL `Know More`, IOCL `हिंदी`, BECIL/BDL `Circulars`. Exact `Login` / `Tender` titles did **not** publish (0) — substring chrome did.
- Why it is a problem: Real PSU markup uses "Click here to …", "Know More", "Skip to main content", Hindi chrome.
- Fix: Match as substring / word. Add `skip to`, `know more`, `discover more`, `click here`, `apply online`, `हिंदी`, `circulars`, `brochure`, `handbook`, `updated on`. Keep apply-portal hrefs as `applicationUrl` on a parent notice, not as their own job.

### Issue 8 -- Severity: bug
- Area: `scripts/collect/collectors/becil.js`
- Evidence: Still not using `filterJobLinks` / `recordsFromNoticeLinks`. Published BECIL (3), all junk: `Circulars`, `Awareness on PIDPI`, `Public Awareness Notice`.
- Why it is a problem: P0 staffing source; real contract ads missed.
- Fix: Route BECIL through `recordsFromNoticeLinks`. Point `listUrls` at the careers page, not `https://www.becil.com/`.

### Issue 9 -- Severity: bug
- Area: `scripts/collect/collectors/genericBoard.js`
- Evidence: Line 87 still calls `dedupeByUrl(records)` with **no import**. Confirmed 2026-08-21. 7 enabled sources: `rbi_opportunities`, `nabard_careers`, `sebi_vacancies`, `india_post`, `fci_recruitment`, `lic_careers`, `epfo_recruitment`. This crawl **died at HCL before any genericBoard source**.
- Why it is a problem: When a later run reaches the first board source it will throw `ReferenceError: dedupeByUrl is not defined` (that one **would** be inside try/catch and count as `FAILED:`), writing 0 RBI/SEBI/India Post/FCI/LIC/EPFO notices.
- Fix: `const { dedupeByUrl } = require('../lib/toStaging');`. Add a no-network fixture test that loads `collectGenericBoard`.

### Issue 10 -- Severity: bug
- Area: `scripts/collect/collectors/ncs.js` + Playwright vs HTTP fallback
- Evidence: Only `http-fallback.html` exists (no `playwright-*.html`). Fallback is the Angular `NcsNewWebsite` shell. Published NCS is now **1 row**: `Indian Staffing Federation (ISF Registration Flow)` (`pdf-v1`, employer login-flow PDF). Old NCS handbooks dropped because `keptPublished` was 0 this run.
- Why it is a problem: Playwright failure is silent. HTTP on an SPA cannot see the government-job list. `isRecruitmentPdfText` accepted an employer manual, and that is now the entire NCS catalog.
- Fix: If Playwright throws or kept links == 0, mark the source failed / zero-with-error; do **not** HTTP-scrape the marketing homepage. Reject `handbook|flowchart|user registration|employer portal|login flow`.

### Issue 11 -- Severity: bug
- Area: `scripts/collect/collectors/employmentNews.js` + `calendarPdf.officialUrlForRow`
- Evidence: 2 published highlights still point at `Home.aspx#slug` (`CONSULTANT (LEGAL)`, `LEAD CONSULTANT (POLICY PLANNING) & OTHERS`). Quarantine has **14** `needs_review` EN rows — the same 7 titles twice, because `loadStagingRecords` ingested **both** `2026-08-20` and `2026-08-21` highlight files.
- Why it is a problem: `officialUrl` is not a specific posting. Duplicate staging files double-quarantine the same rows.
- Fix: If the highlights cell has no official href, do not publish. Stop calling `officialUrlForRow` for Employment News. Load only the newest staging file per source (Issue 19).

### Issue 12 -- Severity: bug
- Area: `data/seed/jobs.json` + `isGarbageJob` seed exception
- Evidence: Still **5 published `seed_manual` homepage jobs**: SSC CGL fixture → `https://ssc.gov.in/`, ESIC, ICAR, NHSRC, Coal India homepages. `pr21` still asserts a seed homepage is not garbage. 10 of 24 quarantine rows are other seeds with homepage/index URLs (`becil`, `ncs_gov`, `powergrid_careers`, `bel_careers`, `bhel_careers`, `hal_careers`).
- Why it is a problem: `sourceId === 'seed_manual' && lastDate && score >= -6` bypasses the new filter.
- Fix: Remove the seed homepage bypass. Point remaining seeds at a real advertisement URL or drop them. Flip the pr21 assertion.

### Issue 13 -- Severity: bug
- Area: `scripts/collect/lib/htmlLinks.js` + `genericCareers.js` (limit 50, early-stop after 3)
- Evidence: This-run staging hit the cap with chrome: `psu_aai` 50, `psu_grse` 50, `psu_grse_2` 50, `psu_iocl` 50, `psu_nalco` 50. IOCL first rows are skip-to / हिंदी / brochures. NALCO first 8 are metal specs. Extra `listUrls` are skipped once `found.length >= 3`.
- Why it is a problem: Real ads further down the page (or on `/recruitment`) are never collected. PDF budget is wasted on the first product files.
- Fix: Filter **before** the slice. Do not count skip-to / language / brochure / product-spec toward the 3-link early-stop. Prefer `listUrls` whose path contains `career|recruit|vacanc` over the org homepage.

### Issue 14 -- Severity: bug
- Area: AAI path + `findLastDateHint` any-date fallback
- Evidence: **50** published AAI jobs; **48** titled `Updated On DD-MM-YYYY` with that stamp stored as `lastDate` (e.g. `Updated On 20-12-2023` → `2023-12-20` on `/recruitment/release/396317`, also `/press-note/` and `/syllabus/`). Only 2 AAI rows lack a date (`Corporate Plan`, `Dashboard on Backlog Vacancies`).
- Why it is a problem: Status is the page-updated date, not apply-by. Cards have no post name.
- Fix: Drop `/^updated on /i` titles. Only accept dates next to `last date` / `apply by` / `closing date` — remove the "any date in text" fallback.

### Issue 15 -- Severity: bug
- Area: `shared/jobSchema.js` `computeStatus`
- Evidence: `if (!lastDate) return 'open'`. **442** dateless published rows are all `open`. `stats.json` `open: 443`.
- Why it is a problem: Undated junk ranks above real closing-soon ads (`sortPublished` uses `'9999'`).
- Fix: Do not publish dateless scrape rows (Issue 6). If they must exist, do not call them `open`.

### Issue 16 -- Severity: bug
- Area: `isRecruitmentPdfText` (too loose) + BECIL/NCS `pdf-v1` rows
- Evidence: Any `NOTICE_TEXT_RE` hit in the first 5000 chars keeps the PDF. Published `pdf-v1` includes NCS `ISF Registration Flow` (employer login manual) and BECIL PIDPI / public-awareness PDFs. Log also parsed CBT-result and travel-reimbursement PDFs earlier in the run.
- Why it is a problem: One incidental keyword (`notification`, `engagement`, `apply`) keeps manuals and forms.
- Fix: Require a recruitment heading (`advertisement for`, `applications are invited`, `walk-in`, `engagement of`, `no. of posts`) **and** reject `result of`, `travel reimbursement`, `registration flow`, `user manual`, `specification`, `brochure`, `integrity pact`.

### Issue 17 -- Severity: bug
- Area: `data/sources/registry.json` acronym collisions (PSU catalog importer)
- Evidence: Cotton Corporation (`psu_ccil`) and EdCIL (`psu_cil_2`) both have `baseUrl` / `listUrls` = **Coal India** (`https://www.coalindia.in/…`). This run: `psu_ccil` wrote 33 and **published 33** Coal India pages labeled `organization: Cotton Corporation of India Limited (CCIL) (CCIL)` (hosts `www.coalindia.in`, `d3u7ubx0okog7j.cloudfront.net`). `psu_cil_2` wrote 33 then de-duped to 0. Same pattern as `psu_grse` / `psu_grse_2` / `psu_grse_3` sharing `https://grse.in/career`.
- Why it is a problem: Wrong org on 33 public cards. Duplicate sources waste the 50-link and PDF budgets (GRSE clone wrote another 50, then `deduped: 163` collapsed them).
- Fix: Disable `_2` / `_3` clones. Fix the importer so `CIL` / `CCIL` / `EdCIL` / `GRSE` map to the correct host. Do not copy another PSU's `listUrls` because the acronym collided.

### Issue 18 -- Severity: suggestion
- Area: keep-published in `buildJobs.js` (latent this run)
- Evidence: This process run had **`keptPublished: 0`** because incoming published (544) **>** previous (433). The 421-row keep from the earlier review did **not** apply. The current 544 rows **are** the new staging (plus 20 seeds), not leftover July cards. The keep-published branch is still in code and will re-glue garbage on any future smaller crawl.
- Why it is a problem: A cleaner later run that writes fewer rows will resurrect this 544-row junk set unless `isGarbageJob` is tightened or `REPLACE_PUBLISHED=1`.
- Fix: After tightening `isGarbageJob`, process with `REPLACE_PUBLISHED=1`. Restrict keep-published to dated, keepable titles from sources that wrote 0 this run.

### Issue 19 -- Severity: suggestion
- Area: `buildJobs.js` `loadStagingRecords`
- Evidence: Walker reads **every** `data/staging/**/*.json`. Process `inputStaging: 711` vs this-run files **702** — the extra 9 are `employment_news/2026-08-20T14-56-01-506Z.json`. That is why quarantine lists the same 7 EN titles twice (14 `needs_review`).
- Why it is a problem: Yesterday's highlights are re-ingested every pipeline run.
- Fix: Load only the newest file per `sourceId` (or only the latest `runId`). Ignore `.gitkeep`.

### Issue 20 -- Severity: suggestion
- Area: Playwright coverage vs HTTP zeros
- Evidence: 241 enabled sources; **4** browser. This run: **23 / 66** wrote 0 (`apprenticeship_india`, `psu_bhel`, `psu_ntpc`, `psu_gail`, `psu_hal`, `psu_bel`, `psu_rites`, `psu_pgcil`, `psu_nmdc`, `psu_nlc`, `psu_irctc`, `psu_irfc`, `psu_pfc`, `psu_rec`, `psu_rinl`, `psu_rvnl`, `psu_cwc`, `psu_concor`, `psu_antrix`, `psu_balmer_lawrie`, `psu_ccl`, `psu_gsl`, `psu_rcf`). Log showed `warnings: 5` on several zeros (HTTP errors / no parse), but `ok: true` / `wrote 0` / no `FAILED:`. 173 sources never started because of Issue 1. July rebuilt report still shows 157/199 zero.
- Why it is a problem: HTTP on BHEL/NTPC/NCS-style apps cannot see the vacancy table; those boards stay empty while IOCL homepage chrome fills staging.
- Fix: Auto-upgrade to Playwright after 0 keepable HTTP links for known SPA hosts. Record `errors: [{message: 'no keepable notices'}]` so a zero is not silent-green.

### Issue 21 -- Severity: suggestion
- Area: `tests/pr21-collector-quality.js`
- Evidence: Tests still allow a seed homepage and do not cover: NALCO product PDFs, IOCL skip-to / हिंदी, AAI `Updated On`, NIT_ filenames, genericBoard `dedupeByUrl`, PDF_MAX skip-still-publishes, buildJobs needsReview clearing, or an undici/abort isolation test.
- Why it is a problem: Issues 3–17 will regress without fixtures.
- Fix: Add those exact URLs as `isKeepableJobLink === false`. Assert `recordsFromNoticeLink` skips `{ok:false, skipped:true}`.

### Issue 22 -- Severity: suggestion
- Area: published non-posting officialUrls
- Evidence: 5 seed homepages + 3 seed careers-index URLs still published (`sail.co.in/en/careers`, `ntpc.co.in/careers`, ONGC `/en/career/`). Also ONGC `Recruitment Policy` / `Recruitment Notices` (live staging). MIDHANI published brochures / postal ballot / TDS guidelines. `isIndexOnlyUrl` only matches a path that *ends* with `/careers|/jobs|/recruitment`.
- Why it is a problem: Listing and policy pages pass as specific postings.
- Fix: Treat careers-index, policy, brochure, MoU, BRSR, PIDPI, vendor-list, and citizen-charter as garbage unless the title is a specific post **and** a parsed recruitment PDF backs it.

### Issue 23 -- Severity: suggestion
- Area: near-duplicate titles / GRSE variants
- Evidence: Process **deduped 163** (URL+title), so `psu_grse_2` 50 staging rows published as 0 extra. Remaining GRSE cards are still detailed + EN abridged + HI abridged + `APPLY ONLINE (URL)` for the same advt (`psu_grse` 50, 48 dateless). OIL `Know More` tiles remain separate URLs.
- Why it is a problem: One notification becomes 4+ cards.
- Fix: Parse `notification no` / `advt. no` and keep the detailed EN PDF only.

### Issue 24 -- Severity: nit
- Area: `pdf-parse` log flood
- Evidence: **4846** `Ran out of space in font private use area` + **10** `TT: undefined function` in the crawl log (259 KB, almost all font noise). Happens while parsing Hindi/complex PDFs under the 40-PDF budget.
- Why it is a problem: Real warnings (`warnings: N`, HTTP errors) are unreadable. Does not crash the process (HCL undici did), but hides collector errors.
- Fix: Mute pdf-parse / fontkit stdout (or parse in a worker with stderr filtered). Keep a single per-PDF `pdf_font_warnings` metric instead of thousands of lines.

### Issue 25 -- Severity: nit
- Area: scrape title chrome / registry display names
- Evidence: GRSE titles embed `( | PDF | 1.1 KB | English)`. HUDCO titles embed `(पीडीएफ साइज़: …)`. Registry names still doubled: `Hindustan Copper (HCL) (HCL) Careers`, `Cotton Corporation of India Limited (CCIL) (CCIL)`.
- Why it is a problem: Ugly cards; harder de-dupe.
- Fix: Strip size/language suffixes in `toStagingRecord`. Stop appending `(ORG)` twice in the PSU catalog importer.

## This run vs prior review

- **Disproved as the source of the current 544-row catalog:** keep-published leftovers (Issue 18). `keptPublished` was 0; the junk is freshly staged.
- **Not seen in this published set:** exact `Login` / `Tender` titles (0). `TENDER_RE` still misses `NIT_` filenames; this 50-cap just did not pick the old SECI NIT PDFs.
- **Still unproven in production:** genericBoard `dedupeByUrl` crash — crawl died earlier at HCL.
- **New this run:** undici process kill, no collect-report, 23/66 zeros, 4846 font warnings, CCIL→Coal India registry collision, process `544 / 24 / 163`.
