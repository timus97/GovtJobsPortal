import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import { isPrepareEnabled, isProfileMatchEnabled, isStudentEnabled } from '../lib/features'
import { isStaticPagesHost } from '../api/ops'
import { logoutAccount, meAccount } from '../api/account'
import { onStudentSession } from '../lib/studentSession'

const nav = [
  { to: '/', label: 'Home', end: true },
  { to: '/jobs', label: 'Jobs' },
  ...(isProfileMatchEnabled()
    ? [
        { to: '/profile', label: 'Profile' },
        { to: '/match', label: 'Match' },
      ]
    : []),
  ...(isPrepareEnabled() ? [{ to: '/prepare', label: 'Prepare' }] : []),
  { to: '/process', label: 'How it works' },
  { to: '/sources', label: 'Sources' },
  { to: '/about', label: 'About' },
]

export default function Layout() {
  const showOps = !isStaticPagesHost()
  const studentOn = isStudentEnabled()
  const [student, setStudent] = useState(null)

  useEffect(() => {
    if (!studentOn) return undefined
    let cancelled = false
    function load() {
      meAccount()
        .then((body) => {
          if (!cancelled) setStudent(body.student || null)
        })
        .catch(() => {
          if (!cancelled) setStudent(null)
        })
    }
    load()
    const off = onStudentSession(load)
    return () => {
      cancelled = true
      off()
    }
  }, [studentOn])

  async function onLogout() {
    try {
      await logoutAccount()
    } catch {
      /* ignore */
    }
    setStudent(null)
  }

  return (
    <div className="app-shell">
      <div className="disclaimer-bar">
        Aggregator only — always verify and apply on the official website. Not affiliated with Government of India or any PSU.
      </div>
      <header className="site-header">
        <div className="container header-inner">
          <Link to="/" className="brand">
            <span className="brand-mark" aria-hidden />
            <span>
              <strong>NoExam Sarkari</strong>
              <span className="brand-sub">Jobs · Exam desk · Prepare</span>
            </span>
          </Link>
          <nav className="nav" aria-label="Main">
            {nav.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
              >
                {item.label}
              </NavLink>
            ))}
            {studentOn && student && (
              <NavLink
                to="/dashboard"
                className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
              >
                Desk
              </NavLink>
            )}
            {studentOn && !student && (
              <NavLink
                to="/account/login"
                className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
              >
                Sign in
              </NavLink>
            )}
            {studentOn && student && (
              <button type="button" className="nav-link" onClick={onLogout}>
                Sign out
              </button>
            )}
            {showOps && (
              <NavLink
                to="/ops"
                className={({ isActive }) =>
                  isActive ? 'nav-link active nav-link-ops' : 'nav-link nav-link-ops'
                }
              >
                Ops
              </NavLink>
            )}
          </nav>
        </div>
      </header>
      <main className="main">
        <Outlet />
      </main>
      <footer className="site-footer">
        <div className="container footer-inner">
          <p>
            <strong>NoExam Sarkari Jobs</strong> lists Indian government and PSU openings that do not require a competitive written test.
            Coverage is curated and growing — not exhaustive.
          </p>
          <p className="muted">
            Always confirm eligibility, last date, and selection process on the official notification before applying.
          </p>
        </div>
      </footer>
    </div>
  )
}
