import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import '../App.css'
import { fetchExamSeries } from '../api/jobs'
import { matchExamSeries } from '../lib/eligibilityMatch'
import { isProfileComplete, loadProfile } from '../lib/profile'
import TrackButton from '../components/TrackButton'

function SeriesCard({ series, recommended }) {
  const canApply = Boolean(series.canApply && !series.applyNever)
  const openOpp = (series.linkedOpportunities || [])[0]
  return (
    <article className="job-card">
      <div className="job-card-top">
        <div className="badge-row">
          <span className="badge badge-soft">{series.board}</span>
          <span className="badge badge-exam">Prepare-for</span>
          {series.applyNever && <span className="badge badge-muted">Not a vacancy</span>}
          {recommended && <span className="badge badge-soft">Recommended</span>}
          {series.cycle && <span className="badge badge-muted">{series.cycle}</span>}
        </div>
        <h3 className="job-title">{series.name}</h3>
        <p className="job-org">Official calendar — not an apply-now listing</p>
      </div>
      <dl className="series-dates">
        {series.expectedNotify && (
          <div>
            <dt>Expected notification</dt>
            <dd>{series.expectedNotify}</dd>
          </div>
        )}
        {series.expectedApply && (
          <div>
            <dt>Expected apply window</dt>
            <dd>{series.expectedApply}</dd>
          </div>
        )}
        {series.expectedExam && (
          <div>
            <dt>Expected exam</dt>
            <dd>{series.expectedExam}</dd>
          </div>
        )}
        {series.minEducation && (
          <div>
            <dt>Typical education</dt>
            <dd>{series.minEducation}</dd>
          </div>
        )}
      </dl>
      <div className="chip-row">
        {(series.reasons || []).map((r) => (
          <span key={`${series.id}-${r.rule}`} className={`chip chip-${r.outcome}`}>
            {r.detail}
          </span>
        ))}
      </div>
      <div className="job-card-actions">
        <a
          href={series.officialUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="btn btn-secondary"
        >
          Official calendar
        </a>
        {canApply && openOpp && (
          <Link to={`/jobs/${openOpp.id}`} className="btn btn-primary">
            Apply (open window)
          </Link>
        )}
        <TrackButton kind="series" refId={series.id} compact />
      </div>
    </article>
  )
}

export default function PreparePage() {
  const [profile] = useState(() => loadProfile())
  const [board, setBoard] = useState('')
  const [data, setData] = useState({ items: [], boards: [] })
  const [error, setError] = useState('')
  const complete = isProfileComplete(profile)

  useEffect(() => {
    let cancelled = false
    fetchExamSeries(board ? { board } : {})
      .then((body) => {
        if (!cancelled) setData(body)
      })
      .catch((e) => {
        if (!cancelled) setError(e.message)
      })
    return () => {
      cancelled = true
    }
  }, [board])

  const ranked = useMemo(() => {
    if (!complete || !data.items.length) {
      return { recommended: [], rest: data.items }
    }
    try {
      const { matches, excluded } = matchExamSeries(profile, data.items)
      const byId = Object.fromEntries(data.items.map((s) => [s.id, s]))
      const recommended = matches.map((m) => ({ ...byId[m.id], ...m, reasons: m.reasons }))
      const rest = data.items.map((s) => {
        const scored = matches.find((m) => m.id === s.id) || excluded.find((m) => m.id === s.id)
        return scored ? { ...s, ...scored, reasons: scored.reasons } : s
      })
      return { recommended, rest }
    } catch {
      return { recommended: [], rest: data.items }
    }
  }, [complete, data.items, profile])

  return (
    <div className="section">
      <div className="container">
        <div className="match-banner" role="note">
          These are official calendars to prepare for — not vacancies. There is no Apply button
          unless a linked notification is currently open. Not an official eligibility decision.
        </div>

        <div className="section-head">
          <div>
            <h1>Prepare for upcoming exams</h1>
            <p className="muted">
              UPSC, SSC, IBPS, SBI, RRB calendars plus UGC NET as prepare-for. CUET is not listed.
            </p>
          </div>
          {complete ? (
            <Link to="/profile" className="btn btn-secondary">
              Edit profile
            </Link>
          ) : (
            <Link to="/profile" className="btn btn-primary">
              Add profile for recommendations
            </Link>
          )}
        </div>

        <label className="field" style={{ maxWidth: '16rem' }}>
          Board
          <select value={board} onChange={(e) => setBoard(e.target.value)}>
            <option value="">All boards</option>
            {(data.boards || []).map((b) => (
              <option key={b} value={b}>
                {b}
              </option>
            ))}
          </select>
        </label>

        {error && <p className="error-box">{error}</p>}

        {ranked.recommended.length > 0 && (
          <section>
            <h2>Recommended for your profile</h2>
            <p className="muted small">
              Education floor only. Missing age-as-on dates do not fail a future cycle.
            </p>
            <div className="job-grid">
              {ranked.recommended.map((s) => (
                <SeriesCard key={s.id} series={s} recommended />
              ))}
            </div>
          </section>
        )}

        <section className="section">
          <h2>{ranked.recommended.length ? 'All calendars' : 'Exam calendars'}</h2>
          {data.items.length === 0 && !error && <p className="muted">Loading calendars…</p>}
          <div className="job-grid">
            {(ranked.recommended.length ? ranked.rest : data.items).map((s) => (
              <SeriesCard key={s.id} series={s} />
            ))}
          </div>
        </section>
      </div>
    </div>
  )
}
