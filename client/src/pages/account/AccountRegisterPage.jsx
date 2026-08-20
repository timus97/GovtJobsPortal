import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { isStudentEnabled } from '../../lib/features'
import { registerAccount } from '../../api/account'

export default function AccountRegisterPage() {
  const navigate = useNavigate()
  const enabled = isStudentEnabled()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function onSubmit(e) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      await registerAccount(email, password)
      navigate('/profile', { replace: true })
    } catch (err) {
      setError(err.message || 'Registration failed')
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
            <p className="muted">Open the always-on Express host to create an account.</p>
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
          <h1>Create an account</h1>
          <p className="muted">
            Email + password (at least 10 characters). No email verification in this version. Profile
            facts stay on this API host — not on GitHub Pages.
          </p>
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
              autoComplete="new-password"
              minLength={10}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>
          <button className="btn btn-primary" type="submit" disabled={busy}>
            {busy ? 'Creating…' : 'Create account'}
          </button>
          <p className="muted small" style={{ margin: '1rem 0 0' }}>
            Already have an account? <Link to="/account/login">Sign in</Link>
          </p>
        </form>
      </div>
    </div>
  )
}