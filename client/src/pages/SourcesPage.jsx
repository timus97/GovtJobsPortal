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
  // Manual seed is never "scraped"
  if (source.method === 'manual') {
    return {
      text: source.publishedJobs > 0 ? 'Manual · has live jobs' : 'Manual only (no auto-scrape)',
      className: 'badge badge-muted',
      help: 'Filled by human seed/CSV, not by the daily scraper.',
    }
  }
  if (!source.enabled) {
    return {
      text: 'Paused · no scrape',
      className: 'badge badge-muted',
      help: 'enabled=false in registry — usually missing a known career URL.',
    }
  }
  const run = source.lastScrape
  if (!run) {
    if (source.publishedJobs > 0) {
      return {
        text: `Has ${source.publishedJobs} live jobs · report stale`,
        className: 'badge badge-soft',
        help: 'Jobs exist in jobs.json but this source is missing from the last collect-report. Re-run collect or rebuildCollectReport.',
      }
    }
    return {
      text: 'Not in last scrape report',
      className: 'badge badge-muted',
      help: 'Listed in the registry, but the last collect-report has no result for this id (not run yet, or report outdated).',
    }
  }
  if (!run.ok) {
    return {
      text: `Failed · ${run.written || 0} rows`,
      className: 'badge badge-warn',
      help: run.errors?.[0]?.message || 'Scraper error for this source.',
    }
  }
  if (run.written > 0) {
    return {
      text: `Scraped · ${run.written} rows`,
      className: 'badge badge-soft',
      help: 'Last collect wrote this many staging rows from career pages/PDFs.',
    }
  }
  return {
    text: 'Scraped · 0 job links',
    className: 'badge badge-muted',
    help: 'URL was fetched but no job-like links were extracted (empty, blocked, or layout change).',
  }
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

      {status.help && <p className="muted small status-help">{status.help}</p>}

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

        <div className="panel status-legend" style={{ padding: '1rem 1.15rem', marginBottom: '1.25rem' }}>
          <h2 style={{ fontSize: '1.05rem', marginBottom: '0.5rem' }}>What the status badges mean</h2>
          <ul className="legend-list">
            <li>
              <span className="badge badge-soft">Scraped · N rows</span> — last collect run extracted N
              job links into staging for this site.
            </li>
            <li>
              <span className="badge badge-muted">Scraped · 0 job links</span> — URL was hit; no
              vacancies extracted (empty page, block, or layout change).
            </li>
            <li>
              <span className="badge badge-muted">Not in last scrape report</span> — source is
              listed, but the last <code>collect-report.json</code> has no entry (not run yet, or
              report is outdated). <strong>Not</strong> the same as “scraper is running now”.
            </li>
            <li>
              <span className="badge badge-muted">Paused · no scrape</span> — disabled in registry
              (often no known official career URL).
            </li>
            <li>
              <span className="badge badge-muted">Manual only</span> — human-curated seed, not
              auto-scraped.
            </li>
            <li>
              <span className="badge badge-soft">N jobs live</span> — published vacancies currently
              on the Jobs page from this source id.
            </li>
          </ul>
          <p className="muted small" style={{ margin: '0.75rem 0 0' }}>
            <strong>Where is the data?</strong> There is no SQL database. All listings live in JSON
            files — mainly <code>data/processed/jobs.json</code> (and a public copy under{' '}
            <code>/data/jobs.json</code> on this site). Full explanation:{' '}
            <a
              href="https://github.com/timus97/GovtJobsPortal/blob/main/docs/DATA_AND_STATUS.md"
              target="_blank"
              rel="noopener noreferrer"
            >
              docs/DATA_AND_STATUS.md
            </a>
            .
          </p>
          <p className="muted small" style={{ margin: '0.35rem 0 0' }}>
            <strong>In progress?</strong> Scrapes only run when someone executes{' '}
            <code>npm run collect:daily</code> or the GitHub Action. The public site itself does not
            scrape in the background. Watch the terminal or the repo Actions tab for active runs.
          </p>
        </div>

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
            <span className="stat-label">Had rows in last report</span>
          </div>
          <div className="stat-card">
            <span className="stat-value">
              {sources.filter((s) => (s.publishedJobs || 0) > 0).length}
            </span>
            <span className="stat-label">Sources with live jobs</span>
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
