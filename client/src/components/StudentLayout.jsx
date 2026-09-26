import { useEffect } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { logoutAccount } from '../api/account'
import { useAuth } from '../lib/authContext'
import { logClientView } from '../lib/clientLog'
import { isPrepareEnabled } from '../lib/features'

const links = [
  { to: '/match', label: 'Matches' },
  { to: '/jobs', label: 'Search' },
  ...(isPrepareEnabled() ? [{ to: '/prepare', label: 'Prepare' }] : []),
  { to: '/profile', label: 'Profile' },
  { to: '/dashboard', label: 'Desk' },
]

export default function StudentLayout() {
  const { student, setStudent } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  useEffect(() => {
    logClientView('student', location.pathname)
  }, [location.pathname])

  async function onLogout() {
    try {
      await logoutAccount()
    } catch {
      /* ignore */
    }
    setStudent(null)
    navigate('/', { replace: true })
  }

  return (
    <div className="app-shell theme-student">
      <div className="disclaimer-bar">
        Aggregator only — not an official eligibility decision. Always verify on the official site.
      </div>
      <header className="site-header">
        <div className="container header-inner">
          <Link to="/match" className="brand">
            <span className="brand-mark" aria-hidden />
            <span>
              <strong>Sarkari Desk</strong>
              <span className="brand-sub">Your matches · search · prepare</span>
            </span>
          </Link>
          <nav className="nav" aria-label="Student">
            {links.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
              >
                {item.label}
              </NavLink>
            ))}
            <span className="nav-user" title={student?.email || ''}>
              {student?.email || 'Signed in'}
            </span>
            <button type="button" className="nav-link" onClick={onLogout}>
              Sign out
            </button>
          </nav>
        </div>
      </header>
      <main className="main">
        <Outlet />
      </main>
      <footer className="site-footer">
        <div className="container footer-inner">
          <p>
            Private exam desk on this host. Jobs are pointers to official notifications — apply on the
            board or PSU site.
          </p>
          <p className="muted small">
            Operator? <Link to="/ops/login">Open the admin console</Link>
          </p>
        </div>
      </footer>
    </div>
  )
}
