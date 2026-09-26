import { useState } from 'react'
import { Link } from 'react-router-dom'
import { requestPasswordReset } from '../../api/account'
import { isStudentEnabled } from '../../lib/features'

export default function ForgotPasswordPage() {
  const enabled = isStudentEnabled()
  const [email, setEmail] = useState('')
  const [done, setDone] = useState(false)
  const [busy, setBusy] = useState(false)

  async function onSubmit(e) {
    e.preventDefault()
    setDone(false)
    setBusy(true)
    try {
      await requestPasswordReset(email)
    } catch {
      /* same copy whether send succeeds or fails */
    } finally {
      setDone(true)
      setBusy(false)
    }
  }

  if (!enabled) {
    return (
      <div className="auth-stage">
        <div className="panel auth-card">
          <p className="eyebrow">Student desk</p>
          <h1>Password reset needs the API host</h1>
          <p className="muted">Open the always-on Express host to reset a password.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="auth-stage">
      <section className="auth-intro">
        <p className="eyebrow">Student desk</p>
        <h1>Reset your password</h1>
        <p className="lead">
          Enter the email you used to register. If it matches an account, we email a one-hour,
          one-time link (check spam). This is not a board or PSU account.
        </p>
      </section>
      <form className="panel auth-card" onSubmit={onSubmit}>
        <h2>Forgot password</h2>
        {done && (
          <p className="muted" role="status">
            If that email is registered, we sent a reset link. Check your inbox and spam folder.
          </p>
        )}
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
        <button className="btn btn-primary btn-lg" type="submit" disabled={busy}>
          {busy ? 'Sending…' : 'Send reset link'}
        </button>
        <p className="muted small" style={{ margin: '1rem 0 0' }}>
          <Link to="/account/login">Back to sign in</Link>
        </p>
      </form>
    </div>
  )
}
