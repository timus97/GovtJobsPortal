# Student desk UX spec

**Figma:** [GovtJobsPortal — Student desk UX](https://www.figma.com/design/37BnL3aZ62LmXqnJu1PnZH)  
**Product design:** [STUDENT_COACHING_DESIGN.md](STUDENT_COACHING_DESIGN.md)  
**Tokens:** navy `#0B1F3A`, saffron `#E8782A`, green `#0F6B4C`, bg `#F4F6F9`, text `#152033`, muted `#5B677A`, border `#DCE3EE`.  
**Type:** Inter (Figma). Product CSS also uses DM Sans + Source Serif 4 — match weights, not a new palette.

Disclaimer bar on every signed-in desk / mock screen: *Aggregator only — not an official eligibility decision. Verify on the official website.*

---

## Information architecture

```
Public:   Home · Jobs · Match · Prepare · About
Guest:    + Sign in / Create account
Student:  + Dashboard (desk) · Profile (server) · Sign out
          Desk /:item  →  files, dates, next step
                      →  Study plan
                      →  Unofficial mock → Review
Ops:      /ops (unchanged, separate cookie)
```

Entry points onto the desk:

| From | Control | Creates item |
| --- | --- | --- |
| Prepare card | **Track** | `series` / `watching` |
| Job detail | **Track** | `opportunity` / `watching` |
| Job detail | **I applied** | `opportunity` / `applied` |
| Dashboard | **Add custom exam** | `custom` (date required for countdown) |

---

## Screens (implement in this order)

### 01 Sign in · 02 Create account
Centered card, max ~480px. Eyebrow “Student desk”. Email + password. Primary navy button. Helper link to the other form. Copy: *This is not an official board account.* Pages snapshot: “Accounts need the API host.”

**In Figma:** frames `01 Sign in`, `02 Create account`.

### 03 Dashboard (home of the desk)
Header: “Your exam desk” + **Add custom exam** + **Browse prepare**.  
Four stat chips: upcoming exams, applied/admit pending, nearest days-left, mocks completed.  
Filter chips: All / Watching / Applied / Admit ready / Appeared.  
Each row: kind badge, status badge, board, title, **what to do next**, big days-left, **Open desk** + **Official site**.  
Empty: `04 Empty desk` — “Track a calendar from Prepare…”

**In Figma:** `03 Dashboard`, `04 Empty desk`.

Days-left: exam date, else last/apply date, else “Add exam date” (muted, not a fake number).

### 05 Exam desk detail
Back to dashboard. Title + badges + countdown. Saffron “what to do next” banner.  
Left: status stepper (Watching → Applied → Admit ready → Appeared → Result), editable exam date / last date / official URL.  
Private documents: admit card + result dropzones (PDF/JPEG/PNG, 5 MB). Download only if a file exists.  
Right rail: guidance checklist + **Open study plan** + **Start unofficial mock**.

**In Figma:** `05 Exam desk detail`.

### 06 Prepare + Track · 07 Job + I applied
Existing Prepare / Job layouts, plus a navy **Track** and (jobs only) saffron **I applied**. After apply, a green note: *Added to your desk as Applied.*

**In Figma:** `06 Prepare + Track`, `07 Job + I applied`.

### 08 Profile (signed in)
Same fields as today. Subcopy: *Signed in as {email}. Saved on this API host.* Import banner if browser profile exists and server is empty.

**In Figma:** `08 Profile signed in`.

### 09 Add custom exam (modal)
Name *, board, exam date *, official https URL. Cancel / Add to desk. No date → cannot start a countdown (show “Add exam date” on the row).

**In Figma:** `09 Add custom exam`.

### 10 Study plan
Series name, progress bar (done / total topics), even-split day ranges, checkboxes persisted. Unofficial topic list only.

**Specified; not yet on the Figma canvas** (Starter MCP call limit). Build from this spec + dashboard tokens.

### 11 Mock in progress · 12 Mock review
Timer chip, one question, A–D choices, Previous / Next / Submit. Review: score, unofficial disclaimer, per-question correct/explain, Back to desk / Retry.

**Specified; not yet on the canvas.**

### Mobile (390)
Dashboard list (title + days), desk detail stacked (next-step banner, download, plan, mock), sign-in card full width. Bottom nav not required v1 — keep the existing top nav, wrap.

**Specified; not yet on the canvas.**

---

## Components to reuse in code

| Component | Use |
| --- | --- |
| Disclaimer bar | All desk / mock / account pages |
| Status chip | watching / applied / admit_ready / appeared / result_in / done |
| Kind chip | Calendar / Job applied / Custom |
| Days-left block | Large number + caption |
| Next-step banner | Saffron soft, one sentence |
| File tile | Label, filename or “Upload”, Download/Replace |
| Primary / secondary / saffron buttons | Navy fill, outline, saffron fill |

---

## Copy rules

- Never say “you are eligible.”
- Always “unofficial” on mocks and syllabus.
- Always “verify on official site.”
- Never invent a last date or exam date from free text.

---

## Implementation mapping

| UX | PR |
| --- | --- |
| 01–02, 08 | PR11 (done) |
| 03–04, 06–07, 09, days-left | PR12 (done) |
| 05 files | PR13 (done) |
| 10 | PR14 (in tree) |
| 11–12 | PR15 (in tree) |
| Brand / hosting | PR16 (this PR) |

When Figma MCP quota resets, add frames 10–12 and mobile to the same file (`37BnL3aZ62LmXqnJu1PnZH`) — do not start a second file.
