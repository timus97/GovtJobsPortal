import { useState } from 'react'
import { Link } from 'react-router-dom'
import { createDeskItem } from '../api/account'
import { isStudentEnabled } from '../lib/features'

export default function TrackButton({ kind, refId, applied, compact }) {
  const [state, setState] = useState('idle')
  const [error, setError] = useState('')
  const studentOn = isStudentEnabled()

  if (!studentOn) return null

  async function onClick(status) {
    setError('')
    setState('busy')
    try {
      await createDeskItem({ kind, refId, status })
      setState('done')
    } catch (err) {
      if (err.status === 401) {
        setState('need-login')
        return
      }
      setError(err.message || 'Could not add to desk')
      setState('idle')
    }
  }

  if (state === 'need-login') {
    return (
      <Link to="/account/login" className="btn btn-secondary">
        Sign in to track
      </Link>
    )
  }

  if (state === 'done') {
    return (
      <Link to="/dashboard" className="btn btn-secondary">
        On your desk
      </Link>
    )
  }

  return (
    <>
      <button
        type="button"
        className="btn btn-secondary"
        disabled={state === 'busy'}
        onClick={() => onClick('watching')}
      >
        Track
      </button>
      {applied && (
        <button
          type="button"
          className="btn btn-primary"
          disabled={state === 'busy'}
          onClick={() => onClick('applied')}
        >
          I applied
        </button>
      )}
      {error && !compact && <p className="muted small">{error}</p>}
    </>
  )
}
