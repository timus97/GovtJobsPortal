# Session knowledge: All-India govt jobs + student exam desk

| Field | Value |
| --- | --- |
| Saved | 2026-08-19 (rewritten 2026-08-20 after PR10; **updated again 2026-08-20** after Stage 7 + student DB) |
| Workspace | `C:\Users\Timus97\Desktop\grokAnalysis\GovtJobsPortal` |
| Repo | https://github.com/timus97/GovtJobsPortal |
| Default branch | `master` (`2f5d19a` — configurable json/postgres student store) |
| Status | **Catalog PR01–PR10 complete.** **Stage 7 PR11–PR16 complete.** Student SoR is `STUDENT_STORE=json` or `postgres` (Docker). Next work is operational, not a new design-plan PR. |
| Catalog design | [docs/ALL_GOVT_JOBS_DESIGN.md](../ALL_GOVT_JOBS_DESIGN.md) |
| Desk design | [docs/STUDENT_COACHING_DESIGN.md](../STUDENT_COACHING_DESIGN.md) · [docs/STUDENT_DESK_UX.md](../STUDENT_DESK_UX.md) |

Use this article to resume a new session. Do not re-litigate locked decisions. Do not start from “no-exam only” as the product goal. Do not reopen “no student accounts” (owner reopened that in Stage 7 and it shipped). Do not open another design-plan PR unless the owner changes scope.

---

## What shipped

The product is a **fullstack all-India government-jobs + exam desk** app (React 19 + Vite 8 SPA, Express API).

### Catalog (PR01–PR10)

1. **Browse `/jobs`** — exam and no-exam. Default filter `hasExam=all`.
2. **Profile + Match** — `reservationCategory` required. Anonymous match uses a request body / `localStorage`; logged-in match uses the **server** profile. Banner: not an official eligibility decision.
3. **Prepare `/prepare`** — ExamSeries from official calendars. No Apply unless a linked Opportunity is open. **UGC NET, GATE, CTET** are prepare-for (`applyNever`). CUET excluded.
4. **Ops `/ops`** — hashed operator password + `ops_session`. Paste official URL → review → publish to `data/staging/ops_paste/`. Unpublish on (`FEATURE_UNPUBLISH=on`).

Matching is rule-based only. Never invent eligibility. Age uses notification `ageAsOnDate`. Incomplete parse → unknown, not fail. Post-wise PwBD when `posts[]` are explicit; reserved-only fails UR only with structured `reservedOnly` / `openToCategories`.

### Student exam desk (Stage 7, PR11–PR16)

5. **Accounts** — email + password (min 10), unique email, no verify/reset. Cookie `student_session` (`aud=student`), idle 14 days. Ops cookie cannot call student routes.
6. **Server profile** — same eligibility fields; one-click import from `sarkari.profile.v1` if the server profile is empty.
7. **Dashboard `/dashboard`** — track `series` \| `opportunity` \| `custom`. Statuses: watching → applied → admit_ready → appeared → result_in → done. Days-left from exam date else last/apply date else “Add exam date.” Track / I applied on Prepare and Job detail.
8. **Private files** — one admit + one result per item, PDF/JPEG/PNG ≤ 5 MB, owner-only download. Bytes on disk under `STUDENT_FILES_DIR/<studentId>/<itemId>/`.
9. **Unofficial study plan** `/desk/:id/plan` — topic packs in `data/coaching/syllabus/` (CGL, CSE, IBPS PO, UGC NET). Even-split UTC day ranges to the exam date. Ticks persist.
10. **Unofficial mocks** `/desk/:id/mock` — banks in `data/coaching/mocks/`. Timed attempt, score on submit, review. Open attempt is reused (no StrictMode double-create).
11. **Brand / hosting copy** — fullstack exam desk; Pages is a snapshot without accounts.

### Student persistence (after PR16)

`STUDENT_STORE=json` (default, `data/students/students.json`) or `STUDENT_STORE=postgres` (Docker Compose `student-db` / `STUDENT_DATABASE_URL`). If `STUDENT_DATABASE_URL` is set and `STUDENT_STORE` is unset → postgres. Admit/result **bytes** stay on disk. Catalog stays git JSON. **Never** put student tables in `data/cache/portal.sqlite`.

```bash
npm run db:up                  # docker compose student-db
npm run student:import-json    # copy existing students.json into Postgres
npm run server:pg
node tests/pr17-student-postgres.js
```

Default local URL: `postgres://govtjobs:govtjobs@127.0.0.1:5432/govtjobs_students` (change before any shared host). `GET /api/health` includes `studentStore: json|postgres`.

Postgres was implemented and documented; **`db:up` / pr17 were not run in the 2026-08-20 session** because Rancher Desktop’s engine never stayed up and the WSL daemon could not pull `postgres:16-alpine`.

---

## Owner decisions — do not reopen

**2026-08-18 (catalog)**

1. Reservation category required before match. Printed age-relaxation tables only.
2. Fullstack Express + SPA is the product. Pages is a snapshot.
3. UGC NET is prepare-for immediately.
4. Real ops auth. `OPERATOR_PASSWORD` bootstraps the first admin only.
5. `/jobs` defaults to all jobs immediately.
6. PDFs stay in private raw staging, 30-day TTL **intended**, never Pages / public JSON. *(TTL code still a follow-up.)*

**2026-08-20 (desk — reopened “no student accounts”)**

1. Email + password. No verify, no reset in v1.
2. Admit/result files private on the API host. Owner-only.
3. Dashboard: calendars + applied jobs + custom exams.
4. Full coaching now: desk + unofficial syllabus/plan + unofficial mocks (PR11–PR16).
5. Account extras: sign up, sign in, persist session only.

---

## Architecture locks (do not reopen)

- **Git JSON is the catalog store.** Optional `better-sqlite3` cache on Express is catalog-only. Never install it in the root/GHA pipeline.
- **Student SoR is configurable** (`json` \| `postgres`). Files under `STUDENT_FILES_DIR`. Never `portal.sqlite`. Never git / Pages.
- Two entities: **Opportunity** and **ExamSeries**.
- `hasExam` is a filter, not a drop. `selectionProcess` stays a **string** on `jobs.json`.
- Source catalog is **only** `data/sources/registry.json`.
- Paste-URL publish writes `data/staging/ops_paste/<stableJobId>.json`. Never `workflow_dispatch` `pipeline:daily` to save a paste.
- Daily collect does **not** create `collect_jobs` per PSU URL.
- RRB national apply is `rrbapply.gov.in` + zonals. **`rrbcdg.gov.in` is Chandigarh.**
- Defence: calendar/manual only. **No CAPTCHA bypass.**
- Employment News: free highlights table only.
- Coaching content is hand-built unofficial JSON. Never scrape official papers.
- Legal: rate-limit, metadata + official URLs only, disclaimer, no PDF republish. Never say “you are eligible.”

---

## Implementation status

### Catalog DAG (complete)

```text
01 → 02 → (03 ∥ 05 ∥ 07) → 04 → 06 → 08 → 09a → (09b ∥ 09c) → 10
 done  done   done  done  done  done  done  done   done   done  done
```

PR01–PR10 are on `master`. Feature branches `pr/01`–`pr/10` were deleted.

### Stage 7 DAG (complete)

```text
11 → 12 → (13 ∥ 14) → 15 → 16
done  done   done  done  done  done
```

| PR | Ships | Commit (approx.) |
| --- | --- | --- |
| 11 | Accounts + server profile | `b711fdb` |
| 12 | Desk tracker, days-left, Track / I applied | `e86d48a` |
| 13 | Private admit/result uploads | `96e5852` |
| 14 | Unofficial syllabus + even-split plan + ticks | `90b869e` |
| 15 | Unofficial timed mocks + review | `b1de2bc` / `692c854` / `7cb8914` |
| 16 | Docs, hosting disk, brand | `00d7d82` / `62f190b` |
| store | `STUDENT_STORE=json\|postgres` + Docker Compose | `2f5d19a` |

UX: Figma [37BnL3aZ62LmXqnJu1PnZH](https://www.figma.com/design/37BnL3aZ62LmXqnJu1PnZH) frames 00–09. Screens 10–12 + mobile are written in `STUDENT_DESK_UX.md` (Starter MCP quota). Same file when quota resets — do not start a second Figma file.

### Tests

`tests/pr11-student-account.js` … `pr15-mocks.js` (JSON tmp dirs). `tests/pr17-student-postgres.js` needs `npm run db:up`.

### Catalog snapshot (unchanged since PR10)

- **628** jobs in `data/processed/jobs.json` (563 open; 1 exam fixture). Last process still **2026-07-11**.
- **25** ExamSeries. Capacity target ~200.
- **307** registry sources, **241** enabled.
- Match p95 **~71 ms** on 10k synthetic rows.
- **22** golden fixtures in `tests/fixtures/golden-opportunities.json`.

---

## Follow-ups (not a new design PR)

1. Run a real `collect:daily` + `process` so P0/P1 boards fill `jobs.json` / `exam_series.json`.
2. PDF **30-day TTL** in raw staging / GHA.
3. Optional API polish: `GET /api/opportunities`, `includeClosed=1`, `kind=` filter.
4. Always-on host. `render.yaml` is still free-tier (sleeps). Attach a **persistent disk** for files and/or a hosted Postgres for `STUDENT_STORE=postgres`.
5. Start Rancher Desktop / Docker Desktop and run `npm run db:up` + `pr17` + `student:import-json` if you want the desk on Postgres locally.
6. Deferred collector-helper cleanup.
7. `origin/main` leftover remote branch (if still present).

---

## Resume prompt (paste into a new session)

```text
Continue GovtJobsPortal from docs/knowledge/2026-08-19-session-all-govt-jobs-expansion.md,
docs/ALL_GOVT_JOBS_DESIGN.md, and docs/STUDENT_COACHING_DESIGN.md.
Catalog PRs 01–10 and Stage 7 PRs 11–16 are complete on master (tip 2f5d19a).
Student store is STUDENT_STORE=json (default) or postgres (Docker).
Do not reopen locked decisions. Do not start another design-plan PR.
Next work is operational: live collect, PDF TTL, always-on host + disk/Postgres,
and optionally bringing Docker up to run npm run db:up + tests/pr17-student-postgres.js.
```
