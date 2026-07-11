import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchSources, fetchPipeline } from '../api/jobs'
import { formatDateTime, ORG_TYPE_LABELS } from '../utils/labels'

const CATEGORY_LABELS = {
  aggregator: 'Central / national portals',
  manual: 'Manual curator',
  staffing: 'Govt company / staffing',
  psu_careers: 'Public Sector Undertakings (PSU)',
  apprenticeship: 'Apprenticeship',
  other: 'Other',
}

const GROUP_ORDER = [
  {
    key: 'central',
    title: 'Central government & national portals',
    blurb: 'Official central / national job listing sites we scrape for no-exam openings.',
    match: (s) =>
      ['aggregator', 'manual', 'apprenticeship'].includes(s.category) || s.orgType === 'central',
  },
  {
    key: 'govt_company',
    title: 'Government companies',
    blurb: 'Central public-sector companies and project staffing agencies.',
    match: (s) => s.orgType === 'govt_company' || s.category === 'staffing',
  },
  {
    key: 'psu',
    title: 'PSU career pages',
    blurb: 'Major Public Sector Undertaking recruitment / careers URLs monitored for vacancies.',
    match: (s) => s.category === 'psu_careers' || s.orgType === 'psu',
  },
]

function scrapeLabel(source) {
  if (!source.enabled) return { text: 'Paused', className: 'badge badge-muted' }
  const run = source.lastScrape
  if (!run) return { text: 'Not scraped yet', className: 'badge badge-muted' }
  if (!run.ok) return { text: `Failed · ${run.written} rows`, className: 'badge badge-warn' }
  if (run.written > 0) return { text: `Scraped · ${run.written} rows`, className: 'badge badge-soft' }
  return { text: 'Reached · 0 job links', className: 'badge badge-muted' }
}

function SourceCard({ source }) {
  const status = scrapeLabel(source)
  const urls = source.urls?.length
    ? source.urls
    : [source.baseUrl, ...(source.listUrls || [])].filter(Boolean)

  return (
    <article className="source-card panel">
      <div className="source-card-head">
        <div>
          <h3>{source.name}</h3>
          <p className="muted small source-id">{source.sourceId}</p>
        </div>
        <div className="badge-row">
          {source.orgType && (
            <span className={`badge org-${source.orgType}`}>
              {ORG_TYPE_LABELS[source.orgType] || source.orgType}
            </span>
          )}
          <span className={status.className}>{status.text}</span>
          {source.publishedJobs > 0 && (
            <span className="badge badge-soft">{source.publishedJobs} jobs live</span>
          )}
        </div>
      </div>

      <dl className="source-meta">
        <div>
          <dt>Category</dt>
          <dd>{CATEGORY_LABELS[source.category] || source.category}</dd>
        </div>
        <div>
          <dt>Method</dt>
          <dd>
            {source.method}
            {source.render === 'browser' ? ' · Playwright' : ''}
          </dd>
        </div>
        <div>
          <dt>Cadence</dt>
          <dd>{source.cadence}</dd>
        </div>
        <div>
          <dt>Priority</dt>
          <dd>{source.priority || '—'}</dd>
        </div>
      </dl>

      <div className="source-urls">
        <h4>Official URLs we scrape</h4>
        {urls.length === 0 ? (
          <p className="muted small">No public URL (manual seed only).</p>
        ) : (
          <ul className="url-list">
            {urls.map((url) => (
              <li key={url}>
                <a href={url} target="_blank" rel="noopener noreferrer">
                  {url}
                </a>
              </li>
            ))}
          </ul>
        )}
      </div>

      {source.lastScrape?.errors?.length > 0 && (
        <p className="source-error small">
          Last scrape note: {source.lastScrape.errors[0].message || 'error'}
        </p>
      )}

      {source.publishedJobs > 0 && (
        <p className="source-jobs-link">
          <Link to={`/jobs?sourceId=${encodeURIComponent(source.sourceId)}`}>
            View jobs from this source →
          </Link>
        </p>
      )}
    </article>
  )
}

export default function SourcesPage() {
  const [payload, setPayload] = useState({ sources: [] })
  const [pipeline, setPipeline] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    Promise.all([fetchSources(), fetchPipeline()])
      .then(([src, pipe]) => {
        setPayload(src)
        setPipeline(pipe)
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  const sources = payload.sources || []

  const grouped = useMemo(() => {
    const used = new Set()
    return GROUP_ORDER.map((g) => {
      const items = sources.filter((s) => {
        if (used.has(s.sourceId)) return false
        if (g.match(s)) {
          used.add(s.sourceId)
          return true
        }
        return false
      })
      return { ...g, items }
    }).filter((g) => g.items.length > 0)
  }, [sources])

  const scrapeTargets = sources.filter((s) => s.method !== 'manual' || s.urls?.length)
  const withUrls = sources.filter((s) => (s.urls || []).length > 0)

  return (
    <div className="section">
      <div className="container">
        <div className="section-head">
          <div>
            <h1>Scraped gov &amp; PSU sources</h1>
            <p className="muted lead" style={{ marginBottom: 0 }}>
              Full list of central government, government company and PSU career pages this portal
              monitors. Every job card still links to the official apply / notification URL.
            </p>
          </div>
        </div>

        {error && <p className="error-box">{error}</p>}
        {loading && <p className="muted">Loading sources…</p>}

        <div className="stats-grid sources-summary">
          <div className="stat-card">
            <span className="stat-value">{sources.length}</span>
            <span className="stat-label">Sources in registry</span>
          </div>
          <div className="stat-card">
            <span className="stat-value">{withUrls.length}</span>
            <span className="stat-label">With official URLs</span>
          </div>
          <div className="stat-card">
            <span className="stat-value">
              {sources.filter((s) => s.lastScrape && s.lastScrape.written > 0).length}
            </span>
            <span className="stat-label">Returned jobs last run</span>
          </div>
          <div className="stat-card">
            <span className="stat-value">
              {sources.reduce((n, s) => n + (s.publishedJobs || 0), 0)}
            </span>
            <span className="stat-label">Live jobs from sources</span>
          </div>
        </div>

        {(pipeline?.collect || pipeline?.process) && (
          <p className="muted" style={{ marginTop: '1rem' }}>
            Last collect: {formatDateTime(pipeline.collect?.finishedAt)} · Last process:{' '}
            {formatDateTime(pipeline.process?.finishedAt)} · Registry updated:{' '}
            {payload.updatedAt || '—'}
          </p>
        )}

        {/* Compact master URL table */}
        <div className="table-wrap" style={{ marginTop: '1.5rem', marginBottom: '2rem' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>Organisation / portal</th>
                <th>Type</th>
                <th>Official URL(s)</th>
                <th>Last scrape</th>
                <th>Jobs live</th>
              </tr>
            </thead>
            <tbody>
              {scrapeTargets.map((s) => {
                const urls = s.urls?.length
                  ? s.urls
                  : [s.baseUrl, ...(s.listUrls || [])].filter(Boolean)
                const status = scrapeLabel(s)
                return (
                  <tr key={s.sourceId}>
                    <td>
                      <strong>{s.name}</strong>
                      <div className="muted small">{s.sourceId}</div>
                    </td>
                    <td>{ORG_TYPE_LABELS[s.orgType] || s.category || '—'}</td>
                    <td>
                      {urls.length === 0 ? (
                        <span className="muted">—</span>
                      ) : (
                        <ul className="url-list compact">
                          {urls.map((url) => (
                            <li key={url}>
                              <a href={url} target="_blank" rel="noopener noreferrer">
                                {url}
                              </a>
                            </li>
                          ))}
                        </ul>
                      )}
                    </td>
                    <td>
                      <span className={status.className}>{status.text}</span>
                    </td>
                    <td>{s.publishedJobs || 0}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>

        {grouped.map((group) => (
          <section key={group.key} className="source-group">
            <h2>{group.title}</h2>
            <p className="muted">{group.blurb}</p>
            <div className="source-grid">
              {group.items.map((s) => (
                <SourceCard key={s.sourceId} source={s} />
              ))}
            </div>
          </section>
        ))}

        <p className="muted small" style={{ marginTop: '2rem' }}>
          Scrapers hit these URLs on the daily pipeline. A “0 rows” result usually means the page
          blocked the bot, returned 404, or had no job-like links that day — the source stays listed
          for the next run.
        </p>
      </div>
    </div>
  )
}
