/** Pages snapshot host (no always-on API unless VITE_API_BASE is set). */
export function isStaticPagesHost() {
  if (typeof window === 'undefined') return false
  return /\.github\.io$/i.test(window.location.hostname)
}

/**
 * FEATURE_PROFILE_MATCH: default on for API/dev.
 * Off when VITE_FEATURE_PROFILE_MATCH is explicitly off, or on github.io without VITE_API_BASE.
 */
export function isProfileMatchEnabled() {
  const flag = String(import.meta.env.VITE_FEATURE_PROFILE_MATCH ?? '')
    .trim()
    .toLowerCase()
  if (flag === 'off' || flag === 'false' || flag === '0') return false
  if (flag === 'on' || flag === 'true' || flag === '1') return true
  const apiBase = String(import.meta.env.VITE_API_BASE ?? '').trim()
  if (isStaticPagesHost() && !apiBase) return false
  return true
}

/**
 * FEATURE_PREPARE: default on once PR06 ships (including Pages JSON snapshot).
 * Off only when VITE_FEATURE_PREPARE is explicitly off.
 */
export function isPrepareEnabled() {
  const flag = String(import.meta.env.VITE_FEATURE_PREPARE ?? '')
    .trim()
    .toLowerCase()
  if (flag === 'off' || flag === 'false' || flag === '0') return false
  return true
}
