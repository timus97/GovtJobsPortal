# Classification rules (no-exam filter)

## Exclude (drop) if selection text matches

- written test / written examination / competitive exam  
- CBT / computer based test / online test  
- GATE, UPSC, SSC, IBPS, RRB exam  
- tier-I / tier-II, preliminary exam, mains examination  

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

If neither include nor exclude matches clearly → quarantine (`needsReview`), do not auto-publish.
