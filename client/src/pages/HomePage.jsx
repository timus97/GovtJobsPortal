import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchJobs, fetchStats, fetchPipeline } from '../api/jobs'
import JobCard from '../components/JobCard'
import { isStudentEnabled } from '../lib/features'
import { formatDateTime } from '../utils/labels'

export default function HomePage() {
  const [stats, setStats] = useState(null)
  const [latest, setLatest] = useState([])
  const [pipeline, setPipeline] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    Promise.all([fetchStats(), fetchJobs({ limit: 6, sort: 'lastDate' }), fetchPipeline()])
      .then(([s, j, p]) => {
        if (cancelled) return
        setStats(s)
        setLatest(j.items || [])
        setPipeline(p)
      })
      .catch((e) => {
        if (!cancelled) setError(e.message)
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div>
      <section className="hero">
        <div className="container hero-grid">
          <div>
            <p className="eyebrow">India · Official notifications</p>
            <h1>Central, PSU &amp; govt company jobs — exam and interview routes</h1>
            <p className="lead">
              Browse open applications (exam and no-exam). Match against a profile (reservation
              category required). Create a student desk to track exams, keep admit cards private on
              this API host, and practise unofficial mocks. Every card links to the official apply
              page. Aggregator only — always verify dates and eligibility on the official site.
            </p>
            <div className="hero-actions">
              <Link to="/jobs" className="btn btn-primary btn-lg">
                Browse open jobs
              </Link>
              <Link to="/prepare" className="btn btn-secondary btn-lg">
                Prepare for exams
              </Link>
              {isStudentEnabled() && (
                <Link to="/account/register" className="btn btn-secondary btn-lg">
                  Create exam desk
                </Link>
              )}
            </div>
            {(pipeline?.process?.finishedAt || pipeline?.collect?.finishedAt) && (
              <p className="pipeline-stamp muted">
                Data last processed: {formatDateTime(pipeline.process?.finishedAt)} · Last
                collect: {formatDateTime(pipeline.collect?.finishedAt)} ·{' '}
                {pipeline.process?.sourcesMonitored ?? pipeline.collect?.sourcesAttempted ?? '—'}{' '}
                sources
                {stats?.examSeries != null ? ` · ${stats.examSeries} exam calendars` : ''}
              </p>
            )}

          </div>
          <div className="stats-grid">
            <div className="stat-card">
              <span className="stat-value">{stats?.open ?? '—'}</span>
              <span className="stat-label">Open listings</span>
            </div>
            <div className="stat-card">
              <span className="stat-value">{stats?.closingSoon ?? '—'}</span>
              <span className="stat-label">Closing soon</span>
            </div>
            <div className="stat-card">
              <span className="stat-value">{stats?.byOrgType?.psu ?? '—'}</span>
              <span className="stat-label">PSU posts</span>
            </div>
            <div className="stat-card">
              <span className="stat-value">{stats?.total ?? '—'}</span>
              <span className="stat-label">In dataset</span>
            </div>
          </div>
        </div>
      </section>

      <section className="section">
        <div className="container">
          <div className="section-head">
            <h2>Closing soon &amp; latest</h2>
            <Link to="/jobs">View all →</Link>
          </div>
          {error && <p className="error-box">{error}. Is the API running on port 4000?</p>}
          <div className="job-grid">
            {latest.map((job) => (
              <JobCard key={job.id} job={job} />
            ))}
          </div>
          {!error && latest.length === 0 && (
            <p className="muted">No open jobs right now. Check back after the next pipeline run.</p>
          )}
        </div>
      </section>

      <section className="section section-alt">
        <div className="container how-grid">
          <div>
            <h2>What we include</h2>
            <ul className="check-list">
              <li>Walk-in interviews</li>
              <li>Interview-only / CV shortlisting</li>
              <li>Merit-based (no exam) selection</li>
              <li>Contract &amp; consultant roles via interview</li>
              <li>Apprenticeships (clearly labelled)</li>
              <li>Exam-based notifications (CBT, SSC, UPSC, IBPS, etc.)</li>
            </ul>
          </div>
          <div>
            <h2>Competitive exams are labelled, not hidden</h2>
            <ul className="check-list exclude">
              <li>UPSC / SSC / IBPS style exams</li>
              <li>CBT / online written tests</li>
              <li>GATE-based PSU drives</li>
              <li>Bank &amp; railway competitive exams</li>
            </ul>
          </div>
        </div>
      </section>
    </div>
  )
}
