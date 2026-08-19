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

export function listOpsJobs() {
  return opsFetch('/jobs')
}

export function createOperator(username, password) {
  return opsFetch('/operators', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}
