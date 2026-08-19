import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { fetchSources } from '../../api/jobs'
import { createOperator, listOpsJobs, logout, me, probeOpsApi } from '../../api/ops'
import { formatDateTime } from '../../utils/labels'
import OpsRequiresApi from './OpsRequiresApi'

function scrapeBadge(source) {
  if (!source.enabled) return { text: 'Paused', className: 'badge badge-muted' }
  const run = source.lastScrape
  if (!run) return { text: 'No last scrape', className: 'badge badge-muted' }
  if (!run.ok) return { text: 'Error', className: 'badge badge-warn' }
  return { text: 'Ok', className: 'badge badge-soft' }
}

export default function OpsDashboard() {
  const navigate = useNavigate()
  const [apiOk, setApiOk] = useState(null)
  const [session, setSession] = useState(null)
  const [jobs, setJobs] = useState([])
  const [sourcesPayload, setSourcesPayload] = useState({ sources: [] })
  const [error, setError] = useState('')
  const [newUser, setNewUser] = useState('')
  const [newPass, setNewPass] = useState('')
  const [addMsg, setAddMsg] = useState('')
  const [adding, setAdding] = useState(false)

  useEffect(() => {
    let cancelled = false
    async function boot() {
      const ok = await probeOpsApi()
      if (cancelled) return
      setApiOk(ok)
      if (!ok) return
      try {
        const who = await me()
        if (cancelled) return
        setSession(who)
        const [jobPayload, src] = await Promise.all([listOpsJobs(), fetchSources()])
        if (cancelled) return
        setJobs(jobPayload.items || [])
        setSourcesPayload(src || { sources: [] })
      } catch (err) {
        if (cancelled) return
        if (err.status === 401) {
          navigate('/ops/login', { replace: true })
          return
        }
        setError(err.message || 'Failed to load ops dashboard')
      }
    }
    boot()
    return () => {
      cancelled = true
    }
  }, [navigate])

  const sources = sourcesPayload.sources || []
  const health = useMemo(() => {
    const enabled = sources.filter((s) => s.enabled)
    const failed = sources.filter((s) => s.lastScrape && s.lastScrape.ok === false)
    const ok = sources.filter((s) => s.lastScrape && s.lastScrape.ok)
    return { total: sources.length, enabled: enabled.length, failed: failed.length, ok: ok.length }
  }, [sources])

  async function onLogout() {
    try {
      await logout()
    } catch {
      /* still leave */
    }
    navigate('/ops/login', { replace: true })
  }

  async function onAddOperator(e) {
    e.preventDefault()
    setAddMsg('')
    setAdding(true)
    try {
      const result = await createOperator(newUser, newPass)
      setAddMsg(`Created operator ${result.operator.username}`)
      setNewUser('')
      setNewPass('')
    } catch (err) {
      setAddMsg(err.message || 'Could not add operator')
    } finally {
      setAdding(false)
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

  if (!session) {
    return (
      <div className="section">
        <div className="container">
          <p className="muted">Loading session…</p>
        </div>
      </div>
    )
  }

  return (
    <div className="section">
      <div className="container">
        <div className="section-head">
          <div>
            <p className="eyebrow">Operator dashboard</p>
            <h1>Collect jobs &amp; source health</h1>
            <p className="muted" style={{ marginBottom: 0 }}>
              Signed in as <strong>{session.username}</strong> ({session.role}). Paste-URL collect
              lands in PR08.
            </p>
          </div>
          <button type="button" className="btn btn-secondary" onClick={onLogout}>
            Sign out
          </button>
        </div>

        {error && <p className="error-box">{error}</p>}

        <div className="panel ops-card" style={{ marginBottom: '1.25rem' }}>
          <h2>Paste a careers URL</h2>
          <div className="ops-paste-bar">
            <input type="url" placeholder="https://…" disabled aria-disabled="true" />
            <input type="text" placeholder="Optional source label" disabled aria-disabled="true" />
            <button type="button" className="btn btn-primary" disabled>
              Submit
            </button>
          </div>
          <p className="muted small" style={{ margin: '0.65rem 0 0' }}>
            coming in PR08
          </p>
        </div>

        <div className="panel ops-card" style={{ marginBottom: '1.25rem' }}>
          <h2>Active collect jobs</h2>
          <div className="table-wrap" style={{ marginTop: '0.75rem' }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Job ID</th>
                  <th>Host</th>
                  <th>State</th>
                  <th>Started</th>
                  <th>Elapsed</th>
                </tr>
              </thead>
              <tbody>
                {jobs.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="muted">
                      No collect jobs yet. Paste-URL ingest is not in this PR.
                    </td>
                  </tr>
                ) : (
                  jobs.map((job) => (
                    <tr key={job.id || job.jobId}>
                      <td>
                        <Link to={`/ops/runs/${encodeURIComponent(job.id || job.jobId)}`}>
                          {job.id || job.jobId}
                        </Link>
                      </td>
                      <td>{job.host || '—'}</td>
                      <td>{job.state || '—'}</td>
                      <td>{formatDateTime(job.startedAt || job.createdAt)}</td>
                      <td>{job.elapsed || '—'}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

        <div className="stats-grid sources-summary" style={{ marginBottom: '1.25rem' }}>
          <div className="stat-card">
            <span className="stat-value">{health.total}</span>
            <span className="stat-label">Registry sources</span>
          </div>
          <div className="stat-card">
            <span className="stat-value">{health.enabled}</span>
            <span className="stat-label">Enabled</span>
          </div>
          <div className="stat-card">
            <span className="stat-value">{health.ok}</span>
            <span className="stat-label">Last scrape ok</span>
          </div>
          <div className="stat-card">
            <span className="stat-value">{health.failed}</span>
            <span className="stat-label">Last scrape error</span>
          </div>
        </div>

        <div className="panel ops-card" style={{ marginBottom: '1.25rem' }}>
          <h2>Source health</h2>
          <p className="muted small">
            From public <code>GET /api/sources</code>. Last collect:{' '}
            {formatDateTime(sourcesPayload.lastCollectAt)}
          </p>
          <div className="table-wrap" style={{ marginTop: '0.75rem' }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Source</th>
                  <th>Priority</th>
                  <th>Enabled</th>
                  <th>Last scrape</th>
                  <th>Note</th>
                </tr>
              </thead>
              <tbody>
                {sources.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="muted">
                      No registry sources loaded.
                    </td>
                  </tr>
                ) : (
                  sources.map((s) => {
                    const badge = scrapeBadge(s)
                    const err = s.lastScrape?.errors?.[0]?.message
                    return (
                      <tr key={s.sourceId}>
                        <td>
                          <strong>{s.name}</strong>
                          <div className="muted small">{s.sourceId}</div>
                        </td>
                        <td>{s.priority || '—'}</td>
                        <td>{s.enabled ? 'yes' : 'no'}</td>
                        <td>
                          <span className={badge.className}>{badge.text}</span>
                        </td>
                        <td className="muted small">{err || '—'}</td>
                      </tr>
                    )
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>

        {session.role === 'admin' && (
          <div className="panel ops-card" style={{ marginBottom: '1.25rem' }}>
            <h2>Add operator</h2>
            <form className="ops-paste-bar" onSubmit={onAddOperator}>
              <input
                name="new-username"
                placeholder="username"
                autoComplete="off"
                value={newUser}
                onChange={(e) => setNewUser(e.target.value)}
                required
              />
              <input
                name="new-password"
                type="password"
                placeholder="password (8+ chars)"
                autoComplete="new-password"
                value={newPass}
                onChange={(e) => setNewPass(e.target.value)}
                required
                minLength={8}
              />
              <button type="submit" className="btn btn-secondary" disabled={adding}>
                {adding ? 'Adding…' : 'Add'}
              </button>
            </form>
            {addMsg && <p className="muted small" style={{ margin: '0.65rem 0 0' }}>{addMsg}</p>}
          </div>
        )}

        <p className="muted small ops-legal">
          Rate-limited collectors. Metadata and official URLs only. PDFs stay in private raw staging
          and are never republished. Not affiliated with any board. Always verify on the official
          site.
        </p>
      </div>
    </div>
  )
}
