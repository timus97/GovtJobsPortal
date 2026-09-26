import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { peekResetToken, resetPassword } from '../../api/account'
import { useAuth } from '../../lib/authContext'
import { isStudentEnabled } from '../../lib/features'

export default function ResetPasswordPage() {
  const enabled = isStudentEnabled()
  const navigate = useNavigate()
  const { refresh } = useAuth()
  const [params] = useSearchParams()
  const token = params.get('token') || ''
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [tokenOk, setTokenOk] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!enabled || !token) {
      setTokenOk(false)
      return undefined
    }
    let cancelled = false
    peekResetToken(token)
      .then((body) => {
        if (!cancelled) setTokenOk(Boolean(body && body.ok))
      })
      .catch(() => {
        if (!cancelled) setTokenOk(false)
      })
    return () => {
      cancelled = true
    }
  }, [enabled, token])

  async function onSubmit(e) {
    e.preventDefault()
    setError('')
    if (password !== confirm) {
      setError('passwords-differ')
      return
    }
    setBusy(true)
    try {
      await resetPassword(token, password)
      await refresh()
      navigate('/profile', { replace: true })
    } catch {
      setTokenOk(false)
    } finally {
      setBusy(false)
    }
  }

  if (!enabled) {
    return (
      <div className="auth-stage">
        <div className="panel auth-card">
          <p className="eyebrow">Student desk</p>
          <h1>Password reset needs the API host</h1>
        </div>
      </div>
    )
  }

  if (!token || tokenOk === false) {
    return (
      <div className="auth-stage">
        <div className="panel auth-card">
          <p className="eyebrow">Student desk</p>
          <h1>Link expired</h1>
          <p className="muted">This reset link is invalid or has already been used.</p>
          <Link to="/account/forgot" className="btn btn-primary">
            Request a new link
          </Link>
        </div>
      </div>
    )
  }

  if (tokenOk === null) {
    return (
      <div className="auth-stage">
        <div className="panel auth-card">
          <p className="muted">Checking reset link…</p>
        </div>
      </div>
    )
  }

  return (
    <div className="auth-stage">
      <section className="auth-intro">
        <p className="eyebrow">Student desk</p>
        <h1>Choose a new password</h1>
        <p className="lead">At least 10 characters. You will be signed in after it is saved.</p>
      </section>
      <form className="panel auth-card" onSubmit={onSubmit}>
        <h2>New password</h2>
        {error === 'passwords-differ' && (
          <p className="muted">The two passwords must match.</p>
        )}
        <label className="field">
          <span>New password</span>
          <input
            type="password"
            autoComplete="new-password"
            minLength={10}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        <label className="field">
          <span>Confirm password</span>
          <input
            type="password"
            autoComplete="new-password"
            minLength={10}
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            required
          />
        </label>
        <button className="btn btn-primary btn-lg" type="submit" disabled={busy}>
          {busy ? 'Saving…' : 'Save password and sign in'}
        </button>
        <p className="muted small" style={{ margin: '1rem 0 0' }}>
          <Link to="/account/login">Back to sign in</Link>
        </p>
      </form>
    </div>
  )
}
