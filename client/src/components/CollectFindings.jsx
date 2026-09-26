function kindLabel(kind) {
  if (kind === 'pdf') return 'PDF'
  if (kind === 'html') return 'Page'
  return kind || 'Item'
}

function statusClass(status) {
  return status === 'kept' ? 'badge badge-soft' : 'badge badge-muted'
}

function shortError(msg) {
  const s = String(msg || '')
  if (/Executable doesn't exist|playwright install/i.test(s)) return 'Playwright browser is not installed'
  return s.length > 180 ? `${s.slice(0, 180)}…` : s
}

function FindingRow({ item }) {
  return (
    <tr>
      <td>
        <span className={statusClass(item.status)}>{item.status === 'kept' ? 'Kept' : 'Skipped'}</span>
        <span className="badge badge-muted" style={{ marginLeft: '0.35rem' }}>
          {kindLabel(item.kind)}
        </span>
      </td>
      <td>
        <strong>{item.title || 'Untitled'}</strong>
        {item.organization ? <div className="muted small">{item.organization}</div> : null}
        {item.excerpt ? <div className="muted small collect-excerpt">{item.excerpt}</div> : null}
        {item.reason ? <div className="muted small">Skip reason: {item.reason}</div> : null}
      </td>
      <td className="muted small">{item.lastDate || '—'}</td>
      <td className="muted small">{item.vacancies ?? '—'}</td>
      <td className="muted small">{item.selectionProcess || '—'}</td>
      <td>
        {item.url ? (
          <a className="collect-url" href={item.url} target="_blank" rel="noreferrer">
            {item.url}
          </a>
        ) : (
          '—'
        )}
        {item.pageUrl && item.pageUrl !== item.url ? (
          <div className="muted small">
            from{' '}
            <a href={item.pageUrl} target="_blank" rel="noreferrer">
              listing
            </a>
          </div>
        ) : null}
      </td>
    </tr>
  )
}

export default function CollectFindings({ progress }) {
  if (!progress) return null
  const sources = progress.sourcesLog || []
  const liveId = progress.running ? progress.current?.sourceId : null
  const liveFindings = liveId
    ? (progress.findings || []).filter((f) => f.sourceId === liveId)
    : []
  const shown = sources.length || liveFindings.length
  if (!shown && !progress.running) {
    return (
      <p className="muted small" style={{ margin: '0.75rem 0 0' }}>
        Scraped jobs and PDFs will appear here during a collect so you can check titles, dates, and
        official links.
      </p>
    )
  }

  return (
    <div className="collect-findings">
      <div className="collect-findings-head">
        <h3>Scraped this run</h3>
        <p className="muted small" style={{ margin: 0 }}>
          Kept notices, skipped PDFs, last dates, and the URL each row came from.
        </p>
      </div>

      {liveFindings.length > 0 && (
        <div className="collect-source-block is-live">
          <div className="collect-source-title">
            <strong>{progress.current?.name || liveId}</strong>
            <span className="muted small">
              {liveFindings.filter((f) => f.status === 'kept').length} kept ·{' '}
              {liveFindings.filter((f) => f.status === 'skipped').length} skipped
            </span>
          </div>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Result</th>
                  <th>Job / PDF</th>
                  <th>Last date</th>
                  <th>Posts</th>
                  <th>Selection</th>
                  <th>URL</th>
                </tr>
              </thead>
              <tbody>
                {liveFindings
                  .slice()
                  .reverse()
                  .slice(0, 40)
                  .map((item, idx) => (
                    <FindingRow key={`${item.url}-${idx}`} item={item} />
                  ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {sources.map((source) => (
        <div className="collect-source-block" key={`${source.sourceId}-${source.at}`}>
          <div className="collect-source-title">
            <strong>{source.name || source.sourceId}</strong>
            <span className="muted small">
              {source.ok ? 'ok' : 'failed'} · {source.kept || 0} kept · {source.skipped || 0} skipped
              {source.durationMs != null ? ` · ${source.durationMs}ms` : ''}
            </span>
          </div>
          {source.findings && source.findings.length ? (
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Result</th>
                    <th>Job / PDF</th>
                    <th>Last date</th>
                    <th>Posts</th>
                    <th>Selection</th>
                    <th>URL</th>
                  </tr>
                </thead>
                <tbody>
                  {source.findings
                    .slice()
                    .reverse()
                    .map((item, idx) => (
                      <FindingRow key={`${source.sourceId}-${item.url}-${idx}`} item={item} />
                    ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="muted small" style={{ margin: '0.4rem 0 0' }}>
              No keepable job or PDF extracted
              {source.errors?.[0]?.message ? ` — ${shortError(source.errors[0].message)}` : '.'}
            </p>
          )}
        </div>
      ))}
    </div>
  )
}
