import { Link, Outlet } from 'react-router-dom'

export default function PublicLayout() {
  return (
    <div className="app-shell theme-auth">
      <header className="site-header auth-header">
        <div className="container header-inner">
          <Link to="/" className="brand">
            <span className="brand-mark" aria-hidden />
            <span>
              <strong>Sarkari Desk</strong>
              <span className="brand-sub">Match · search · prepare</span>
            </span>
          </Link>
          <nav className="nav" aria-label="Public">
            <Link to="/ops/login" className="nav-link nav-link-ops">
              Admin
            </Link>
          </nav>
        </div>
      </header>
      <main className="main">
        <Outlet />
      </main>
      <footer className="site-footer">
        <div className="container footer-inner">
          <p className="muted">
            Aggregator only. Not affiliated with the Government of India or any board or PSU.
          </p>
        </div>
      </footer>
    </div>
  )
}
