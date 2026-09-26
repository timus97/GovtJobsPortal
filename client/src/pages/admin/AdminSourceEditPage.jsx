import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { collectSource, createSource, getOpsSource, patchSource } from '../../api/ops'

const empty = {
  sourceId: '',
  name: '',
  category: 'other',
  baseUrl: '',
  listUrlsText: '',
  orgTypeDefault: 'central',
  priority: 'P2',
  method: 'html_scrape',
  cadence: 'daily',
  enabled: true,
  collector: '',
  render: '',
  robotsNotes: '',
  owner: 'curator',
  autoPublish: false,
  rateLimitMs: 5000,
}

function fromSource(source) {
  return {
    ...empty,
    ...source,
    listUrlsText: Array.isArray(source.listUrls) ? source.listUrls.join('\n') : '',
    enabled: source.enabled !== false,
    rateLimitMs: source.rateLimitMs ?? 5000,
  }
}

export default function AdminSourceEditPage() {
  const { sourceId } = useParams()
  const isNew = !sourceId || sourceId === 'new'
  const navigate = useNavigate()
  const [form, setForm] = useState(empty)
  const [health, setHealth] = useState(null)
  const [error, setError] = useState('')
  const [saved, setSaved] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (isNew) return undefined
    let cancelled = false
    getOpsSource(sourceId)
      .then((body) => {
        if (cancelled) return
        setForm(fromSource(body.source || {}))
        setHealth(body.health || null)
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'Failed to load source')
      })
    return () => {
      cancelled = true
    }
  }, [isNew, sourceId])

  function update(patch) {
    setForm((f) => ({ ...f, ...patch }))
    setSaved('')
  }

  function payload() {
    return {
      sourceId: form.sourceId,
      name: form.name,
      category: form.category,
      baseUrl: form.baseUrl,
      listUrls: form.listUrlsText,
      orgTypeDefault: form.orgTypeDefault,
      priority: form.priority,
      method: form.method,
      cadence: form.cadence,
      enabled: form.enabled,
      collector: form.collector,
      render: form.render,
      robotsNotes: form.robotsNotes,
      owner: form.owner,
      autoPublish: form.autoPublish,
      rateLimitMs: Number(form.rateLimitMs) || 0,
    }
  }

  async function onSubmit(e) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      if (isNew) {
        const created = await createSource(payload())
        navigate(`/ops/sources/${encodeURIComponent(created.source.sourceId)}`, { replace: true })
        return
      }
      await patchSource(sourceId, payload())
      setSaved('Saved to registry.json')
    } catch (err) {
      setError(err.message || 'Save failed')
    } finally {
      setBusy(false)
    }
  }

  async function onCollect() {
    setError('')
    setBusy(true)
    try {
      const result = await collectSource(sourceId)
      navigate(`/ops/runs/${encodeURIComponent(result.jobId)}`)
    } catch (err) {
      setError(err.message || 'Collect failed')
      setBusy(false)
    }
  }

  return (
    <div className="section">
      <div className="container profile-layout">
        <p className="back-link">
          <Link to="/ops/sources">← All sources</Link>
          {' · '}
          <Link to="/ops">Collectors dashboard</Link>
        </p>
        <div className="section-head">
          <div>
            <p className="eyebrow">Admin · source</p>
            <h1>{isNew ? 'Add source' : form.name || sourceId}</h1>
            <p className="muted">
              Registry drives daily collect and this dashboard. Queue collect to send the first
              list URL through the paste-URL review queue.
            </p>
          </div>
        </div>
        {error && <p className="error-box">{error}</p>}
        {health && (
          <p className="muted small">
            Last scrape: {health.lastScrape?.ok === false ? 'error' : health.lastScrape ? 'ok' : 'none'}{' '}
            · live jobs: {health.publishedJobs ?? 0}
          </p>
        )}
        <form className="panel profile-form" onSubmit={onSubmit}>
          {isNew && (
            <label className="field">
              <span>Source id *</span>
              <input
                value={form.sourceId}
                onChange={(e) => update({ sourceId: e.target.value })}
                placeholder="ncs_gov"
                required
              />
            </label>
          )}
          <label className="field">
            <span>Name *</span>
            <input value={form.name} onChange={(e) => update({ name: e.target.value })} required />
          </label>
          <label className="field">
            <span>Base URL</span>
            <input
              type="url"
              value={form.baseUrl}
              onChange={(e) => update({ baseUrl: e.target.value })}
              placeholder="https://…"
            />
          </label>
          <label className="field">
            <span>List URLs (one per line)</span>
            <textarea
              rows={4}
              value={form.listUrlsText}
              onChange={(e) => update({ listUrlsText: e.target.value })}
            />
          </label>
          <div className="jobs-layout" style={{ gridTemplateColumns: '1fr 1fr' }}>
            <label className="field">
              <span>Category</span>
              <select value={form.category} onChange={(e) => update({ category: e.target.value })}>
                <option value="aggregator">aggregator</option>
                <option value="psu_careers">psu_careers</option>
                <option value="staffing">staffing</option>
                <option value="board">board</option>
                <option value="psc">psc</option>
                <option value="calendar">calendar</option>
                <option value="apprenticeship">apprenticeship</option>
                <option value="manual">manual</option>
                <option value="other">other</option>
              </select>
            </label>
            <label className="field">
              <span>Org type</span>
              <select
                value={form.orgTypeDefault}
                onChange={(e) => update({ orgTypeDefault: e.target.value })}
              >
                <option value="central">central</option>
                <option value="psu">psu</option>
                <option value="govt_company">govt_company</option>
                <option value="autonomous">autonomous</option>
                <option value="state">state</option>
                <option value="other">other</option>
              </select>
            </label>
            <label className="field">
              <span>Priority</span>
              <select value={form.priority} onChange={(e) => update({ priority: e.target.value })}>
                <option value="P0">P0</option>
                <option value="P1">P1</option>
                <option value="P2">P2</option>
                <option value="P3">P3</option>
              </select>
            </label>
            <label className="field">
              <span>Method</span>
              <select value={form.method} onChange={(e) => update({ method: e.target.value })}>
                <option value="html_scrape">html_scrape</option>
                <option value="browser_scrape">browser_scrape</option>
                <option value="pdf_watch">pdf_watch</option>
                <option value="manual">manual</option>
              </select>
            </label>
            <label className="field">
              <span>Collector key</span>
              <input
                value={form.collector}
                onChange={(e) => update({ collector: e.target.value })}
                placeholder="genericCareers / genericPsc / …"
              />
            </label>
            <label className="field">
              <span>Cadence</span>
              <input value={form.cadence} onChange={(e) => update({ cadence: e.target.value })} />
            </label>
            <label className="field">
              <span>Rate limit (ms)</span>
              <input
                type="number"
                min="0"
                value={form.rateLimitMs}
                onChange={(e) => update({ rateLimitMs: e.target.value })}
              />
            </label>
            <label className="field">
              <span>Render</span>
              <input
                value={form.render}
                onChange={(e) => update({ render: e.target.value })}
                placeholder="browser or empty"
              />
            </label>
          </div>
          <label className="field">
            <span>Owner</span>
            <input value={form.owner} onChange={(e) => update({ owner: e.target.value })} />
          </label>
          <label className="field">
            <span>Robots / legal notes</span>
            <textarea
              rows={3}
              value={form.robotsNotes}
              onChange={(e) => update({ robotsNotes: e.target.value })}
            />
          </label>
          <label className="check-inline">
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={(e) => update({ enabled: e.target.checked })}
            />
            Enabled for daily collect
          </label>
          <label className="check-inline" style={{ marginTop: '0.5rem' }}>
            <input
              type="checkbox"
              checked={form.autoPublish}
              onChange={(e) => update({ autoPublish: e.target.checked })}
            />
            Auto-publish (registry flag only)
          </label>
          <div className="hero-actions">
            <button className="btn btn-primary" type="submit" disabled={busy}>
              {busy ? 'Saving…' : isNew ? 'Create source' : 'Save source'}
            </button>
            {!isNew && (
              <button
                type="button"
                className="btn btn-secondary"
                disabled={busy || form.method === 'manual' || !form.enabled}
                onClick={onCollect}
              >
                Queue collect
              </button>
            )}
          </div>
          {saved && <p className="muted small">{saved}</p>}
        </form>
      </div>
    </div>
  )
}
