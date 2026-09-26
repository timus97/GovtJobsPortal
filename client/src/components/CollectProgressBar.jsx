import { useEffect, useState } from 'react'
import { fetchCollectProgress, startDailyCollect } from '../api/ops'
import CollectFindings from './CollectFindings'

function phaseLabel(phase) {
  if (phase === 'pdf') return 'Reading PDF'
  if (phase === 'source') return 'Opening source'
  if (phase === 'fetch') return 'Checking page'
  return phase || 'Idle'
}

export default function CollectProgressBar({ compact = false }) {
  const [progress, setProgress] = useState(null)
  const [error, setError] = useState('')
  const [starting, setStarting] = useState(false)

  useEffect(() => {
    let cancelled = false
    async function tick() {
      try {
        const data = await fetchCollectProgress()
        if (!cancelled) {
          setProgress(data)
          setError('')
        }
      } catch (err) {
        if (!cancelled && err.status !== 401) {
          setError(err.message || 'Could not load collector progress')
        }
      }
    }
    tick()
    const ms = compact || progress?.running ? 2000 : 6000
    const id = setInterval(tick, ms)
    return () => {
      cancelled = true
      clearInterval(id)
    }
  }, [compact, progress?.running])

  async function onStart(full) {
    setStarting(true)
    setError('')
    try {
      await startDailyCollect(full ? {} : { limit: 8 })
      const data = await fetchCollectProgress()
      setProgress(data)
    } catch (err) {
      setError(err.message || 'Could not start collector')
    } finally {
      setStarting(false)
    }
  }

  const running = Boolean(progress?.running)
  const total = Number(progress?.sourcesTotal || 0)
  const done = Number(progress?.sourcesDone || 0)
  const pct = total > 0 ? Math.min(100, Math.round((done / total) * 100)) : running ? 2 : 0
  const current = progress?.current || null
  const currentUrl = current?.url || progress?.lastFinished?.url || ''

  if (compact) {
    if (!progress) return null
    return (
      <div className={`collect-strip${running ? ' is-running' : ''}`}>
        <div className="collect-strip-bar" style={{ width: `${pct}%` }} />
        <div className="collect-strip-copy">
          {running ? (
            <>
              <strong>Collector</strong>
              <span>
                {done}/{total || '?'} · {current?.name || current?.sourceId || 'starting'}
                {progress.lastFinding?.title ? ` · ${progress.lastFinding.status === 'skipped' ? 'skip' : 'kept'}: ${progress.lastFinding.title}` : ''}
              </span>
              {currentUrl ? (
                <a href={currentUrl} target="_blank" rel="noreferrer" title={currentUrl}>
                  {currentUrl}
                </a>
              ) : null}
            </>
          ) : (
            <>
              <span>Collector idle{progress.finishedAt ? ` · last run ${new Date(progress.finishedAt).toLocaleString()}` : ''}</span>
              {currentUrl ? (
                <a href={currentUrl} target="_blank" rel="noreferrer" title={currentUrl}>
                  {currentUrl}
                </a>
              ) : null}
            </>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="panel ops-card collect-progress-card" style={{ marginBottom: '1.25rem' }}>
      <div className="collect-progress-head">
        <div>
          <h2>Daily collector</h2>
          <p className="muted small" style={{ margin: 0 }}>
            Live source, the portal URL being checked, and each job or PDF scraped from it.
          </p>
        </div>
        <div className="hero-actions">
          <button type="button" className="btn btn-secondary" disabled={starting || running} onClick={() => onStart(false)}>
            {starting ? 'Starting…' : 'Quick check (8)'}
          </button>
          <button type="button" className="btn btn-primary" disabled={starting || running} onClick={() => onStart(true)}>
            {running ? 'Collecting…' : 'Start daily collect'}
          </button>
        </div>
      </div>

      {error && <p className="error-box">{error}</p>}

      <div className="collect-meter" aria-label="Collector progress">
        <div className="collect-meter-fill" style={{ width: `${pct}%` }} />
      </div>
      <div className="collect-meter-meta">
        <span>
          {running ? 'Running' : progress?.abortedAt ? 'Aborted' : progress?.finishedAt ? 'Finished' : 'Idle'}
          {total ? ` · ${done} / ${total} sources` : ''}
        </span>
        <span>
          {progress?.recordsWritten || 0} records · {progress?.pdfsProcessed || 0} PDFs
          {progress?.pdfsSkipped ? ` · ${progress.pdfsSkipped} PDFs skipped` : ''}
        </span>
      </div>

      <div className={`collect-current${running ? ' is-live' : ''}`}>
        <div className="muted small">{running ? phaseLabel(current?.phase) : 'Last page'}</div>
        <strong>{current?.name || current?.sourceId || progress?.lastFinished?.name || 'No source yet'}</strong>
        {currentUrl ? (
          <a className="collect-url" href={currentUrl} target="_blank" rel="noreferrer">
            {currentUrl}
          </a>
        ) : (
          <span className="muted small">Waiting for the next portal URL…</span>
        )}
      </div>

      {progress?.lastFinished && (
        <p className="muted small" style={{ margin: '0.65rem 0 0' }}>
          Last finished: {progress.lastFinished.name || progress.lastFinished.sourceId}
          {progress.lastFinished.ok ? ` · wrote ${progress.lastFinished.written}` : ' · failed'}
          {progress.lastFinished.durationMs != null ? ` · ${progress.lastFinished.durationMs}ms` : ''}
        </p>
      )}

      {progress?.lastFinding?.title && (
        <p className="muted small" style={{ margin: '0.35rem 0 0' }}>
          Latest row: {progress.lastFinding.status === 'skipped' ? 'skipped' : 'kept'} ·{' '}
          {progress.lastFinding.title}
          {progress.lastFinding.lastDate ? ` · last date ${progress.lastFinding.lastDate}` : ''}
        </p>
      )}

      <CollectFindings progress={progress} />
    </div>
  )
}
