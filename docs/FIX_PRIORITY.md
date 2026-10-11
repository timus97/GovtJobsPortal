# Fix priority

Audit date: 2026-09-30. Rechecked 2026-10-10 on `phase-a-one-catalog` (HEAD `f960ace`, plus the uncommitted desk, collector, and cutover work). Product host: Java 21 + Spring Boot 4.1.1 on port 8090. Catalog students read: Postgres `catalog`, copied from git JSON at boot.

Owner order still holds: fix the desk UI first, then hosting. Coaching stays out of scope. Collectors and the daily job are Java as of 2026-10-10.

This file is the working list. Checked items are done in the tree. The rest are still open.

Since 30 Sep, the Java registry collector and `/ops/fetch` are committed (`9d20647`). `V5` corrects the Chandigarh RRB priority link. The published catalog has no `http://` official URL. Student password changes already bump `session_epoch`. Items 1–7, 12, and 23–25 are done. Item 11 is only the RRB homepage rewrite. Item 22 stays leave. Items 8–10 and 13–21 are still open.

## P0 — broken or unsafe on the desk

| # | Item | State |
| --- | --- | --- |
| 1 | Admin can run the Java registry collector from the desk: all enabled sources, one source, or rebuild. A successful rebuild refreshes Search, Match, and Prepare without a restart. | Done. `/ops/fetch`, committed in `9d20647`. The desk daily command runs the Java daily job. |
| 2 | `/ops/sources` returns 500 because the template reads `s.collector` on a map. | Done. Map keys use `s['collector']`. Sources is in the admin nav. |
| 3 | Desk validation is silent. An `http://` custom exam and a non-image admit upload are rejected, then the flash message is dropped because the desk templates never render `notice`. | Done. Dashboard and desk detail render `notice`. A rejected custom exam, track, or save stays on that page with the validation message. |
| 4 | Signed-out Search and Prepare use the student header, so Matches, Profile, Desk, and Sign out show with no session. The public landing header only links to Admin. | Done. Matches, Profile, and Desk render only when a student is signed in and the student feature is on. Sign out follows the session. Signed-out Search and Prepare link to Search, Prepare, and Sign in. The public header links to Jobs, Prepare, Sign in, and Admin. The feature flag does not mean authenticated, and route checks are unchanged. |
| 5 | Ops “Publish locally” ignores edits on the same form. Facts are stored only when the action is `save`. A blank last date is then published as open. | Done. Publish locally validates the submitted title, organisation, URL, summary, selection, has-exam value, and last date, then publishes that row. Catalog status has no undated value, and `JobSchema.computeStatus` would label a blank or unparseable date as open, so publish refuses it with “Last date must be YYYY-MM-DD before this can be published. A missing date is not published as open.” It does not invent a date, change the queue item, or write the catalog. Save, reject, and unpublish do not use those publish rules. |
| 6 | Password reset prints the one-time URL whenever `COOKIE_SECURE` is false and no mailer is set. Nothing checks that the caller is on loopback, and `/health` reports `mail=dev`. | Done. The forgot page adds the one-time URL only when no mailer is configured, `COOKIE_SECURE` is false, and the socket address is a loopback literal (`127.0.0.0/8`, `::1`, or an IPv4-mapped loopback). `X-Forwarded-For` is ignored, and the URL is not logged. A non-loopback `/health` reports `mail=unconfigured`. Loopback `/health` still reports `mail=dev` when no mailer is set. |
| 7 | Student Postgres listens on `0.0.0.0:5432` with user and password `govtjobs`. | Done. `docker-compose.yml` publishes `127.0.0.1:5432:5432`. The dev role and password are unchanged. |

## P1 — catalog honesty and the publish path

| # | Item | State |
| --- | --- | --- |
| 8 | After the 30 Sep 2026 daily fetch and a rebuild, Search shows three rows that are not closed: the SSC CGL fixture (last date 30 Sep 2026), an HLL walk-in on 5 Oct 2026, and an ESIC walk-in on 13 Oct 2026. Result notices, cancellation notices, “View Advertisement”, call letters, and scrapes older than 18 months are quarantined. Most of the 27 published rows are still closed seeds. | Open. The committed catalog is still that 30 Sep rebuild: 27 jobs, 24 closed, 1 open, 2 closing soon. As of 8 Oct 2026 the SSC and HLL dates are past, and `jobs.json` has not been recomputed. The ESIC walk-in on 13 Oct 2026 is the remaining date still ahead. `collect-report.json` in git is the 21 Aug 2026 short run. The 30 Sep Java run was cancelled before it wrote sources, and that empty file was not committed. Real vacancies stay thin until list URLs and PDF dates improve. |
| 9 | Priority-link approval sets `collect.notices.review_status` and leaves `catalog_id` null. The pipeline copy now says that. Students still never see an approved crawl notice. | Open. Rechecked: approve still runs `UPDATE collect.notices SET review_status = 'approved', catalog_id = NULL`. |
| 10 | The priority crawler stores one page title per link, keeps a row only when it finds an ISO date, and does not follow notice links or PDFs. | Open. Rechecked: `CrawlRunner` still reads `<title>` and the first `YYYY-MM-DD` in the page. |
| 11 | Flyway `V4` still seeds four homepages, including `rrbcdg.gov.in`. `V5` only rewrites that host to the RRB apply homepage. This database’s priority list is ahead of the migrations. | Partly done. `V5` (`a83c605`) rewrites `https://www.rrbcdg.gov.in/` to `https://www.rrbapply.gov.in/` and deletes the old row, so a migrate is no longer behind on that host. `V4` still inserts the SSC, UPSC, and IBPS homepages, and `V5` does not replace those with list URLs. |
| 12 | Java calendar rows were stamped `qualification=graduate` even when the notice stated no education floor. | Done. Committed in `9d20647`. The floor is left blank. A daily run keeps every pasted row, reads dates from the table row (`14-Oct-2026`), follows a `www` redirect and a redirect on the same registrable site, refuses a bot-manager interstitial, reads notice PDFs up to 4 MB, fetches browser boards with Playwright and falls back to HTML, and runs up to four sources at once. Result, cancellation, call-letter, and index titles are dropped. A calendar title such as Tentative Vacancy is not turned into an exam series. A scraped application date older than 18 months is quarantined as `stale_scrape`. Curated rows are left alone. Later collector behaviour is in `docs/collector-fix-log.md`. |
| 13 | Paste has no `pending → extracted → valid` states, no git ingest, and no source editor. `ops-ingest.yml` exists and the host never starts it. | Open |
| 14 | Several published official URLs are outside the paste allowlist. One BECIL link is `http://` and is correctly not rendered. The CGL fixture points at `https://ssc.gov.in/apply/cgl-2026`. | Open. The published `jobs.json` has no `http://` official URL. The August `collect-report.json` still records `http://www.becil.com/Vacancies`, and that link is not a published row. Many published PSU hosts are outside `.gov.in`, `.nic.in`, and `extra-hosts` (HLL, HUDCO, BHEL, IOCL, SAIL, NTPC, and others). `becil.com` and `ongcindia.com` are on the extra list. The CGL fixture is still `https://ssc.gov.in/apply/cgl-2026`. |
| 15 | Match lists every closed row under “Did not match”. Search hides them unless a status is chosen. | Open. Behavior is explainable; the closed pile is the problem. `EligibilityMatch` still fails a `closed` status, and `match.html` still renders that bucket. |

## P2 — hardening, leftovers, docs

| # | Item | State |
| --- | --- | --- |
| 16 | Logout clears the browser cookie only. A copied student cookie works until the password changes. Operator cookies have no epoch. | Open. A student password change increments `session_epoch` (`V3`) and `SignedAuthFilter` rejects a cookie whose epoch does not match. Logout still only clears the browser cookie, so a copied student cookie works until the password changes. `signOps` still uses epoch 0, and operators have no stored epoch. |
| 17 | Paste and crawl check DNS, then the HTTP client resolves the name again. `0.0.0.0/8` is only partly blocked. Redirects stay off. | Open. `OfficialUrlPolicy.isBlockedAddress` blocks the any-local address, loopback, link-local, site-local, and carrier-grade NAT. It does not block the rest of `0.0.0.0/8`. The Java fetch client follows up to three same-site redirects and refuses a bot-manager interstitial (item 12). Paste and the priority crawl still resolve the name twice. |
| 18 | Admit and result files check magic bytes and size. The owner-only ACL is best-effort. | Done 2026-10-11. A failed owner-only mode logs `desk.file_acl_failed` without the file bytes or a student email. The save does not describe that file as owner-only. Magic-byte and path checks stay. |
| 19 | Session cookies are not `Secure` unless `COOKIE_SECURE=true`. | Open. Required before HTTPS hosting. |
| 20 | A tracked opportunity is badged “Job applied” while its status is still Watching. The status menu shows raw codes. | Done 2026-10-11. Kind is Job, Calendar, or Custom. The status menu reads Watching, Applied, Admit ready, Appeared, Result in, and Done. |
| 21 | The paste desk still prints the August `collect-progress.json` blob. The Java fetch desk reads `collect-report.json` instead. | Open. `ops/dashboard.html` still polls `/ops/collect-progress`, and its copy still names the Node pipeline. Java collect now writes `collect-progress.json` during a run, and `/ops/fetch` prints each source `outcome` from `collect-report.json`. The committed `collect-report.json` is still the 21 Aug 2026 short run. |
| 22 | `/desk/{id}/plan` and `/desk/{id}/mock` still answer for the four coaching packs. The desk does not link to them. Do not extend them. | Leave. Rechecked: `desk/detail.html` has no plan or mock link. |
| 23 | `.github/workflows/daily-collect.yml` still runs the Node pipeline. `.github/workflows/deploy-pages.yml` still deploys the React client on push to `master`. | Done 2026-10-10. `daily-collect.yml` runs the Java daily job on Temurin 21. `deploy-pages.yml` is removed. `collect-java.yml` still only verifies `domain` and `collect` and does not fetch sites. |
| 24 | The old Express API accepts a student cookie as an operator when both sides share `SESSION_SECRET`, and its paste fetch follows redirects. | Done 2026-10-10. The Express server is removed, so that mix-up cannot be started. Java student and operator sessions stay separate. |
| 25 | `docs/JAVA21_REWRITE.md`, `docs/DATA_AND_STATUS.md`, and `docs/STUDENT_COACHING_DESIGN.md` disagree with the running host (Postgres catalog, Spring Boot 4.1.1, password reset, Java `BuildJobs`). | Done 2026-10-10. Those three files now record Spring Boot 4.1.1, Postgres students, the loopback password reset, and the Java daily job. Older command lists in the design history are marked as history. |

## Completed 2026-10-10 — collector and cutover

Recorded in `docs/collector-fix-log.md` and `docs/collection-runbook.md`. This did not crawl live sites and did not rewrite `jobs.json` or the August `collect-report.json`.

- A source outcome is `complete`, `partial`, `empty`, `failed`, or `not_finished`. Empty and failed runs write no snapshot and delete nothing. A partial run keeps the previous non-empty snapshot. Process reads the newest non-empty file.
- Calendar PDFs are discovered for IBPS, UPSC, SSC, and `*_calendar` sources. A parsed calendar row keeps the listing page as `officialUrl` (`calendar-v1`). The education floor stays blank. A notice PDF is kept only when its text is recruitment evidence (`pdf-v1`).
- The daily job fetches with a 120-second limit per source, rebuilds only when a source returned notices, schema-checks, alerts, and snapshots. A day with no notices leaves `jobs.json` unread.
- HTML calendar rows, SBI advertisement blocks, the PSC and board paste-URL failure, the raw HTML archive, the PDF cache, the document probe, and `collect-progress.json` are in the Java collector.
- `client/`, `server/`, `shared/`, the Node collectors, and `tests/pr*.js` are removed. Golden fixtures remain in `tests/fixtures/`. Student Postgres stays on Flyway. The compose file no longer mounts the old Node init script.

## Fetch desk

`/ops/fetch` is the admin control for the Java CLI. The page is committed in `9d20647`. The daily command now runs the Java daily job.

| Action | What it runs | What students see |
| --- | --- | --- |
| Run enabled sources | Java daily job: enabled sources, then process, schema-check, alert, and snapshot only when one source returned notices | Catalog refreshes when that rebuild runs. An empty or failed day leaves `jobs.json` unread. |
| Run this source | `collect source <id>` | A snapshot only when that source returned notices. Otherwise the previous snapshot stays. |
| Rebuild catalog | `collect process` with replace-published off, then `CatalogStore.syncFromGit()` | Search, Match, and Prepare on the next request |

One command at a time. Defence and manual rows are refused by the collector. The priority-link crawl on `/ops/pipeline` is unchanged and still does not publish. The host process that was already on port 8090 during this cutover was not restarted, so it serves the previous build until the next start.
