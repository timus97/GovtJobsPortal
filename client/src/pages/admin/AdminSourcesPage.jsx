import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { collectSource, listOpsSources, patchSource } from '../../api/ops'
import { formatDateTime } from '../../utils/labels'

function scrapeBadge(source) {
  if (!source.enabled) return { text: 'Paused', className: 'badge badge-muted' }
  const run = source.lastScrape
  if (!run) return { text: 'No last scrape', className: 'badge badge-muted' }
  if (!run.ok) return { text: 'Error', className: 'badge badge-warn' }
  return { text: 'Ok', className: 'badge badge-soft' }
}

export default function AdminSourcesPage() {
  const navigate = useNavigate()
  const [payload, setPayload] = useState({ sources: [] })
  const [q, setQ] = useState('')
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState('')

  async function load() {
    const data = await listOpsSources()
    setPayload(data || { sources: [] })
  }

  useEffect(() => {
    load().catch((err) => setError(err.message || 'Failed to load sources'))
  }, [])

  const sources = useMemo(() => {
    const term = q.trim().toLowerCase()
    const list = payload.sources || []
    if (!term) return list
    return list.filter(
      (s) =>
        String(s.name).toLowerCase().includes(term) ||
        String(s.sourceId).toLowerCase().includes(term) ||
        String(s.category).toLowerCase().includes(term)
    )
  }, [payload.sources, q])

  async function onToggle(source) {
    setBusyId(source.sourceId)
    setError('')
    try {
      await patchSource(source.sourceId, { enabled: !source.enabled })
      await load()
    } catch (err) {
      setError(err.message || 'Could not update source')
    } finally {
      setBusyId('')
    }
  }

  async function onCollect(source) {
    setBusyId(source.sourceId)
    setError('')
    try {
      const result = await collectSource(source.sourceId)
      navigate(`/ops/runs/${encodeURIComponent(result.jobId)}`)
    } catch (err) {
      setError(err.message || 'Could not queue collect')
      setBusyId('')
    }
  }

  return (
    <div className="section">
      <div className="container">
        <div className="section-head">
          <div>
            <p className="eyebrow">Admin · registry</p>
            <h1>Sources</h1>
            <p className="muted" style={{ marginBottom: 0 }}>
              Edit collector registry rows. Enable/disable, then queue a collect into the same
              review pipeline as paste-URL.
            </p>
          </div>
          <Link to="/ops/sources/new" className="btn btn-primary">
            Add source
          </Link>
        </div>
        {error && <p className="error-box">{error}</p>}
        <label className="field" style={{ maxWidth: '24rem' }}>
          <span>Filter</span>
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Name, id, category" />
        </label>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Source</th>
                <th>Priority</th>
                <th>Method</th>
                <th>Enabled</th>
                <th>Last scrape</th>
                <th>Jobs</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {sources.length === 0 ? (
                <tr>
                  <td colSpan={7} className="muted">
                    No sources match.
                  </td>
                </tr>
              ) : (
                sources.map((s) => {
                  const badge = scrapeBadge(s)
                  return (
                    <tr key={s.sourceId}>
                      <td>
                        <Link to={`/ops/sources/${encodeURIComponent(s.sourceId)}`}>
                          <strong>{s.name}</strong>
                        </Link>
                        <div className="muted small">{s.sourceId}</div>
                      </td>
                      <td>{s.priority || '—'}</td>
                      <td>{s.method || '—'}</td>
                      <td>{s.enabled ? 'yes' : 'no'}</td>
                      <td>
                        <span className={badge.className}>{badge.text}</span>
                      </td>
                      <td>{s.publishedJobs ?? 0}</td>
                      <td>
                        <div className="hero-actions" style={{ margin: 0 }}>
                          <Link
                            className="btn btn-secondary"
                            to={`/ops/sources/${encodeURIComponent(s.sourceId)}`}
                          >
                            Edit
                          </Link>
                          <button
                            type="button"
                            className="btn btn-secondary"
                            disabled={busyId === s.sourceId}
                            onClick={() => onToggle(s)}
                          >
                            {s.enabled ? 'Pause' : 'Enable'}
                          </button>
                          <button
                            type="button"
                            className="btn btn-primary"
                            disabled={busyId === s.sourceId || s.method === 'manual' || !s.enabled}
                            onClick={() => onCollect(s)}
                          >
                            Collect
                          </button>
                        </div>
                      </td>
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>
        <p className="muted small">Last collect: {formatDateTime(payload.lastCollectAt)}</p>
      </div>
    </div>
  )
}
