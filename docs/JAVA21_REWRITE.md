# Java 21 rewrite

In-place rewrite of this repo from Node/Express/React to **Java 21 + Spring Boot 4.1.1 + Thymeleaf**. Product behaviour stays the same. The React client, the Express server, and the Node collectors were removed on 2026-10-10.

Locked choices:

- Full Java collectors (Jsoup, Playwright Java, PDFBox). The daily job is `collect` `daily`. No Node sidecar.
- HTML + existing CSS. Tiny JS only where a page needs it (ops collect-progress poll).
- Catalog stays git JSON. Students are Postgres. Coaching (syllabus, plan, mocks) is **out of scope**.
- GitHub Pages is not a deploy target. Host the Java app when the UI is complete.
- Port **8080** (`PORT` override). Cookie names stay `student_session` / `ops_session`.
- Session cookies are HttpOnly + SameSite=Lax. Default `COOKIE_SECURE=false` so local HTTP works; set `COOKIE_SECURE=true` in production HTTPS.

## Layout

| Module | Role |
| --- | --- |
| `domain/` | Pure Java: schemas, match, desk rules |
| `collect/` | Daily collect + `BuildJobs` CLI |
| `web/` | Spring Boot + Thymeleaf product host |

```powershell
# Temurin 21 path lives in .grok/java-home.txt (default JAVA_HOME on this machine is JDK 17).
. .\scripts\java21.ps1
.\mvnw.cmd test
.\scripts\start-web.ps1
# http://localhost:8090   GET /health
```

## Slices

1. Skeleton — Maven, health, public landing HTML
2. Domain + catalog pages (`/jobs`, `/prepare`) — done
3. Match + profile — done
4. Student accounts + desk — **done.** Postgres + signed cookies (`student_session` / `ops_session`). Forgot/reset shows the one-time link only on loopback when no mailer is set. Files stay on disk.
5. Unofficial coaching (plan + mocks) — **out of scope** (2026-09-26). Four packs remain on disk; do not extend them. The routes still answer and are not linked.
6. Ops paste-URL / review / sources — **partial, and the daily fetch is in.** Local publish and `/ops/fetch` run on this host. The desk daily command is the Java daily job. There is still no source editor, and the host does not start git ingest. Publish still writes `jobs.json` only.
7. Process pipeline in Java — `collect process` rebuilds jobs, exam series, and quarantine JSON. Done 2026-10-10.
8. HTTP collectors — Java `SourceCollect`. Done 2026-10-10.
9. Playwright + PDF collectors + the daily workflow — Java, on Temurin 21. Done 2026-10-10.
10. Collector cutover is done. Hosting is not started. Do not treat GitHub Pages as the live site. Do not add the Node tree back.

See the session plan for file-level mapping. Do not change match rules, reservation-required, or invent a catalog database.
