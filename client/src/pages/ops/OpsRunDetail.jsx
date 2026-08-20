import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  cancelOpsJob,
  getOpsJob,
  me,
  patchReview,
  probeOpsApi,
  publishReview,
  rejectReview,
} from '../../api/ops'
import { formatDateTime } from '../../utils/labels'
import OpsRequiresApi from './OpsRequiresApi'

const EDIT_FIELDS = [
  ['title', 'Title'],
  ['organization', 'Organization'],
  ['officialUrl', 'Official URL'],
  ['lastDate', 'Last date (YYYY-MM-DD)'],
  ['selectionProcess', 'Selection process'],
  ['summary', 'Summary'],
]

export default function OpsRunDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [apiOk, setApiOk] = useState(null)
  const [job, setJob] = useState(null)
  const [error, setError] = useState('')
  const [form, setForm] = useState({})
  const [hasExam, setHasExam] = useState(false)
  const [rejectReason, setRejectReason] = useState('')
  const [busy, setBusy] = useState('')

  async function load() {
    const next = await getOpsJob(id)
    setJob(next)
    const extracted = next.extracted || {}
    const nextForm = {}
    for (const [key] of EDIT_FIELDS) nextForm[key] = extracted[key] || ''
    setForm(nextForm)
    setHasExam(extracted.hasExam === true)
  }

  useEffect(() => {
    let cancelled = false
    async function boot() {
      const ok = await probeOpsApi()
      if (cancelled) return
      setApiOk(ok)
      if (!ok) return
      try {
        await me()
        await load()
      } catch (err) {
        if (cancelled) return
        if (err.status === 401) {
          navigate('/ops/login', { replace: true })
          return
        }
        setError(err.message || 'Failed to load collect job')
      }
    }
    boot()
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, navigate])

  async function run(label, fn) {
    setBusy(label)
    setError('')
    try {
      await fn()
      await load()
    } catch (err) {
      setError(err.message || `${label} failed`)
    } finally {
      setBusy('')
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
  if (!job) {
    return (
      <div className="section">
        <div className="container">
          <p>
            <Link to="/ops" className="back-link">
              ← Back to ops
            </Link>
          </p>
          {error ? <p className="error-box">{error}</p> : <p className="muted">Loading…</p>}
        </div>
      </div>
    )
  }

  const canReview = ['needs_review', 'valid', 'invalid', 'extracted'].includes(job.state)
  const canPublish = ['needs_review', 'valid', 'published_local'].includes(job.state)
  const canCancel = job.state === 'pending'

  return (
    <div className="section">
      <div className="container">
        <p>
          <Link to="/ops" className="back-link">
            ← Back to ops
          </Link>
          {' · '}
          <Link to="/ops/review">Review queue</Link>
        </p>
        <div className="section-head">
          <div>
            <p className="eyebrow">Collect job</p>
            <h1>{job.extracted?.title || job.host}</h1>
            <p className="muted">
              {job.state}
              {job.reason ? ` · ${job.reason}` : ''}
            </p>
          </div>
        </div>
        {error && <p className="error-box">{error}</p>}
        {job.state === 'published_local' && (
          <div className="match-banner" role="note">
            Not in git SoR until the staging file is committed.
          </div>
        )}

        <div className="panel ops-card" style={{ marginBottom: '1.25rem' }}>
          <h2>Source</h2>
          <p>
            <a href={job.url} target="_blank" rel="noopener noreferrer">
              {job.url}
            </a>
          </p>
          <p className="muted small">
            Host {job.host} · created {formatDateTime(job.createdAt)}
          </p>
        </div>

        {canReview && (
          <form
            className="panel ops-card"
            style={{ marginBottom: '1.25rem' }}
            onSubmit={(e) => {
              e.preventDefault()
              run('save', () =>
                patchReview(job.id, {
                  ...form,
                  hasExam,
                  officialUrl: form.officialUrl,
                })
              )
            }}
          >
            <h2>Edit facts</h2>
            {EDIT_FIELDS.map(([key, label]) => (
              <label key={key} className="field">
                {label}
                <input
                  value={form[key] || ''}
                  onChange={(e) => setForm((prev) => ({ ...prev, [key]: e.target.value }))}
                />
              </label>
            ))}
            <label className="check-inline">
              <input
                type="checkbox"
                checked={hasExam}
                onChange={(e) => setHasExam(e.target.checked)}
              />
              Has written exam
            </label>
            <div className="hero-actions" style={{ marginTop: '1rem' }}>
              <button type="submit" className="btn btn-secondary" disabled={Boolean(busy)}>
                Save facts
              </button>
            </div>
          </form>
        )}

        <div className="panel ops-card" style={{ marginBottom: '1.25rem' }}>
          <h2>Actions</h2>
          <div className="hero-actions">
            {canCancel && (
              <button
                type="button"
                className="btn btn-secondary"
                disabled={Boolean(busy)}
                onClick={() => run('cancel', () => cancelOpsJob(job.id))}
              >
                Cancel
              </button>
            )}
            {canPublish && (
              <button
                type="button"
                className="btn btn-primary"
                disabled={Boolean(busy)}
                onClick={() => run('publish', () => publishReview(job.id))}
              >
                Publish
              </button>
            )}
          </div>
          <div className="ops-paste-bar" style={{ marginTop: '1rem' }}>
            <input
              placeholder="Reject reason (required)"
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
            />
            <button
              type="button"
              className="btn btn-secondary"
              disabled={Boolean(busy) || !rejectReason.trim()}
              onClick={() => run('reject', () => rejectReview(job.id, rejectReason))}
            >
              Reject
            </button>
          </div>
        </div>

        <div className="panel ops-card" style={{ marginBottom: '1.25rem' }}>
          <h2>Timeline</h2>
          <ul>
            {(job.timeline || []).map((ev, i) => (
              <li key={`${ev.at}-${i}`}>
                <strong>{ev.state}</strong> · {formatDateTime(ev.at)}
                {ev.detail ? ` — ${ev.detail}` : ''}
              </li>
            ))}
          </ul>
        </div>

        {job.extracted && (
          <div className="panel ops-card">
            <h2>Extracted JSON</h2>
            <p className="muted small">
              Aggregator only — always verify and apply on the official website.
            </p>
            <pre className="ops-json">{JSON.stringify(job.extracted, null, 2)}</pre>
          </div>
        )}
      </div>
    </div>
  )
}
