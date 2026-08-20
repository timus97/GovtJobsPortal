import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getDeskItem, meAccount } from '../api/account'
import { startMockAttempt, submitMockAttempt } from '../api/mocks'
import './mock.css'

const LETTERS = ['A', 'B', 'C', 'D']

function remainingMs(startedAt, durationMin) {
  const start = new Date(startedAt).getTime()
  if (!Number.isFinite(start)) return 0
  const total = Math.max(1, Number(durationMin) || 20) * 60 * 1000
  return Math.max(0, start + total - Date.now())
}

function formatClock(ms) {
  const total = Math.ceil(ms / 1000)
  const m = Math.floor(total / 60)
  const s = total % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

export default function MockPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [item, setItem] = useState(null)
  const [attempt, setAttempt] = useState(null)
  const [bank, setBank] = useState(null)
  const [durationMin, setDurationMin] = useState(20)
  const [answers, setAnswers] = useState({})
  const [index, setIndex] = useState(0)
  const [leftMs, setLeftMs] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const submittedRef = useRef(false)
  const answersRef = useRef(answers)

  useEffect(() => {
    answersRef.current = answers
  }, [answers])

  useEffect(() => {
    let cancelled = false
    meAccount()
      .then(() => getDeskItem(id))
      .then(async (body) => {
        if (cancelled) return
        const nextItem = body.item
        setItem(nextItem)
        if (!nextItem || nextItem.kind !== 'series' || !nextItem.refId) {
          setError('Unofficial mocks are available only for tracked exam calendars.')
          return
        }
        const started = await startMockAttempt(nextItem.refId, nextItem.id)
        if (cancelled) return
        setAttempt(started.attempt)
        setBank(started.bank)
        setDurationMin(started.durationMin || started.bank?.durationMin || 20)
        setLeftMs(remainingMs(started.attempt.startedAt, started.durationMin || 20))
      })
      .catch((err) => {
        if (cancelled) return
        if (err.status === 401) navigate('/account/login', { replace: true })
        else if (err.status === 404) setError('No unofficial mock for this exam yet.')
        else setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [id, navigate])

  useEffect(() => {
    if (!attempt || attempt.submittedAt) return undefined
    const tick = () => setLeftMs(remainingMs(attempt.startedAt, durationMin))
    tick()
    const timer = setInterval(tick, 500)
    return () => clearInterval(timer)
  }, [attempt, durationMin])

  async function finish() {
    if (!attempt || submittedRef.current) return
    submittedRef.current = true
    setBusy(true)
    setError('')
    try {
      await submitMockAttempt(attempt.id, answersRef.current)
      navigate(`/desk/${id}/mock/${attempt.id}`, { replace: true })
    } catch (err) {
      if (err.status === 409) {
        navigate(`/desk/${id}/mock/${attempt.id}`, { replace: true })
        return
      }
      submittedRef.current = false
      setError(err.message)
      setBusy(false)
    }
  }

  useEffect(() => {
    if (leftMs === 0 && attempt && !attempt.submittedAt) finish()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [leftMs, attempt])

  if (error && !bank) {
    return (
      <div className="section container mock-page">
        <p className="error-box">{error}</p>
        <Link to={`/desk/${id}`}>← Back to desk</Link>
      </div>
    )
  }

  if (!item || !bank || !attempt) {
    return (
      <div className="section container mock-page">
        <p className="muted">Starting unofficial mock…</p>
      </div>
    )
  }

  const questions = bank.questions || []
  const question = questions[index]
  const low = leftMs != null && leftMs <= 60 * 1000

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
        {error && <p className="error-box">{error}</p>}

        <div className="mock-top">
          <div>
            <p className="eyebrow">Unofficial mock</p>
            <h1>{item.title}</h1>
          </div>
          <span className={`chip mock-timer ${low ? 'is-low' : ''}`} aria-live="polite">
            {leftMs == null ? '--:--' : formatClock(leftMs)}
          </span>
        </div>

        {question ? (
          <section className="panel">
            <p className="mock-progress">
              Question {index + 1} of {questions.length}
            </p>
            <p className="mock-stem">{question.stem}</p>
            <div className="mock-choices" role="group" aria-label="Answer choices">
              {(question.choices || []).map((choice, i) => (
                <button
                  key={`${question.id}-${i}`}
                  type="button"
                  className={`mock-choice ${answers[question.id] === i ? 'is-selected' : ''}`}
                  onClick={() => setAnswers((prev) => ({ ...prev, [question.id]: i }))}
                  disabled={busy}
                >
                  <span className="mock-letter">{LETTERS[i] || i + 1}</span>
                  <span>{choice}</span>
                </button>
              ))}
            </div>
            <div className="mock-nav">
              <button
                type="button"
                className="btn btn-secondary"
                disabled={busy || index === 0}
                onClick={() => setIndex((n) => Math.max(0, n - 1))}
              >
                Previous
              </button>
              <button
                type="button"
                className="btn btn-secondary"
                disabled={busy || index >= questions.length - 1}
                onClick={() => setIndex((n) => Math.min(questions.length - 1, n + 1))}
              >
                Next
              </button>
              <button type="button" className="btn btn-primary" disabled={busy} onClick={finish}>
                Submit
              </button>
            </div>
          </section>
        ) : (
          <p className="muted">No questions in this unofficial bank.</p>
        )}
      </div>
    </div>
  )
}
