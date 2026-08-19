import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { login, me, probeOpsApi } from '../../api/ops'
import OpsRequiresApi from './OpsRequiresApi'

export default function OpsLoginPage() {
  const navigate = useNavigate()
  const [apiOk, setApiOk] = useState(null)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let cancelled = false
    probeOpsApi().then((ok) => {
      if (cancelled) return
      setApiOk(ok)
      if (!ok) return
      me()
        .then(() => {
          if (!cancelled) navigate('/ops', { replace: true })
        })
        .catch(() => {})
    })
    return () => {
      cancelled = true
    }
  }, [navigate])

  async function onSubmit(e) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      await login(username, password)
      navigate('/ops', { replace: true })
    } catch (err) {
      setError(err.message || 'Login failed')
    } finally {
      setBusy(false)
    }
  }

  if (apiOk === null) {
    return (
      <div className="section">
        <div className="container">
          <p className="muted">Checking API host…</p>
        </div>
      </div>
    )
  }

  if (!apiOk) return <OpsRequiresApi />

  return (
    <div className="section">
      <div className="container">
        <form className="panel ops-card ops-login-card" onSubmit={onSubmit}>
          <p className="eyebrow">Operators</p>
          <h1>Sign in</h1>
          <p className="muted">
            Individual operator account. First-run username is <code>admin</code> with the host{' '}
            <code>OPERATOR_PASSWORD</code> (bootstrap only).
          </p>
          {error && <p className="error-box">{error}</p>}
          <label className="field">
            <span>Username</span>
            <input
              name="username"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </label>
          <label className="field">
            <span>Password</span>
            <input
              name="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>
          <button className="btn btn-primary" type="submit" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
          <p className="muted small" style={{ margin: '1rem 0 0' }}>
            <Link to="/">Back to public site</Link>
          </p>
        </form>
      </div>
    </div>
  )
}
