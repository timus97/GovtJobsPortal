import { useEffect, useState } from 'react'
import { fetchOpsLogs } from '../../api/ops'
import { formatDateTime } from '../../utils/labels'

const UNITS = [
  '',
  'http',
  'auth.student',
  'auth.ops',
  'match',
  'collect',
  'sources',
  'jobs',
  'student.store',
  'student.desk',
  'ops.review',
  'client.student',
  'client.ops',
  'sqlite',
]

export default function AdminLogsPage() {
  const [unit, setUnit] = useState('')
  const [role, setRole] = useState('')
  const [level, setLevel] = useState('')
  const [q, setQ] = useState('')
  const [data, setData] = useState({ items: [], total: 0 })
  const [error, setError] = useState('')

  async function load() {
    const body = await fetchOpsLogs({ unit, role, level, q, limit: 200 })
    setData(body)
  }

  useEffect(() => {
    load().catch((err) => setError(err.message || 'Failed to load logs'))
  }, [unit, role, level])

  return (
    <div className="section">
      <div className="container">
        <div className="section-head">
          <div>
            <p className="eyebrow">Admin · observability</p>
            <h1>Application logs</h1>
            <p className="muted" style={{ marginBottom: 0 }}>
              Structured events for students, operators, HTTP, match, collect, and sources. Passwords
              and profile bodies are never stored.
            </p>
          </div>
          <button type="button" className="btn btn-secondary" onClick={() => load().catch(() => {})}>
            Refresh
          </button>
        </div>
        {error && <p className="error-box">{error}</p>}
        <div className="ops-paste-bar" style={{ marginBottom: '1rem' }}>
          <select value={unit} onChange={(e) => setUnit(e.target.value)} aria-label="Unit">
            {UNITS.map((u) => (
              <option key={u || 'all'} value={u}>
                {u || 'All units'}
              </option>
            ))}
          </select>
          <select value={role} onChange={(e) => setRole(e.target.value)} aria-label="Role">
            <option value="">All roles</option>
            <option value="student">student</option>
            <option value="operator">operator</option>
            <option value="admin">admin</option>
            <option value="anon">anon</option>
            <option value="system">system</option>
          </select>
          <select value={level} onChange={(e) => setLevel(e.target.value)} aria-label="Level">
            <option value="">All levels</option>
            <option value="info">info</option>
            <option value="warn">warn</option>
            <option value="error">error</option>
          </select>
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search message / actor"
            onKeyDown={(e) => {
              if (e.key === 'Enter') load().catch((err) => setError(err.message))
            }}
          />
        </div>
        <p className="muted small">
          Showing {data.returned ?? data.items.length} of {data.total} buffered events
        </p>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Level</th>
                <th>Unit</th>
                <th>Role</th>
                <th>Actor</th>
                <th>Message</th>
              </tr>
            </thead>
            <tbody>
              {(data.items || []).length === 0 ? (
                <tr>
                  <td colSpan={6} className="muted">
                    No events yet. Use the app, then refresh.
                  </td>
                </tr>
              ) : (
                data.items.map((row) => (
                  <tr key={row.id}>
                    <td className="muted small">{formatDateTime(row.ts)}</td>
                    <td>
                      <span
                        className={
                          row.level === 'error'
                            ? 'badge badge-warn'
                            : row.level === 'warn'
                              ? 'badge badge-muted'
                              : 'badge badge-soft'
                        }
                      >
                        {row.level}
                      </span>
                    </td>
                    <td>{row.unit}</td>
                    <td>{row.role}</td>
                    <td>{row.actor || '—'}</td>
                    <td>
                      {row.message}
                      {row.action ? <div className="muted small">{row.action}</div> : null}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
