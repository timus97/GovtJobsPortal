import { notifyStudentSession } from '../lib/studentSession'

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

export async function registerAccount(email, password) {
  const body = await accountFetch('/account/register', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
  notifyStudentSession()
  return body
}

export async function loginAccount(email, password) {
  const body = await accountFetch('/account/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
  notifyStudentSession()
  return body
}

export async function logoutAccount() {
  const body = await accountFetch('/account/logout', { method: 'POST', body: '{}' })
  notifyStudentSession()
  return body
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

export function listDeskItems(status) {
  const qs = status ? `?status=${encodeURIComponent(status)}` : ''
  return accountFetch(`/me/items${qs}`)
}

export function createDeskItem(payload) {
  return accountFetch('/me/items', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getDeskItem(id) {
  return accountFetch(`/me/items/${encodeURIComponent(id)}`)
}

export function patchDeskItem(id, payload) {
  return accountFetch(`/me/items/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export function deleteDeskItem(id) {
  return accountFetch(`/me/items/${encodeURIComponent(id)}`, { method: 'DELETE' })
}