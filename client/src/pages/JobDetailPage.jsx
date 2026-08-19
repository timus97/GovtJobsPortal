import { useEffect, useState } from 'react'
import '../App.css'
import { Link, useParams } from 'react-router-dom'
import { fetchJob } from '../api/jobs'
import {
  ORG_TYPE_LABELS,
  SELECTION_LABELS,
  QUAL_LABELS,
  STATUS_LABELS,
  HAS_EXAM_LABELS,
  formatDate,
} from '../utils/labels'

export default function JobDetailPage() {
  const { id } = useParams()
  const [job, setJob] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    setJob(null)
    fetchJob(id)
      .then(setJob)
      .catch((e) => setError(e.message))
  }, [id])

  if (error) {
    return (
      <div className="section container">
        <p className="error-box">{error}</p>
        <Link to="/jobs">← Back to jobs</Link>
      </div>
    )
  }

  if (!job) {
    return (
      <div className="section container">
        <p className="muted">Loading job…</p>
      </div>
    )
  }

  return (
    <div className="section">
      <div className="container detail-layout">
        <p>
          <Link to="/jobs" className="back-link">
            ← All jobs
          </Link>
        </p>

        <header className="detail-header panel">
          <div className="badge-row">
            <span className={`badge org-${job.orgType}`}>{ORG_TYPE_LABELS[job.orgType]}</span>
            <span className="badge badge-soft">{SELECTION_LABELS[job.selectionProcess] || job.selectionProcess}</span>
            {job.hasExam === true && <span className="badge badge-exam">Exam</span>}
            <span className={`badge ${job.status === 'closing_soon' ? 'badge-warn' : 'badge-muted'}`}>
              {STATUS_LABELS[job.status]}
            </span>
          </div>
          <h1>{job.title}</h1>
          <p className="job-org">{job.organization}</p>
          <p className="detail-summary">{job.summary}</p>
          <div className="hero-actions">
            <a
              href={job.officialUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="btn btn-primary btn-lg"
            >
              Apply / read official notification
            </a>
          </div>
          <p className="muted small">
            You will leave this site. Apply only on the official organisation website.
          </p>
        </header>

        <div className="detail-grid">
          <section className="panel">
            <h2>Key details</h2>
            <dl className="detail-dl">
              <div>
                <dt>Location</dt>
                <dd>{job.location}</dd>
              </div>
              <div>
                <dt>Sector</dt>
                <dd>{job.sector}</dd>
              </div>
              <div>
                <dt>Vacancies</dt>
                <dd>{job.vacancies ?? 'Not specified'}</dd>
              </div>
              <div>
                <dt>Qualification</dt>
                <dd>{QUAL_LABELS[job.qualification] || job.qualification || '—'}</dd>
              </div>
              <div>
                <dt>Experience</dt>
                <dd>{job.experience || '—'}</dd>
              </div>
              <div>
                <dt>Salary</dt>
                <dd>{job.salary || '—'}</dd>
              </div>
              <div>
                <dt>Application mode</dt>
                <dd>{job.applicationMode || '—'}</dd>
              </div>
              <div>
                <dt>Exam</dt>
                <dd>{job.hasExam === true ? HAS_EXAM_LABELS.yes : HAS_EXAM_LABELS.no}</dd>
              </div>
              <div>
                <dt>Notification date</dt>
                <dd>{formatDate(job.notificationDate)}</dd>
              </div>
              <div>
                <dt>Last date</dt>
                <dd>{formatDate(job.lastDate)}</dd>
              </div>
              {job.walkInDate && (
                <div>
                  <dt>Walk-in date</dt>
                  <dd>{formatDate(job.walkInDate)}</dd>
                </div>
              )}
            </dl>
          </section>

          <section className="panel">
            <h2>Selection process</h2>
            <p>
              <strong>{SELECTION_LABELS[job.selectionProcess] || job.selectionProcess}</strong>
              {job.hasExam === true
                ? ' — exam-based listing (as classified).'
                : ' — no competitive written exam / CBT for this listing (as classified).'}
            </p>
            {job.processSteps?.length > 0 ? (
              <ol className="steps">
                {job.processSteps.map((step, i) => (
                  <li key={i}>{step}</li>
                ))}
              </ol>
            ) : (
              <p className="muted">See official notification for step-by-step process.</p>
            )}
          </section>

          <section className="panel">
            <h2>Eligibility</h2>
            {job.eligibility?.length > 0 ? (
              <ul className="check-list">
                {job.eligibility.map((e, i) => (
                  <li key={i}>{e}</li>
                ))}
              </ul>
            ) : (
              <p className="muted">See official notification.</p>
            )}
          </section>

          <section className="panel">
            <h2>Documents</h2>
            {job.documentsRequired?.length > 0 ? (
              <ul className="check-list">
                {job.documentsRequired.map((d, i) => (
                  <li key={i}>{d}</li>
                ))}
              </ul>
            ) : (
              <p className="muted">As per official advertisement.</p>
            )}
          </section>

          <section className="panel">
            <h2>Source &amp; provenance</h2>
            <dl className="detail-dl">
              <div>
                <dt>Source</dt>
                <dd>{job.sourceName}</dd>
              </div>
              <div>
                <dt>Source page</dt>
                <dd>
                  <a href={job.sourceUrl} target="_blank" rel="noopener noreferrer">
                    {job.sourceUrl}
                  </a>
                </dd>
              </div>
              <div>
                <dt>Collected</dt>
                <dd>{formatDate(job.collectedAt)}</dd>
              </div>
            </dl>
          </section>
        </div>
      </div>
    </div>
  )
}
