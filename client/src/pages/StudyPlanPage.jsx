import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getDeskItem, meAccount } from '../api/account'
import { getPlan, setTopicDone } from '../api/coaching'
import '../App.css'
import './studyPlan.css'

function dateLabel(topic) {
  if (topic.startDate && topic.endDate) {
    return topic.startDate === topic.endDate
      ? topic.startDate
      : `${topic.startDate} – ${topic.endDate}`
  }
  return 'Add exam date'
}

export default function StudyPlanPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [item, setItem] = useState(null)
  const [plan, setPlan] = useState(null)
  const [progress, setProgress] = useState({})
  const [noPack, setNoPack] = useState(false)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState('')

  useEffect(() => {
    let cancelled = false
    meAccount()
      .then(() => getDeskItem(id))
      .then(async (body) => {
        if (cancelled) return
        const deskItem = body.item
        setItem(deskItem)
        if (deskItem.kind !== 'series' || !deskItem.refId) return
        try {
          const payload = await getPlan(deskItem.refId, deskItem.id)
          if (cancelled) return
          setPlan(payload.plan)
          setProgress(payload.progress || {})
        } catch (err) {
          if (cancelled) return
          if (err.status === 404) setNoPack(true)
          else setError(err.message)
        }
      })
      .catch((err) => {
        if (cancelled) return
        if (err.status === 401) navigate('/account/login', { replace: true })
        else setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [id, navigate])

  async function onToggle(topicId, done) {
    if (!item || !item.refId) return
    const prev = progress
    setError('')
    setBusyId(topicId)
    const next = { ...progress }
    if (done) next[topicId] = new Date().toISOString()
    else delete next[topicId]
    setProgress(next)
    try {
      const body = await setTopicDone(item.refId, topicId, done)
      setProgress(body.progress || {})
      if (body.plan) setPlan(body.plan)
    } catch (err) {
      setProgress(prev)
      setError(err.message)
    } finally {
      setBusyId('')
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
        <p className="muted">Loading study plan…</p>
      </div>
    )
  }

  const topics = (plan && plan.topics) || []
  const doneCount = topics.filter((t) => progress[t.id]).length
  const total = topics.length
  const pct = total ? Math.round((doneCount / total) * 100) : 0
  const notSeries = item.kind !== 'series'

  return (
    <div className="section">
      <div className="container plan-page">
        <p>
          <Link to={`/desk/${item.id}`} className="back-link">
            ← Back to desk
          </Link>
        </p>
        {error && <p className="error-box">{error}</p>}

        <div className="desk-card-top">
          <div>
            <div className="badge-row">
              <span className="badge badge-soft">{item.kindLabel}</span>
              {item.board && <span className="badge badge-exam">{item.board}</span>}
              <span className="badge badge-muted">Unofficial</span>
            </div>
            <h1>{item.title}</h1>
            <p className="muted">Study plan</p>
          </div>
        </div>

        <div className="match-banner" role="note">
          Unofficial topic list — not an official board syllabus. Verify on the official website.
        </div>

        {notSeries && (
          <section className="panel">
            <p className="muted">
              Track a calendar from Prepare to get an unofficial syllabus.
            </p>
          </section>
        )}

        {!notSeries && noPack && (
          <section className="panel">
            <p className="muted">
              No unofficial topic pack for this exam. We do not invent a syllabus. Verify the
              official website.
            </p>
          </section>
        )}

        {!notSeries && plan && (
          <section className="panel">
            <div className="plan-progress-meta">
              <strong>
                {doneCount} / {total} topics
              </strong>
              <span className="muted small">{pct}%</span>
            </div>
            <div
              className="plan-progress"
              role="progressbar"
              aria-valuemin={0}
              aria-valuemax={total || 0}
              aria-valuenow={doneCount}
              aria-label="Topics completed"
            >
              <div className="plan-progress-fill" style={{ width: `${pct}%` }} />
            </div>
            {plan.note && <p className="muted small plan-note">{plan.note}</p>}
            <ul className="plan-topics">
              {topics.map((topic) => {
                const done = Boolean(progress[topic.id])
                return (
                  <li key={topic.id} className={`plan-topic ${done ? 'is-done' : ''}`}>
                    <input
                      id={`topic-${topic.id}`}
                      type="checkbox"
                      checked={done}
                      disabled={Boolean(busyId)}
                      onChange={(e) => onToggle(topic.id, e.target.checked)}
                    />
                    <label htmlFor={`topic-${topic.id}`}>
                      <span className="plan-topic-title">{topic.title}</span>
                      <span className="plan-topic-dates">{dateLabel(topic)}</span>
                    </label>
                  </li>
                )
              })}
            </ul>
          </section>
        )}
      </div>
    </div>
  )
}
