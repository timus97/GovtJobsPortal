import { accountFetch } from './account'

export function getPublicMockBank(seriesId) {
  return accountFetch(`/coaching/mocks/${encodeURIComponent(seriesId)}`)
}

export function startMockAttempt(seriesId, itemId) {
  return accountFetch(`/me/mocks/${encodeURIComponent(seriesId)}/attempts`, {
    method: 'POST',
    body: JSON.stringify(itemId ? { itemId } : {}),
  })
}

export function getMockAttempt(attemptId) {
  return accountFetch(`/me/mocks/attempts/${encodeURIComponent(attemptId)}`)
}

export function submitMockAttempt(attemptId, answers) {
  return accountFetch(`/me/mocks/attempts/${encodeURIComponent(attemptId)}/submit`, {
    method: 'POST',
    body: JSON.stringify({ answers }),
  })
}
