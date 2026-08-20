import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  deleteDeskFile,
  deleteDeskItem,
  downloadDeskFile,
  getDeskItem,
  meAccount,
  patchDeskItem,
  uploadDeskFile,
} from '../api/account'
import { STATUSES, STATUS_LABELS } from '../lib/deskGuidance'
import '../App.css'

const MAX_BYTES = 5 * 1024 * 1024

function formatBytes(n) {
  if (n == null) return ''
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

function FileTile({ label, kind, file, busy, onUpload, onDownload, onRemove }) {
  const inputId = `desk-file-${kind}`
  return (
    <div className="desk-file">
      <strong>{label}</strong>
      {file ? (
        <p className="muted small">
          {file.originalName} · {formatBytes(file.bytes)}
        </p>
      ) : (
        <p className="muted small">Not uploaded yet · PDF, JPEG, or PNG · 5 MB max</p>
      )}
      <input
        id={inputId}
        type="file"
        accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
        disabled={busy}
        onChange={(e) => {
          const chosen = e.target.files && e.target.files[0]
          e.target.value = ''
          if (chosen) onUpload(kind, chosen)
        }}
      />
      <div className="desk-file-actions">
        <label
          htmlFor={inputId}
          className={`btn btn-secondary ${busy ? 'is-disabled' : ''}`}
          aria-label={file ? `Replace ${label.toLowerCase()}` : `Upload ${label.toLowerCase()}`}
        >
          {file ? 'Replace' : 'Upload'}
        </label>
        {file && (
          <button
            type="button"
            className="btn btn-secondary"
            disabled={busy}
            onClick={() => onDownload(kind, file)}
            aria-label={`Download ${label.toLowerCase()}`}
          >
            Download
          </button>
        )}
        {file && (
          <button
            type="button"
            className="btn btn-secondary"
            disabled={busy}
            onClick={() => onRemove(kind)}
            aria-label={`Remove ${label.toLowerCase()}`}
          >
            Remove
          </button>
        )}
      </div>
    </div>
  )
}

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

  async function onUpload(kind, file) {
    if (file.size > MAX_BYTES) {
      setError('File must be 5 MB or smaller')
      return
    }
    setBusy(true)
    setError('')
    try {
      const body = await uploadDeskFile(id, kind, file)
      setItem(body.item)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function onDownload(kind, file) {
    setError('')
    try {
      await downloadDeskFile(id, kind, file && file.originalName)
    } catch (err) {
      setError(err.message)
    }
  }

  async function onRemoveFile(kind) {
    setBusy(true)
    setError('')
    try {
      const body = await deleteDeskFile(id, kind)
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
              Admit card and result stay on this API host. Only you can download them. Never published
              with the jobs catalog.
            </p>
            <div className="desk-file-grid">
              <FileTile
                label="Admit card"
                kind="admit"
                file={item.admitFile}
                busy={busy}
                onUpload={onUpload}
                onDownload={onDownload}
                onRemove={onRemoveFile}
              />
              <FileTile
                label="Result"
                kind="result"
                file={item.resultFile}
                busy={busy}
                onUpload={onUpload}
                onDownload={onDownload}
                onRemove={onRemoveFile}
              />
            </div>
            {item.kind === 'series' ? (
              <p>
                <Link to={`/desk/${item.id}/plan`} className="btn btn-primary">
                  Open study plan
                </Link>
              </p>
            ) : (
              <p className="muted">Track a calendar from Prepare to get an unofficial syllabus.</p>
            )}
          </section>
        </div>
      </div>
    </div>
  )
}
