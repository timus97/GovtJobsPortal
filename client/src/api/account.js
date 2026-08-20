const BASE = import.meta.env.VITE_API_BASE || '/api'

async function readJson(res) {
  return res.json().catch(() => ({}))
}

export async function accountFetch(path, options = {}) {
  const headers = { ...(options.headers || {}) }
  if (options.body && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json'
  }
  const res = await fetch(`${BASE}${path}`, {
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

export function registerAccount(email, password) {
  return accountFetch('/account/register', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export function loginAccount(email, password) {
  return accountFetch('/account/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export function logoutAccount() {
  return accountFetch('/account/logout', { method: 'POST', body: '{}' })
}

export function meAccount() {
  return accountFetch('/account/me')
}

export function getServerProfile() {
  return accountFetch('/me/profile')
}

export function saveServerProfile(profile) {
  return accountFetch('/me/profile', {
    method: 'PUT',
    body: JSON.stringify(profile),
  })
}

export function importServerProfile(profile) {
  return accountFetch('/me/profile/import', {
    method: 'POST',
    body: JSON.stringify({ profile }),
  })
}