import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { loginAccount, registerAccount } from '../api/account'
import { useAuth } from '../lib/authContext'
import { isStudentEnabled } from '../lib/features'

export default function AuthLandingPage({ mode = 'login' }) {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const { ready, student, ops, refresh } = useAuth()
  const enabled = isStudentEnabled()
  const initial = mode === 'register' || params.get('tab') === 'register' ? 'register' : 'login'
  const [tab, setTab] = useState(initial)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const next = params.get('next') || '/match'

  useEffect(() => {
    if (!ready) return
    if (ops && !student) {
      navigate('/ops', { replace: true })
      return
    }
    if (student) navigate(next.startsWith('/') ? next : '/match', { replace: true })
  }, [ready, student, ops, navigate, next])

  async function onSubmit(e) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      if (tab === 'register') await registerAccount(email, password)
      else await loginAccount(email, password)
      await refresh()
      navigate(tab === 'register' ? '/profile' : next.startsWith('/') ? next : '/match', { replace: true })
    } catch (err) {
      setError(err.message || 'Could not continue')
    } finally {
      setBusy(false)
    }
  }

  if (!enabled) {
    return (
      <div className="auth-stage">
        <div className="panel auth-card">
          <p className="eyebrow">Student desk</p>
          <h1>Accounts need the API host</h1>
          <p className="muted">GitHub Pages is a catalog snapshot only. Open the always-on Express host to sign in.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="auth-stage">
      <section className="auth-intro">
        <p className="eyebrow">For candidates</p>
        <h1>Sign in to see jobs that fit you</h1>
        <p className="lead">
          Create an account, save your profile, and we rank currently open government and PSU
          windows against those facts. Search any listing separately. Prepare-for calendars stay
          unofficial.
        </p>
        <ul className="check-list">
          <li>Editable profile (reservation category required for match)</li>
          <li>Matches from your profile — not an official decision</li>
          <li>Independent job search and exam calendars</li>
        </ul>
      </section>
      <form className="panel auth-card" onSubmit={onSubmit}>
        <div className="auth-tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'login'}
            className={tab === 'login' ? 'auth-tab active' : 'auth-tab'}
            onClick={() => {
              setTab('login')
              setError('')
            }}
          >
            Sign in
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'register'}
            className={tab === 'register' ? 'auth-tab active' : 'auth-tab'}
            onClick={() => {
              setTab('register')
              setError('')
            }}
          >
            Create account
          </button>
        </div>
        <h2>{tab === 'register' ? 'Create an account' : 'Welcome back'}</h2>
        <p className="muted">
          {tab === 'register'
            ? 'Email + password (at least 10 characters). No email verification in this version.'
            : 'This is not a board or PSU account.'}
        </p>
        {error && <p className="muted">{error}</p>}
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
            autoComplete={tab === 'register' ? 'new-password' : 'current-password'}
            minLength={tab === 'register' ? 10 : undefined}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        <button className="btn btn-primary btn-lg" type="submit" disabled={busy}>
          {busy ? 'Please wait…' : tab === 'register' ? 'Create account' : 'Sign in'}
        </button>
        {tab === 'login' && (
          <p className="muted small" style={{ margin: '0.85rem 0 0' }}>
            <Link to="/account/forgot">Forgot password?</Link>
          </p>
        )}
        <p className="muted small" style={{ margin: '1rem 0 0' }}>
          Site operators use a separate login.{' '}
          <Link to="/ops/login">Admin console</Link>
        </p>
      </form>
    </div>
  )
}
