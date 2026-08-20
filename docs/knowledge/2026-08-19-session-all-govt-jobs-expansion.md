# Session knowledge: All-India govt jobs expansion

| Field | Value |
| --- | --- |
| Saved | 2026-08-19 (rewritten 2026-08-20 after PR10 landed) |
| Workspace | `C:\Users\Timus97\Desktop\grokAnalysis\GovtJobsPortal` |
| Repo | https://github.com/timus97/GovtJobsPortal |
| Default branch | `master` (`cecedb0` — Merge PR10) |
| Status | **Design PR plan (01–10) complete.** Stage 7 (student desk + coaching) accepted — see [STUDENT_COACHING_DESIGN.md](../STUDENT_COACHING_DESIGN.md). PR11 accounts start next. |
| Full design | [docs/ALL_GOVT_JOBS_DESIGN.md](../ALL_GOVT_JOBS_DESIGN.md) |

Use this article to resume a new session. Do not re-litigate locked decisions. Do not start from “no-exam only” as the product goal. Do not open another design-plan PR unless the owner changes scope.

---

## What shipped

The NoExam Sarkari Jobs portal is now a **fullstack all-India government-jobs product**:

1. **Browse `/jobs`** — exam and no-exam. Default filter `hasExam=all`.
2. **Profile + Match** — localStorage profile (`reservationCategory` required). `POST /api/match` scores currently-open opportunities with explainable reasons. Banner: not an official eligibility decision.
3. **Prepare `/prepare`** — ExamSeries from official calendars. No Apply unless a linked Opportunity is open. **UGC NET, GATE, CTET** are prepare-for (`applyNever`), never vacancies. CUET excluded.
4. **Ops `/ops`** — operator accounts (hashed password + session + role). Paste official URL → review → publish to `data/staging/ops_paste/`. Unpublish on (`FEATURE_UNPUBLISH=on`).

Matching is rule-based only. Never invent eligibility. Age uses notification `ageAsOnDate`. Incomplete parse → unknown, not fail. Post-wise PwBD when `posts[]` are explicit; reserved-only fails UR only with structured `reservedOnly` / `openToCategories`.

Product host is **always-on Express + SPA**. Git JSON (`data/processed/*.json`) is the durable store. SQLite is an optional Express read cache. GitHub Pages may remain a snapshot fallback.

---

## Owner decisions (2026-08-18) — do not reopen

1. **Reservation category is required** before match (UR / EWS / OBC / SC / ST). Use only printed age-relaxation tables; never invent relaxations.
2. **Fullstack Express + SPA is the product.** `FEATURE_SERVER_MATCH=on`. Pages is a snapshot, not the match/ops host.
3. **UGC NET** is a prepare-for ExamSeries immediately, not a vacancy.
4. **Real admin auth.** `OPERATOR_PASSWORD` bootstraps the first admin only.
5. **`/jobs` defaults to all jobs immediately** (no 7-day no-exam grace).
6. **PDFs** stay in private raw staging, **30-day TTL intended**, never copied to Pages / public JSON. *(TTL code is still a follow-up; GHA must not publish PDF bytes.)*

---

## Architecture locks (do not reopen)

- **Git JSON is the durable store.** Optional `better-sqlite3` cache on Express only. Never install it in the root/GHA pipeline.
- Two entities: **Opportunity** (live apply window) and **ExamSeries** (calendar / prepare-for).
- `hasExam` is a filter, not a drop. `selectionProcess` stays a **string** on `jobs.json`.
- Source catalog is **only** `data/sources/registry.json`. No `psc.json`.
- Paste-URL publish writes `data/staging/ops_paste/<stableJobId>.json`. **Never** `workflow_dispatch` `pipeline:daily` to save a paste.
- Daily collect does **not** create `collect_jobs` per PSU URL.
- RRB national apply is `rrbapply.gov.in` + zonals. **`rrbcdg.gov.in` is Chandigarh.**
- Defence: calendar/manual only. **No CAPTCHA bypass.**
- Employment News: free highlights table only.
- Legal: rate-limit, metadata + official URLs only, disclaimer, no PDF republish.

---

## Implementation status (2026-08-20)

```text
01 → 02 → (03 ∥ 05 ∥ 07) → 04 → 06 → 08 → 09a → (09b ∥ 09c) → 10
 done  done   done  done  done  done  done  done   done   done  done
```

| PR | GitHub | On `master` |
| --- | --- | --- |
| 01 Schema / stop dropping exams | [#1](https://github.com/timus97/GovtJobsPortal/pull/1) | yes |
| 02 Optional SQLite cache | [#5](https://github.com/timus97/GovtJobsPortal/pull/5) | yes |
| 03 UPSC + SSC + calendar PDF | [#3](https://github.com/timus97/GovtJobsPortal/pull/3) | yes |
| 04 IBPS + SBI + RRB + Employment News | [#6](https://github.com/timus97/GovtJobsPortal/pull/6) | yes |
| 05 Profile + match | [#4](https://github.com/timus97/GovtJobsPortal/pull/4) | yes |
| 06 ExamSeries + Prepare | [#7](https://github.com/timus97/GovtJobsPortal/pull/7) | yes |
| 07 Ops auth | [#2](https://github.com/timus97/GovtJobsPortal/pull/2) | yes |
| 08 Paste-URL + review | [#8](https://github.com/timus97/GovtJobsPortal/pull/8) | yes |
| 09a Generic PSC + 10 states | [#9](https://github.com/timus97/GovtJobsPortal/pull/9) | yes |
| 09b Banks / regulators / post | [#10](https://github.com/timus97/GovtJobsPortal/pull/10) | yes |
| 09c School / health / defence + GATE/NET | [#11](https://github.com/timus97/GovtJobsPortal/pull/11) closed after local merge `1c2594f` | yes |
| 10 Hardening | [#12](https://github.com/timus97/GovtJobsPortal/pull/12) | `cecedb0` |

Feature branches `pr/01`–`pr/10` were deleted after merge (local and `origin`).

### Snapshot after PR10

- **628** jobs in `data/processed/jobs.json` (563 open; 1 exam fixture). Last process run still **2026-07-11** — new collectors are registered but have not been collected into the published set.
- **25** ExamSeries (UPSC, SSC, IBPS, SBI, RRB, GATE, UGC NET, CTET). Capacity target remains ~200.
- **307** registry sources, **241** enabled. 10 PSCs + P0 boards + P1 verticals + existing PSU careers.
- Match p95 **~71 ms** on 10k synthetic rows. Processed JSON + raw **~1.8 MB**.
- **22** golden notification fixtures in `tests/fixtures/golden-opportunities.json`.

---

## Stage 7 (accepted 2026-08-20)

Owner reopened “no student accounts.” Full design: [docs/STUDENT_COACHING_DESIGN.md](../STUDENT_COACHING_DESIGN.md).

- Email + password, no verify/reset v1.
- Private files on the API host.
- Dashboard: official calendars + applied jobs + custom exams.
- Then syllabus, study plan, unofficial mocks (PR12–PR15).

## Follow-ups (not catalog PRs)

These are leftover vs the spec or ops, not a 13th implementation PR:

1. Run a real `collect:daily` + `process` (with `REPLACE_PUBLISHED` only when intended) so P0/P1 boards fill `jobs.json` / `exam_series.json`.
2. Implement PDF **30-day TTL** in `rawStore` / GHA so `data/raw/pdfs` does not grow forever.
3. Optional API polish: dedicated `GET /api/opportunities`, `includeClosed=1`, `kind=` filter; write `opportunities.json` on the next successful process.
4. Always-on host: `render.yaml` is still free-tier (sleeps). Product intent is always-on Express.
5. Deferred collector-helper cleanup.

---

## Resume prompt (paste into a new session)

```text
Continue GovtJobsPortal from docs/knowledge/2026-08-19-session-all-govt-jobs-expansion.md
and docs/ALL_GOVT_JOBS_DESIGN.md. The design PR plan (01–10) is complete on master.
Do not reopen locked decisions. Next work is operational (live collect, PDF TTL,
always-on host), not a new design-plan PR.
```
