import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { meAccount } from '../api/account'
import { getMockAttempt } from '../api/mocks'
import './mock.css'

const LETTERS = ['A', 'B', 'C', 'D']

function labelChoice(choices, idx) {
  if (idx == null || idx < 0) return 'Not answered'
  const letter = LETTERS[idx] || String(idx + 1)
  const text = Array.isArray(choices) ? choices[idx] : null
  return text == null ? letter : `${letter}. ${text}`
}

export default function MockReviewPage() {
  const { id, attemptId } = useParams()
  const navigate = useNavigate()
  const [payload, setPayload] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    meAccount()
      .then(() => getMockAttempt(attemptId))
      .then((body) => {
        if (!cancelled) setPayload(body)
      })
      .catch((err) => {
        if (cancelled) return
        if (err.status === 401) navigate('/account/login', { replace: true })
        else setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [attemptId, navigate])

  if (error && !payload) {
    return (
      <div className="section container mock-page">
        <p className="error-box">{error}</p>
        <Link to={`/desk/${id}`}>← Back to desk</Link>
      </div>
    )
  }

  if (!payload) {
    return (
      <div className="section container mock-page">
        <p className="muted">Loading review…</p>
      </div>
    )
  }

  const attempt = payload.attempt
  const review = payload.review || []

  if (!attempt?.submittedAt) {
    return (
      <div className="section container mock-page">
        <p className="muted">This unofficial mock has not been submitted yet.</p>
        <div className="mock-actions">
          <Link to={`/desk/${id}`} className="btn btn-secondary">
            Back to desk
          </Link>
          <Link to={`/desk/${id}/mock`} className="btn btn-primary">
            Start unofficial mock
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="section">
      <div className="container mock-page">
        <p>
          <Link to={`/desk/${id}`} className="back-link">
            ← Back to desk
          </Link>
        </p>
        <p className="mock-disclaimer" role="note">
          Unofficial practice questions — not an official paper. Verify on the official website.
        </p>
        <p className="eyebrow">Unofficial mock review</p>
        <h1 className="mock-score">
          {attempt.score} / {attempt.total}
        </h1>
        <p className="muted">Score on this unofficial practice set. This is not an official result.</p>

        <div className="mock-review-list">
          {review.map((row, i) => (
            <section key={row.id} className="panel mock-review-item">
              <div className="badge-row">
                <span className={`chip ${row.ok ? 'chip-pass' : 'chip-fail'}`}>
                  {row.ok ? 'Correct' : 'Incorrect'}
                </span>
                <span className="chip">Q{i + 1}</span>
              </div>
              <h3>{row.stem}</h3>
              <p className="mock-review-meta">Your answer: {labelChoice(row.choices, row.chosen)}</p>
              <p className="mock-review-meta">Correct: {labelChoice(row.choices, row.answerIndex)}</p>
              {row.explain && <p className="mock-review-explain">{row.explain}</p>}
            </section>
          ))}
        </div>

        <div className="mock-actions">
          <Link to={`/desk/${id}`} className="btn btn-secondary">
            Back to desk
          </Link>
          <Link to={`/desk/${id}/mock`} className="btn btn-primary">
            Retry
          </Link>
        </div>
      </div>
    </div>
  )
}
