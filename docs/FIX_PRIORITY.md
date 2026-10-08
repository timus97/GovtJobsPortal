# Fix priority

Audit date: 2026-09-30. Product host: Java 21 + Spring Boot on port 8090. Catalog students read: Postgres `catalog`, copied from git JSON at boot.

Owner order still holds: fix the desk UI first, then the collector publish path, then hosting. Coaching stays out of scope. Do not delete `client/`, `server/`, or `scripts/collect/` until cutover.

This file is the working list. Checked items are done in the tree. The rest are still open.

## P0 — broken or unsafe on the desk

| # | Item | State |
| --- | --- | --- |
| 1 | Admin can run the Java registry collector from the desk: all enabled sources, one source, or rebuild. A successful rebuild refreshes Search, Match, and Prepare without a restart. | Done. `/ops/fetch`. This does not start the Node `pipeline:daily` workflow. |
| 2 | `/ops/sources` returns 500 because the template reads `s.collector` on a map. | Done. Map keys use `s['collector']`. Sources is in the admin nav. |
| 3 | Desk validation is silent. An `http://` custom exam and a non-image admit upload are rejected, then the flash message is dropped because the desk templates never render `notice`. | Open |
| 4 | Signed-out Search and Prepare use the student header, so Matches, Profile, Desk, and Sign out show with no session. The public landing header only links to Admin. | Open |
| 5 | Ops “Publish locally” ignores edits on the same form. Facts are stored only when the action is `save`. A blank last date is then published as open. | Open |
| 6 | Password reset prints the one-time URL whenever `COOKIE_SECURE` is false and no mailer is set. Nothing checks that the caller is on loopback, and `/health` reports `mail=dev`. | Open. Local dev only until the app is reachable beyond this machine. |
| 7 | Student Postgres listens on `0.0.0.0:5432` with user and password `govtjobs`. | Open. Bind it to loopback before any other machine can reach this host. |

## P1 — catalog honesty and the publish path

| # | Item | State |
| --- | --- | --- |
| 8 | After the 30 Sep 2026 daily fetch and a rebuild, Search shows three rows that are not closed: the SSC CGL fixture (last date 30 Sep 2026), an HLL walk-in on 5 Oct 2026, and an ESIC walk-in on 13 Oct 2026. Result notices, cancellation notices, “View Advertisement”, call letters, and scrapes older than 18 months are quarantined. Most of the 27 published rows are still closed seeds. | Open. Real vacancies stay thin until list URLs and PDF dates improve. |
| 9 | Priority-link approval sets `collect.notices.review_status` and leaves `catalog_id` null. The pipeline copy now says that. Students still never see an approved crawl notice. | Open |
| 10 | The priority crawler stores one page title per link, keeps a row only when it finds an ISO date, and does not follow notice links or PDFs. | Open |
| 11 | Flyway `V4` still seeds four homepages, including `rrbcdg.gov.in`. `V5` only rewrites that host to the RRB apply homepage. This database’s priority list is ahead of the migrations. | Open |
| 12 | Java calendar rows were stamped `qualification=graduate` even when the notice stated no education floor. | Done. The floor is left blank. A daily run keeps every pasted row, reads dates from the table row (`14-Oct-2026`), follows a `www` redirect and a redirect on the same registrable site, refuses a bot-manager interstitial, reads notice PDFs up to 4 MB, fetches browser boards with Playwright and falls back to HTML, and runs up to four sources at once. Result, cancellation, call-letter, and index titles are dropped. A calendar title such as Tentative Vacancy is not turned into an exam series. A scraped application date older than 18 months is quarantined as `stale_scrape`. Curated rows are left alone. |
| 13 | Paste has no `pending → extracted → valid` states, no git ingest, and no source editor. `ops-ingest.yml` exists and the host never starts it. | Open |
| 14 | Several published official URLs are outside the paste allowlist. One BECIL link is `http://` and is correctly not rendered. The CGL fixture points at `https://ssc.gov.in/apply/cgl-2026`. | Open |
| 15 | Match lists every closed row under “Did not match”. Search hides them unless a status is chosen. | Open. Behavior is explainable; the closed pile is the problem. |

## P2 — hardening, leftovers, docs

| # | Item | State |
| --- | --- | --- |
| 16 | Logout clears the browser cookie only. A copied student cookie works until the password changes. Operator cookies have no epoch. | Open |
| 17 | Paste and crawl check DNS, then the HTTP client resolves the name again. `0.0.0.0/8` is only partly blocked. Redirects stay off. | Open |
| 18 | Admit and result files check magic bytes and size. The owner-only ACL is best-effort. | Open |
| 19 | Session cookies are not `Secure` unless `COOKIE_SECURE=true`. | Open. Required before HTTPS hosting. |
| 20 | A tracked opportunity is badged “Job applied” while its status is still Watching. The status menu shows raw codes. | Open |
| 21 | The paste desk still prints the August `collect-progress.json` blob. The Java fetch desk reads `collect-report.json` instead. | Open |
| 22 | `/desk/{id}/plan` and `/desk/{id}/mock` still answer for the four coaching packs. The desk does not link to them. Do not extend them. | Leave |
| 23 | `.github/workflows/daily-collect.yml` still runs the Node pipeline. `.github/workflows/deploy-pages.yml` still deploys the React client on push to `master`. | Leave until cutover. Do not press those as the product host. |
| 24 | The old Express API accepts a student cookie as an operator when both sides share `SESSION_SECRET`, and its paste fetch follows redirects. | Open if that API is started. The Java filters do not have this bug. |
| 25 | `docs/JAVA21_REWRITE.md`, `docs/DATA_AND_STATUS.md`, and `docs/STUDENT_COACHING_DESIGN.md` disagree with the running host (Postgres catalog, Spring Boot 4.1.1, password reset, Java `BuildJobs`). | Open |

## Fetch desk

`/ops/fetch` is the admin control for the Java CLI.

| Action | What it runs | What students see |
| --- | --- | --- |
| Run enabled sources | `collect daily`: every enabled, non-manual registry source, then `process` if one source succeeded | Catalog refreshes only when that rebuild runs |
| Run this source | `collect source <id>` | Staging only, until Rebuild catalog |
| Rebuild catalog | `collect process` with replace-published off, then `CatalogStore.syncFromGit()` | Search, Match, and Prepare on the next request |

One command at a time. Defence and manual rows are refused by the collector. The priority-link crawl on `/ops/pipeline` is unchanged and still does not publish.
