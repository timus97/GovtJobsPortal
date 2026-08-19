# Classification rules

`hasExam` is a boolean filter, not a drop rule. Exam keywords classify onto a `selectionProcess` code (`cbt`, `written_multi_stage`, `interview_after_exam`, `physical`). Unknown selection still quarantines (`needsReview`).

## Exam codes (classify, do not drop)

| Code | Signals |
|------|---------|
| cbt | CBT, computer based test, online test, online examination |
| written_multi_stage | written test / examination, competitive exam, GATE, UPSC, SSC, IBPS, RRB exam, tier-I/II, preliminary exam, mains examination, departmental competitive |
| interview_after_exam | interview after written/CBT |
| physical | PET, PST, physical standard, physical endurance |

## Include codes

| Code | Signals |
|------|---------|
| walk_in | walk-in, walk in interview |
| interview_only | interview only, personal interview |
| merit | purely on merit, no written test |
| contract_interview | contract + interview |
| direct_recruitment | direct recruitment without exam language |
| apprenticeship | apprentice / Apprentices Act |

## Unknown

If neither exam nor include matches clearly → quarantine (`needsReview`), do not auto-publish.
