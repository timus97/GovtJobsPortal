import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { deleteDeskItem, getDeskItem, meAccount, patchDeskItem } from '../api/account'
import { STATUSES, STATUS_LABELS } from '../lib/deskGuidance'
import '../App.css'

export default function DeskDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [item, setItem] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function load() {
    const body = await getDeskItem(id)
    setItem(body.item)
  }

  useEffect(() => {
    let cancelled = false
    meAccount()
      .then(() => load())
      .catch((err) => {
        if (cancelled) return
        if (err.status === 401) navigate('/account/login', { replace: true })
        else setError(err.message)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, navigate])

  async function save(patch) {
    setBusy(true)
    setError('')
    try {
      const body = await patchDeskItem(id, patch)
      setItem(body.item)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function onRemove() {
    if (!window.confirm('Remove this item from your desk?')) return
    try {
      await deleteDeskItem(id)
      navigate('/dashboard', { replace: true })
    } catch (err) {
      setError(err.message)
    }
  }

  if (error && !item) {
    return (
      <div className="section container">
        <p className="error-box">{error}</p>
        <Link to="/dashboard">← Back to desk</Link>
      </div>
    )
  }

  if (!item) {
    return (
      <div className="section container">
        <p className="muted">Loading desk item…</p>
      </div>
    )
  }

  return (
    <div className="section">
      <div className="container desk-detail">
        <p>
          <Link to="/dashboard" className="back-link">
            ← Back to dashboard
          </Link>
        </p>
        {error && <p className="error-box">{error}</p>}

        <div className="desk-card-top">
          <div>
            <div className="badge-row">
              <span className="badge badge-soft">{item.kindLabel}</span>
              <span className="badge badge-ok">{item.statusLabel}</span>
              {item.board && <span className="badge badge-exam">{item.board}</span>}
            </div>
            <h1>{item.title}</h1>
            <p className="muted">
              Not an official eligibility decision. Verify dates on the official site.
            </p>
          </div>
          <div className="desk-days">
            <strong>{item.daysNumber}</strong>
            <span>{item.daysCaption}</span>
          </div>
        </div>

        <div className="match-banner" role="note">
          {item.nextStep}
        </div>

        <div className="detail-grid">
          <section className="panel">
            <h2>Status &amp; dates</h2>
            <div className="chip-row">
              {STATUSES.map((s) => (
                <button
                  key={s}
                  type="button"
                  className={`chip ${item.status === s ? 'chip-pass' : ''}`}
                  disabled={busy}
                  onClick={() => save({ status: s })}
                >
                  {STATUS_LABELS[s]}
                </button>
              ))}
            </div>
            <label className="field">
              Exam date
              <input
                type="date"
                value={item.examDate || ''}
                onChange={(e) => save({ examDate: e.target.value || null })}
              />
            </label>
            <label className="field">
              Last date / apply
              <input
                type="date"
                value={item.lastDate || ''}
                onChange={(e) => save({ lastDate: e.target.value || null })}
              />
            </label>
            {item.officialUrl && (
              <p>
                <a href={item.officialUrl} target="_blank" rel="noopener noreferrer">
                  Official site
                </a>
              </p>
            )}
            <button type="button" className="btn btn-secondary" onClick={onRemove}>
              Remove from desk
            </button>
          </section>

          <section className="panel">
            <h2>Private documents</h2>
            <p className="muted">
              Admit card and result uploads ship in the next step. Files will stay private on this
              API host.
            </p>
            <div className="desk-file-grid">
              <div className="desk-file">
                <strong>Admit card</strong>
                <p className="muted small">{item.hasAdmit ? 'On file' : 'Not uploaded yet'}</p>
              </div>
              <div className="desk-file">
                <strong>Result</strong>
                <p className="muted small">{item.hasResult ? 'On file' : 'Not uploaded yet'}</p>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  )
}
