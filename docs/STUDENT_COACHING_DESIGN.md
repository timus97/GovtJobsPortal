# Stage 7 — Student accounts, exam desk, and coaching

| Field | Value |
| --- | --- |
| Document | Design: student accounts + exam desk + unofficial coaching |
| Product | GovtJobsPortal (exam preparation desk on top of the jobs catalog) |
| Date | 2026-08-20 |
| Status | **Accepted from owner requirements.** Implementation starts at PR11. |
| Supersedes | v1 lock “no public candidate accounts / no server PII” — **reopened by owner** |
| Catalog design | [ALL_GOVT_JOBS_DESIGN.md](ALL_GOVT_JOBS_DESIGN.md) (PR01–PR10 complete) |

Jobs, exam series, and the registry stay **git JSON**. Student PII, tracker rows, admit cards, results, and mock scores are **never** git JSON and **never** GitHub Pages.

---

## 1. Requirements (locked)

### 1.1 Owner answers (2026-08-20)

| # | Question | Answer |
| --- | --- | --- |
| 1 | Sign up | Email + password. Unique email. No verify-before-use. No password-reset in v1. |
| 2 | Admit card / result files | Private files on the API host. Owner-only download. Never Pages or public job JSON. |
| 3 | Dashboard contents | Official calendars **and** applied jobs **and** custom exams the student types (name + date). |
| 4 | Prep / guidance depth | Full coaching now: desk + syllabus + study plans + mock tests. Ship as PR11–PR16, not a later stage. |
| 5 | Account extras | Sign up, sign in, persist session only. |

### 1.2 Keep from v1

Browse `/jobs` (exam + no-exam). Explainable `POST /api/match` with required reservation category. Prepare-for ExamSeries (`applyNever` on NET/GATE/CTET). CUET excluded. Ops paste-URL stays operator-only. No official PDF republish. No CAPTCHA bypass. No invented eligibility.

### 1.3 Functional

**Accounts.** Register email + password (min 10). Duplicate email 409. Session cookie `student_session` (not `ops_session`), idle 14 days. Unauthenticated desk/files → login. Ops session cannot call student routes.

**Profile.** Same fields as v1, stored on the API host. Reservation category still required for match. After login, if browser `sarkari.profile.v1` exists and the server profile is empty, offer one-click import. Logged-in match uses the server profile; anonymous match still accepts a request body.

**Desk `/dashboard`.** Tracked items: `series` | `opportunity` | `custom`. Statuses: `watching` | `applied` | `admit_ready` | `appeared` | `result_in` | `done`. Days-left from exam date, else last/apply date, else “add a date.” Track / I applied from Prepare and Job detail. Guidance strip: apply → admit card → exam in N days → result.

**Documents.** One admit card and one result per item (replace). PDF/JPEG/PNG ≤ 5 MB. Path `data/student-files/<studentId>/<itemId>/` (gitignored). Authenticated owner-only download. No OCR.

**Syllabus / plan.** Unofficial topic packs in `data/coaching/syllabus/<seriesId>.json`. Rule-based even-split plan to the exam date. Topic ticks persist.

**Mocks.** Unofficial banks in `data/coaching/mocks/<seriesId>.json`. Timed attempt, score on submit, review, save. First banks: CSE / CGL / IBPS PO / UGC NET samples. Never scrape official papers.

### 1.4 Non-goals

Email verify, password reset, OAuth, phone OTP, leaderboards, social, OCR, paid test series, application fees, student files in git/Pages, official paper republication.

### 1.5 Constraints

Always-on Express + **persistent disk**. Free Render sleep/redeploy wipes student data unless the disk is persistent. Catalog SoR stays `data/processed/*.json`. Student SoR is a **separate** host store under `STUDENT_DATA_DIR` (not `portal.sqlite` — that cache is wiped on rebuild). Rate-limit register/login 5 / 15 min / IP. Disclaimer on desk and mocks.

---

## 2. Design

### 2.1 Runtime

| UI | API |
| --- | --- |
| `/account/login`, `/account/register` | `POST /api/account/register` `login` `logout` · `GET /api/account/me` |
| `/profile` | `GET/PUT /api/me/profile` |
| `/dashboard` | `GET/POST /api/me/items` |
| `/desk/:itemId` | `PATCH/DELETE /api/me/items/:id` · file upload/download |
| Prepare / Job **Track** | uses items API |
| Desk plan / mock | `GET /api/coaching/syllabus/:seriesId` · `GET /api/coaching/mocks/:seriesId` · `POST /api/me/mocks/:seriesId/attempts` |

`FEATURE_STUDENT` / `VITE_FEATURE_STUDENT`: on for the API host; off on github.io without `VITE_API_BASE`.

Cookie: `student_session`, httpOnly, SameSite=Lax, Secure in production, HMAC with `SESSION_SECRET`, payload `aud=student`. Ops cookies stay `ops_session`.

Password hashing: shared `server/src/services/password.js` (scrypt, extracted from ops).

### 2.2 Student store

Directory `STUDENT_DATA_DIR` (default `data/students/`). v1 implementation: **atomic JSON** `students.json` (same write pattern as `operatorStore`). SQLite is allowed later; **never** put student tables in `data/cache/portal.sqlite`.

```
students[]: { id, email, emailNorm, passwordHash, createdAt, lastLoginAt }
profiles[studentId]: { ...eligibility profile, updatedAt }
items[]: { id, studentId, kind, refId, title, board, status, examDate, lastDate, officialUrl, notes, createdAt, updatedAt }
files[]: { id, itemId, kind: admit|result, storedPath, mime, bytes, originalName, uploadedAt }
topicProgress[]: { studentId, seriesId, topicId, doneAt }
mockAttempts[]: { id, studentId, seriesId, startedAt, submittedAt, score, answers }
```

Files: `STUDENT_FILES_DIR/<studentId>/<itemId>/admit|result`.

Coaching content (git, no PII):

```json
{ "seriesId": "ssc-cgl", "unofficial": true, "topics": [{ "id": "quant", "title": "Quantitative aptitude", "weight": 1 }] }
{ "seriesId": "ssc-cgl", "unofficial": true, "durationMin": 20, "questions": [{ "id": "q1", "stem": "...", "choices": [], "answerIndex": 0, "explain": "..." }] }
```

### 2.3 Days-left

`anchor = item.examDate || series.expectedExam || item.lastDate || opportunity.lastDate`  
Missing → “Add exam date.” Else whole UTC days from today (negative = “N days ago”). Never parse dates from `eligibility[]`.

### 2.4 Key decisions

| Decision | Choice | Why |
| --- | --- | --- |
| Identity | Email + password, no verify/reset | Owner |
| Session | Separate `student_session` + `aud=student` | Do not mix with ops |
| Student SoR | Host JSON/files, gitignored | PII/PDFs cannot be git or Pages |
| Catalog SoR | Unchanged git JSON | Shared vacancies/calendars |
| Tracker kinds | series + opportunity + custom | Owner |
| Coaching content | Hand-built unofficial JSON | No official paper scrape |
| Host | Always-on + persistent disk | Ephemeral disk loses the desk |
| Match | Anonymous body still works | Logged-in uses server profile |

---

## 3. PR plan

| PR | Title | Deps | Ships |
| --- | --- | --- | --- |
| **11** | Student accounts + server profile | — | register/login, `GET/PUT /api/me/profile`, import localStorage, nav |
| **12** | Dashboard tracker + countdowns | 11 | items API, `/dashboard`, Track on Prepare/Jobs, days-left, guidance strip |
| **13** | Private admit card + result uploads | 12 | 5 MB PDF/JPEG/PNG, owner-only download |
| **14** | Syllabus + study plan | 12 | unofficial topic packs, even-split plan, ticks |
| **15** | Mock tests | 14 | timed unofficial banks, score, review |
| **16** | Docs + hosting + brand | 15 | README/HOSTING persistent disk, brand copy |

Order: **11 → 12 → (13 ∥ 14) → 15 → 16.**

---

## 4. Tests

- `tests/pr11-student-account.js` — register, duplicate 409, login cookie, profile persist, ops cookie rejected, rate-limit, FEATURE off → 404
- Later: `pr12-tracker.js`, `pr13-files.js`, `pr14-plan.js`, `pr15-mocks.js`

Temp dirs for student data (same idea as `tests/pr08-ops-paste.js`).
