import { accountFetch } from './account'

export function getSyllabus(seriesId) {
  return accountFetch(`/coaching/syllabus/${encodeURIComponent(seriesId)}`)
}

export function getPlan(seriesId, itemId) {
  const qs = itemId ? `?itemId=${encodeURIComponent(itemId)}` : ''
  return accountFetch(`/me/plan/${encodeURIComponent(seriesId)}${qs}`)
}

export function setTopicDone(seriesId, topicId, done) {
  return accountFetch(`/me/plan/${encodeURIComponent(seriesId)}/topics/${encodeURIComponent(topicId)}`, {
    method: 'PUT',
    body: JSON.stringify({ done: Boolean(done) }),
  })
}
