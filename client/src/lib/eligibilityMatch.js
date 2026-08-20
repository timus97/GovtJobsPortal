// re-export for ESM by wrapping require is not available in browser.
import matchMod from '@shared/eligibilityMatch.js'

const mod = matchMod?.default ?? matchMod

export const matchOpportunities = mod.matchOpportunities
export const matchExamSeries = mod.matchExamSeries
export const LAST_DATE_UNKNOWN_CHIP = mod.LAST_DATE_UNKNOWN_CHIP
export const VERIFY_BADGE = mod.VERIFY_BADGE
export const AGE_WHEN_NOTIFIED = mod.AGE_WHEN_NOTIFIED
export default mod
