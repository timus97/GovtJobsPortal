import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { createDeskItem, listDeskItems, meAccount } from '../api/account'
import { isStudentEnabled } from '../lib/features'
import '../App.css'

const FILTERS = [
  { id: '', label: 'All' },
  { id: 'watching', label: 'Watching' },
  { id: 'applied', label: 'Applied' },
  { id: 'admit_ready', label: 'Admit ready' },
  { id: 'appeared', label: 'Appeared' },
]

function statusTone(status) {
  if (status === 'admit_ready' || status === 'done') return 'badge-ok'
  if (status === 'applied') return 'badge-warn'
  return 'badge-soft'
}

export default function DashboardPage() {
  const navigate = useNavigate()
  const studentOn = isStudentEnabled()
  const [student, setStudent] = useState(null)
  const [status, setStatus] = useState('')
  const [payload, setPayload] = useState({ items: [], stats: {} })
  const [error, setError] = useState('')
  const [showCustom, setShowCustom] = useState(false)
  const [custom, setCustom] = useState({ title: '', board: '', examDate: '', officialUrl: '' })
  const [busy, setBusy] = useState(false)

  async function refresh(nextStatus = status) {
    const body = await listDeskItems(nextStatus)
    setPayload(body)
  }

  useEffect(() => {
    if (!studentOn) return undefined
    let cancelled = false
    meAccount()
      .then(async (body) => {
        if (cancelled) return
        setStudent(body.student)
        await refresh('')
      })
      .catch((err) => {
        if (cancelled) return
        if (err.status === 401) navigate('/account/login', { replace: true })
        else setError(err.message)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [studentOn, navigate])

  async function onFilter(id) {
    setStatus(id)
    setError('')
    try {
      await refresh(id)
    } catch (err) {
      setError(err.message)
    }
  }

  async function onCustom(e) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await createDeskItem({
        kind: 'custom',
        title: custom.title,
        board: custom.board,
        examDate: custom.examDate,
        officialUrl: custom.officialUrl,
      })
      setCustom({ title: '', board: '', examDate: '', officialUrl: '' })
      setShowCustom(false)
      await refresh(status)
    } catch (err) {
      setError(err.message || 'Could not add exam')
    } finally {
      setBusy(false)
    }
  }

  if (!studentOn) {
    return (
      <div className="section container">
        <p className="muted">The exam desk needs the API host.</p>
      </div>
    )
  }

  const stats = payload.stats || {}

  return (
    <div className="section">
      <div className="container">
        <div className="match-banner" role="note">
          Track calendars, jobs you applied for, and custom exams. Days-left uses the exam date you
          set — we never invent dates. Not an official eligibility decision.
        </div>

        <div className="section-head">
          <div>
            <p className="eyebrow">Student desk</p>
            <h1>Your exam desk</h1>
            <p className="muted">
              {student ? `Signed in as ${student.email}.` : 'Loading…'} Track from Prepare or a job,
              or add an exam that is not in the catalog yet.
            </p>
          </div>
          <div className="hero-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setShowCustom((v) => !v)}>
              Add custom exam
            </button>
            <Link to="/prepare" className="btn btn-primary">
              Browse prepare
            </Link>
          </div>
        </div>

        {error && <p className="error-box">{error}</p>}

        {showCustom && (
          <form className="panel desk-custom" onSubmit={onCustom}>
            <h2>Add a custom exam</h2>
            <p className="muted small">A date is required for the countdown.</p>
            <label className="field">
              Exam name *
              <input
                required
                value={custom.title}
                onChange={(e) => setCustom((p) => ({ ...p, title: e.target.value }))}
              />
            </label>
            <label className="field">
              Board / org
              <input
                value={custom.board}
                onChange={(e) => setCustom((p) => ({ ...p, board: e.target.value }))}
              />
            </label>
            <label className="field">
              Exam date *
              <input
                type="date"
                required
                value={custom.examDate}
                onChange={(e) => setCustom((p) => ({ ...p, examDate: e.target.value }))}
              />
            </label>
            <label className="field">
              Official URL (https)
              <input
                value={custom.officialUrl}
                onChange={(e) => setCustom((p) => ({ ...p, officialUrl: e.target.value }))}
                placeholder="https://"
              />
            </label>
            <div className="hero-actions">
              <button type="button" className="btn btn-secondary" onClick={() => setShowCustom(false)}>
                Cancel
              </button>
              <button type="submit" className="btn btn-primary" disabled={busy}>
                Add to desk
              </button>
            </div>
          </form>
        )}

        <div className="desk-stats">
          <div className="panel desk-stat">
            <strong>{stats.upcoming ?? 0}</strong>
            <span>Upcoming exams</span>
          </div>
          <div className="panel desk-stat">
            <strong>{stats.admitPending ?? 0}</strong>
            <span>Applied, admit pending</span>
          </div>
          <div className="panel desk-stat">
            <strong>{stats.nearestDays == null ? '—' : stats.nearestDays}</strong>
            <span>Days to nearest date</span>
          </div>
          <div className="panel desk-stat">
            <strong>{stats.mocksCompleted ?? 0}</strong>
            <span>Mocks completed</span>
          </div>
        </div>

        <div className="chip-row desk-filters">
          {FILTERS.map((f) => (
            <button
              key={f.id || 'all'}
              type="button"
              className={`chip ${status === f.id ? 'chip-pass' : ''}`}
              onClick={() => onFilter(f.id)}
            >
              {f.label}
            </button>
          ))}
        </div>

        {payload.items.length === 0 && (
          <div className="panel empty">
            <h2>Nothing tracked yet</h2>
            <p className="muted">
              Track a calendar from Prepare, mark I applied on a job, or add a custom exam with a
              date.
            </p>
            <div className="hero-actions">
              <Link to="/prepare" className="btn btn-primary">
                Open Prepare
              </Link>
              <button type="button" className="btn btn-secondary" onClick={() => setShowCustom(true)}>
                Add custom exam
              </button>
            </div>
          </div>
        )}

        <div className="desk-list">
          {payload.items.map((item) => (
            <article key={item.id} className="panel desk-card">
              <div className="desk-card-top">
                <div>
                  <div className="badge-row">
                    <span className="badge badge-soft">{item.kindLabel}</span>
                    <span className={`badge ${statusTone(item.status)}`}>{item.statusLabel}</span>
                    {item.board && <span className="badge badge-exam">{item.board}</span>}
                  </div>
                  <h2 className="job-title">
                    <Link to={`/desk/${item.id}`}>{item.title}</Link>
                  </h2>
                  <p className="muted">{item.nextStep}</p>
                </div>
                <div className="desk-days">
                  <strong>{item.daysNumber}</strong>
                  <span>{item.daysCaption}</span>
                </div>
              </div>
              <div className="hero-actions">
                <Link to={`/desk/${item.id}`} className="btn btn-primary">
                  Open desk
                </Link>
                {item.officialUrl && (
                  <a
                    href={item.officialUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="btn btn-secondary"
                  >
                    Official site
                  </a>
                )}
              </div>
            </article>
          ))}
        </div>
      </div>
    </div>
  )
}
