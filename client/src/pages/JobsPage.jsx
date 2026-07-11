import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { fetchFilters, fetchJobs } from '../api/jobs'
import JobCard from '../components/JobCard'
import JobFilters from '../components/JobFilters'

const defaultFilters = {
  q: '',
  orgType: '',
  sector: '',
  qualification: '',
  selectionProcess: '',
  status: '',
  location: '',
  sourceId: '',
  sort: 'lastDate',
  page: 1,
  limit: 12,
}

export default function JobsPage() {
  const [searchParams] = useSearchParams()
  const [meta, setMeta] = useState({})
  const [values, setValues] = useState(() => ({
    ...defaultFilters,
    sourceId: searchParams.get('sourceId') || '',
  }))
  const [result, setResult] = useState({ items: [], total: 0, totalPages: 1, page: 1 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const fromUrl = searchParams.get('sourceId') || ''
    setValues((v) => (v.sourceId === fromUrl ? v : { ...v, sourceId: fromUrl, page: 1 }))
  }, [searchParams])

  useEffect(() => {
    fetchFilters().then(setMeta).catch(() => {})
  }, [])

  useEffect(() => {
    setLoading(true)
    setError('')
    const t = setTimeout(() => {
      fetchJobs(values)
        .then(setResult)
        .catch((e) => setError(e.message))
        .finally(() => setLoading(false))
    }, values.q ? 250 : 0)
    return () => clearTimeout(t)
  }, [values])

  return (
    <div className="section">
      <div className="container">
        <div className="section-head">
          <div>
            <h1>Job listings</h1>
            <p className="muted">No-exam central, PSU and government company openings</p>
          </div>
          <p className="result-count">
            {loading ? 'Loading…' : `${result.total} result${result.total === 1 ? '' : 's'}`}
          </p>
        </div>

        <div className="jobs-layout">
          <JobFilters
            filters={meta}
            values={values}
            onChange={setValues}
            onReset={() => setValues(defaultFilters)}
          />
          <div>
            {error && <p className="error-box">{error}</p>}
            <div className="job-grid">
              {result.items.map((job) => (
                <JobCard key={job.id} job={job} />
              ))}
            </div>
            {!loading && !error && result.items.length === 0 && (
              <p className="empty panel">No jobs match these filters. Try clearing some filters.</p>
            )}
            {result.totalPages > 1 && (
              <div className="pagination">
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={result.page <= 1}
                  onClick={() => setValues((v) => ({ ...v, page: v.page - 1 }))}
                >
                  Previous
                </button>
                <span>
                  Page {result.page} of {result.totalPages}
                </span>
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={result.page >= result.totalPages}
                  onClick={() => setValues((v) => ({ ...v, page: v.page + 1 }))}
                >
                  Next
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
