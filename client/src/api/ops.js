const BASE = import.meta.env.VITE_API_BASE || '/api'

async function readJson(res) {
  return res.json().catch(() => ({}))
}

export function isStaticPagesHost() {
  if (typeof window === 'undefined') return false
  if (import.meta.env.VITE_FEATURE_OPS === 'off') return true
  const host = window.location.hostname
  return host.endsWith('github.io') && !import.meta.env.VITE_API_BASE
}

export async function probeOpsApi() {
  if (isStaticPagesHost()) return false
  try {
    const res = await fetch(`${BASE}/health`)
    if (!res.ok) return false
    const data = await readJson(res)
    return Boolean(data && data.ok)
  } catch {
    return false
  }
}

export async function opsFetch(path, options = {}) {
  const headers = { ...(options.headers || {}) }
  if (options.body && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json'
  }
  const res = await fetch(`${BASE}/ops${path}`, {
    credentials: 'include',
    ...options,
    headers,
  })
  const body = await readJson(res)
  if (!res.ok) {
    const err = new Error(body.error || `Request failed (${res.status})`)
    err.status = res.status
    err.body = body
    throw err
  }
  return body
}

export function login(username, password) {
  return opsFetch('/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function logout() {
  return opsFetch('/logout', { method: 'POST', body: '{}' })
}

export function me() {
  return opsFetch('/me')
}

export function listOpsJobs(state) {
  const qs = state ? `?state=${encodeURIComponent(state)}` : ''
  return opsFetch(`/jobs${qs}`)
}

export function getOpsJob(id) {
  return opsFetch(`/jobs/${encodeURIComponent(id)}`)
}

export function submitCollect(url, sourceLabel) {
  return opsFetch('/collect', {
    method: 'POST',
    body: JSON.stringify({ url, sourceLabel }),
  })
}

export function cancelOpsJob(id) {
  return opsFetch(`/jobs/${encodeURIComponent(id)}/cancel`, { method: 'POST', body: '{}' })
}

export function listReview() {
  return opsFetch('/review')
}

export function patchReview(id, facts) {
  return opsFetch(`/review/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(facts),
  })
}

export function publishReview(id) {
  return opsFetch(`/review/${encodeURIComponent(id)}/publish`, { method: 'POST', body: '{}' })
}

export function rejectReview(id, reason) {
  return opsFetch(`/review/${encodeURIComponent(id)}/reject`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
}

export function unpublishReview(id) {
  return opsFetch(`/review/${encodeURIComponent(id)}/unpublish`, { method: 'POST', body: '{}' })
}

const ALLOWED_HOST_RE = /(\.gov\.in|\.nic\.in)$/i
const EXTRA_HOSTS = [
  'ibps.in',
  'sbi.co.in',
  'sbi.bank.in',
  'bank.sbi',
  'recruitment.sbi.bank.in',
  'becil.com',
  'nta.ac.in',
]

export function pasteUrlError(raw) {
  const url = String(raw || '').trim()
  if (!url) return 'Paste an https URL'
  let parsed
  try {
    parsed = new URL(url)
  } catch {
    return 'URL is not valid'
  }
  if (parsed.protocol !== 'https:') return 'URL must be https'
  const host = parsed.hostname.toLowerCase()
  if (ALLOWED_HOST_RE.test(host)) return ''
  if (EXTRA_HOSTS.some((h) => host === h || host === `www.${h}` || host.endsWith(`.${h}`))) return ''
  return 'Host is not on the official allowlist'
}

export function createOperator(username, password) {
  return opsFetch('/operators', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}
