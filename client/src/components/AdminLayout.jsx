import { useEffect } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { logout } from '../api/ops'
import { useAuth } from '../lib/authContext'
import { logClientView } from '../lib/clientLog'
import CollectProgressBar from './CollectProgressBar'

const links = [
  { to: '/ops', label: 'Collectors', end: true },
  { to: '/ops/sources', label: 'Sources' },
  { to: '/ops/review', label: 'Review' },
  { to: '/ops/jobs', label: 'Jobs' },
  { to: '/ops/prepare', label: 'Prepare' },
  { to: '/ops/process', label: 'Pipeline' },
  { to: '/ops/logs', label: 'Logs' },
]

export default function AdminLayout() {
  const { ops, setOps } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  useEffect(() => {
    logClientView('ops', location.pathname)
  }, [location.pathname])

  async function onLogout() {
    try {
      await logout()
    } catch {
      /* ignore */
    }
    setOps(null)
    navigate('/ops/login', { replace: true })
  }

  return (
    <div className="app-shell theme-admin">
      <header className="site-header admin-header">
        <div className="container header-inner">
          <Link to="/ops" className="brand">
            <span className="brand-mark admin-mark" aria-hidden />
            <span>
              <strong>Portal Admin</strong>
              <span className="brand-sub">Sources · collectors · catalog</span>
            </span>
          </Link>
          <nav className="nav" aria-label="Admin">
            {links.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
              >
                {item.label}
              </NavLink>
            ))}
            <span className="role-chip">{ops?.role || 'operator'}</span>
            <span className="nav-user">{ops?.username}</span>
            <button type="button" className="nav-link" onClick={onLogout}>
              Sign out
            </button>
          </nav>
        </div>
      </header>
      <CollectProgressBar compact />
      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}
