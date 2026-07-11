import {
  ORG_TYPE_LABELS,
  SELECTION_LABELS,
  QUAL_LABELS,
  STATUS_LABELS,
} from '../utils/labels'

export default function JobFilters({ filters, values, onChange, onReset }) {
  const set = (key, value) => onChange({ ...values, [key]: value, page: 1 })

  return (
    <aside className="filters panel">
      <div className="filters-head">
        <h2>Filters</h2>
        <button type="button" className="link-btn" onClick={onReset}>
          Reset
        </button>
      </div>

      <label className="field">
        <span>Search</span>
        <input
          type="search"
          placeholder="Title, org, location…"
          value={values.q || ''}
          onChange={(e) => set('q', e.target.value)}
        />
      </label>

      <label className="field">
        <span>Organisation type</span>
        <select value={values.orgType || ''} onChange={(e) => set('orgType', e.target.value)}>
          <option value="">All</option>
          {(filters.orgTypes || []).map((t) => (
            <option key={t} value={t}>
              {ORG_TYPE_LABELS[t] || t}
            </option>
          ))}
        </select>
      </label>

      <label className="field">
        <span>Sector</span>
        <select value={values.sector || ''} onChange={(e) => set('sector', e.target.value)}>
          <option value="">All</option>
          {(filters.sectors || []).map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </label>

      <label className="field">
        <span>Qualification</span>
        <select
          value={values.qualification || ''}
          onChange={(e) => set('qualification', e.target.value)}
        >
          <option value="">All</option>
          {(filters.qualifications || []).map((q) => (
            <option key={q} value={q}>
              {QUAL_LABELS[q] || q}
            </option>
          ))}
        </select>
      </label>

      <label className="field">
        <span>Selection</span>
        <select
          value={values.selectionProcess || ''}
          onChange={(e) => set('selectionProcess', e.target.value)}
        >
          <option value="">All no-exam types</option>
          {(filters.selectionProcesses || []).map((s) => (
            <option key={s} value={s}>
              {SELECTION_LABELS[s] || s}
            </option>
          ))}
        </select>
      </label>

      <label className="field">
        <span>Status</span>
        <select value={values.status || ''} onChange={(e) => set('status', e.target.value)}>
          <option value="">Open + closing soon</option>
          <option value="open">{STATUS_LABELS.open}</option>
          <option value="closing_soon">{STATUS_LABELS.closing_soon}</option>
          <option value="closed">{STATUS_LABELS.closed}</option>
          <option value="open,closing_soon,closed">All including closed</option>
        </select>
      </label>

      <label className="field">
        <span>Location contains</span>
        <input
          type="text"
          placeholder="e.g. Delhi, All India"
          value={values.location || ''}
          onChange={(e) => set('location', e.target.value)}
        />
      </label>

      <label className="field">
        <span>Source</span>
        <select value={values.sourceId || ''} onChange={(e) => set('sourceId', e.target.value)}>
          <option value="">All sources</option>
          {(filters.sourceIds || []).map((id) => (
            <option key={id} value={id}>
              {id}
            </option>
          ))}
        </select>
      </label>

      <label className="field">
        <span>Sort</span>
        <select value={values.sort || 'lastDate'} onChange={(e) => set('sort', e.target.value)}>
          <option value="lastDate">Last date (soonest)</option>
          <option value="newest">Newest notification</option>
        </select>
      </label>
    </aside>
  )
}
