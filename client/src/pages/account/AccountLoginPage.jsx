import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { isStudentEnabled } from '../../lib/features'
import { loginAccount, meAccount } from '../../api/account'

export default function AccountLoginPage() {
  const navigate = useNavigate()
  const enabled = isStudentEnabled()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!enabled) return undefined
    let cancelled = false
    meAccount()
      .then(() => {
        if (!cancelled) navigate('/profile', { replace: true })
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [enabled, navigate])

  async function onSubmit(e) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      await loginAccount(email, password)
      navigate('/profile', { replace: true })
    } catch (err) {
      setError(err.message || 'Sign in failed')
    } finally {
      setBusy(false)
    }
  }

  if (!enabled) {
    return (
      <div className="section">
        <div className="container">
          <div className="panel ops-card ops-login-card">
            <p className="eyebrow">Student desk</p>
            <h1>Accounts need the API host</h1>
            <p className="muted">
              GitHub Pages is a catalog snapshot only. Open the always-on Express host to sign in.
            </p>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="section">
      <div className="container">
        <form className="panel ops-card ops-login-card" onSubmit={onSubmit}>
          <p className="eyebrow">Student desk</p>
          <h1>Sign in</h1>
          <p className="muted">Email and password. This is not an official board account.</p>
          {error && <p className="error-box">{error}</p>}
          <label className="field">
            <span>Email</span>
            <input
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </label>
          <label className="field">
            <span>Password</span>
            <input
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
            No account? <Link to="/account/register">Create one</Link>
          </p>
        </form>
      </div>
    </div>
  )
}