import { accountFetch } from '../api/account'
import { opsFetch } from '../api/ops'

export function logClientView(role, path) {
  const action = 'client.view'
  if (role === 'ops' || role === 'operator' || role === 'admin') {
    opsFetch('/client-log', { method: 'POST', body: JSON.stringify({ action, path }) }).catch(() => {})
    return
  }
  if (role === 'student') {
    accountFetch('/me/events', { method: 'POST', body: JSON.stringify({ action, path }) }).catch(() => {})
  }
}
