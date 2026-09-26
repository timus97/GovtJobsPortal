# Java 21 rewrite

In-place rewrite of this repo from Node/Express/React to **Java 21 + Spring Boot 3.4 + Thymeleaf**. Product behaviour stays the same. Node is deleted only in slice 10.

Locked choices:

- Full Java collectors (Jsoup, Playwright Java, PDFBox) **after** the UI is complete. No Node sidecar at cutover. Until then, daily collect stays Node.
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
4. Student accounts + desk — **Postgres + signed cookies** (`student_session` / `ops_session`); forgot/reset with dev mail link; files on disk
5. Unofficial coaching (plan + mocks) — **out of scope** (2026-09-26). Four packs remain on disk; do not extend them.
6. Ops paste-URL / review / sources — **partial** (local publish; no source edit / daily collect / GitHub ingest). UI work may touch this. Publish still writes `jobs.json` only.
7. Process pipeline in Java — `collect process` only checks that the JSON files exist. Real `BuildJobs` is still Node, and waits until after the UI.
8. HTTP collectors — still Node. Port after the UI is complete.
9. Playwright + PDF collectors + GHA — still Node. Port with slice 8.
10. Cutover and hosting — not started. Do not delete Node, and do not treat GitHub Pages as the live site.

See the session plan for file-level mapping. Do not change match rules, reservation-required, or invent a catalog database.
