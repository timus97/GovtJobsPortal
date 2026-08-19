import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import '../App.css'
import { postMatch } from '../api/jobs'
import { VERIFY_BADGE } from '../lib/eligibilityMatch'
import { isProfileComplete, loadProfile } from '../lib/profile'

function Chip({ reason }) {
  return (
    <span className={`chip chip-${reason.outcome}`} title={reason.rule}>
      {reason.detail}
    </span>
  )
}

function ResultCard({ row, kind }) {
  return (
    <article className="job-card match-card">
      <div className="job-card-top">
        <div className="badge-row">
          {kind === 'match' && row.lowConfidence && (
            <span className="badge badge-warn">{VERIFY_BADGE}</span>
          )}
          {kind === 'excluded' && <span className="badge badge-muted">Did not match</span>}
          {row.confidence != null && (
            <span className="badge badge-soft">
              Score {row.score} · {(row.confidence * 100).toFixed(0)}% facts covered
            </span>
          )}
        </div>
        <h3 className="job-title">
          {row.id ? <Link to={`/jobs/${row.id}`}>{row.title || row.id}</Link> : row.title || row.id}
        </h3>
        {row.organization && <p className="job-org">{row.organization}</p>}
      </div>
      <div className="chip-row">
        {(row.reasons || []).map((r) => (
          <Chip key={`${row.id}-${r.rule}`} reason={r} />
        ))}
      </div>
      <div className="job-card-actions">
        {row.id && (
          <Link to={`/jobs/${row.id}`} className="btn btn-secondary">
            View details
          </Link>
        )}
        {row.officialUrl && (
          <a
            href={row.officialUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="btn btn-primary"
          >
            Official site
          </a>
        )}
      </div>
    </article>
  )
}

export default function MatchResultsPage() {
  const [profile] = useState(() => loadProfile())
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const complete = isProfileComplete(profile)

  useEffect(() => {
    if (!complete) return undefined
    let cancelled = false
    setLoading(true)
    setError('')
    postMatch(profile, 50)
      .then((body) => {
        if (!cancelled) setData(body)
      })
      .catch((e) => {
        if (!cancelled) setError(e.message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [complete, profile])

  return (
    <div className="section">
      <div className="container">
        <div className="match-banner" role="note">
          Not an official eligibility decision.
        </div>

        <div className="section-head">
          <div>
            <h1>Match results</h1>
            <p className="muted">
              Ranked against currently listed opportunities. Unknown facts lower confidence — they
              do not fail a row. Always verify on the official site.
            </p>
          </div>
          <Link to="/profile" className="btn btn-secondary">
            Edit profile
          </Link>
        </div>

        {!complete && (
          <p className="error-box">
            Complete date of birth, highest education, reservation category, birth state, and at
            least one domicile state on the <Link to="/profile">profile page</Link> before
            matching. Incomplete profiles are not sent to the match API. Category is required for
            printed age-relaxation tables.
          </p>
        )}

        {loading && <p className="muted">Matching listed opportunities…</p>}
        {error && <p className="error-box">{error}</p>}

        {!loading && !error && complete && data && data.matches.length === 0 && (
          <p className="empty panel">
            No rows passed without a hard fail. Check “Did not match” or verify dates on official
            sites. We do not call a missing last date “open”.
          </p>
        )}

        {data && data.matches.length > 0 && (
          <section>
            <h2>You can apply</h2>
            <p className="muted small">
              {data.matches.length} of {data.candidateCount} scored · generated {data.generatedAt}
            </p>
            <div className="job-grid">
              {data.matches.map((row) => (
                <ResultCard key={row.id} row={row} kind="match" />
              ))}
            </div>
          </section>
        )}

        {data && data.excluded.length > 0 && (
          <section className="section">
            <h2>Did not match</h2>
            <p className="muted small">Hard-fail rules only. Truncated to the request limit.</p>
            <div className="job-grid">
              {data.excluded.map((row) => (
                <ResultCard key={row.id} row={row} kind="excluded" />
              ))}
            </div>
          </section>
        )}
      </div>
    </div>
  )
}
