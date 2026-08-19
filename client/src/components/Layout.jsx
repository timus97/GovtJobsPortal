import { Link, NavLink, Outlet } from 'react-router-dom'
import { isProfileMatchEnabled } from '../lib/features'

const nav = [
  { to: '/', label: 'Home', end: true },
  { to: '/jobs', label: 'Jobs' },
  ...(isProfileMatchEnabled()
    ? [
        { to: '/profile', label: 'Profile' },
        { to: '/match', label: 'Match' },
      ]
    : []),
  { to: '/process', label: 'How it works' },
  { to: '/sources', label: 'Sources' },
  { to: '/about', label: 'About' },
]

export default function Layout() {
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
              <span className="brand-sub">Central · PSU · Govt Company</span>
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
