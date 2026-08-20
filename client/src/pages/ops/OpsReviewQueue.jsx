import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { listReview, me, probeOpsApi } from '../../api/ops'
import { formatDateTime } from '../../utils/labels'
import OpsRequiresApi from './OpsRequiresApi'

export default function OpsReviewQueue() {
  const navigate = useNavigate()
  const [apiOk, setApiOk] = useState(null)
  const [items, setItems] = useState([])
  const [counts, setCounts] = useState({ needs_review: 0, valid: 0 })
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    async function boot() {
      const ok = await probeOpsApi()
      if (cancelled) return
      setApiOk(ok)
      if (!ok) return
      try {
        await me()
        const payload = await listReview()
        if (cancelled) return
        setItems(payload.items || [])
        setCounts(payload.counts || { needs_review: 0, valid: 0 })
      } catch (err) {
        if (cancelled) return
        if (err.status === 401) {
          navigate('/ops/login', { replace: true })
          return
        }
        setError(err.message || 'Failed to load review queue')
      }
    }
    boot()
    return () => {
      cancelled = true
    }
  }, [navigate])

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

  return (
    <div className="section">
      <div className="container">
        <p>
          <Link to="/ops" className="back-link">
            ← Back to ops
          </Link>
        </p>
        <div className="section-head">
          <div>
            <p className="eyebrow">Review queue</p>
            <h1>Needs review, then valid</h1>
            <p className="muted">
              {counts.needs_review} need review · {counts.valid} valid. Publish writes staging
              only — never daily pipeline.
            </p>
          </div>
        </div>
        {error && <p className="error-box">{error}</p>}
        <div className="panel ops-card">
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Job</th>
                  <th>Title</th>
                  <th>Host</th>
                  <th>State</th>
                  <th>Updated</th>
                </tr>
              </thead>
              <tbody>
                {items.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="muted">
                      Nothing waiting for review.
                    </td>
                  </tr>
                ) : (
                  items.map((job) => (
                    <tr key={job.id}>
                      <td>
                        <Link to={`/ops/runs/${encodeURIComponent(job.id)}`}>{job.id.slice(0, 8)}</Link>
                      </td>
                      <td>{job.extracted?.title || '—'}</td>
                      <td>{job.host}</td>
                      <td>{job.state}</td>
                      <td>{formatDateTime(job.updatedAt)}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
        <p className="muted small ops-legal">
          Rate-limited. Metadata and official URLs only. Not an official eligibility decision.
        </p>
      </div>
    </div>
  )
}
