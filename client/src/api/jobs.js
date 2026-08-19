const BASE = import.meta.env.VITE_API_BASE || '/api'
const STATIC = `${import.meta.env.BASE_URL || '/'}data`.replace(/\/+/g, '/').replace(/\/$/, '') || '/data'

let cache = null

async function loadStaticBundle() {
  if (cache) return cache
  const [jobs, stats, sources, processReport, collectReport, alertReport] = await Promise.all([
    fetch(`${STATIC}/jobs.json`).then((r) => (r.ok ? r.json() : [])),
    fetch(`${STATIC}/stats.json`).then((r) => (r.ok ? r.json() : {})),
    fetch(`${STATIC}/sources.json`).then((r) => (r.ok ? r.json() : { sources: [] })),
    fetch(`${STATIC}/run-report.json`).then((r) => (r.ok ? r.json() : null)).catch(() => null),
    fetch(`${STATIC}/collect-report.json`).then((r) => (r.ok ? r.json() : null)).catch(() => null),
    fetch(`${STATIC}/alert-report.json`).then((r) => (r.ok ? r.json() : null)).catch(() => null),
  ])
  cache = {
    jobs: Array.isArray(jobs) ? jobs : [],
    stats: stats || {},
    sources: sources || { sources: [] },
    pipeline: {
      process: processReport,
      collect: collectReport,
      alert: alertReport,
    },
  }
  return cache
}

function filterJobs(jobs, params = {}) {
  let list = [...jobs]
  const {
    q,
    orgType,
    location,
    qualification,
    sector,
    status,
    selectionProcess,
    hasExam,
    sourceId,
    page = 1,
    limit = 20,
    sort = 'lastDate',
  } = params

  if (q) {
    const term = String(q).toLowerCase()
    list = list.filter(
      (j) =>
        j.title?.toLowerCase().includes(term) ||
        j.organization?.toLowerCase().includes(term) ||
        j.location?.toLowerCase().includes(term) ||
        j.sector?.toLowerCase().includes(term) ||
        j.summary?.toLowerCase().includes(term)
    )
  }
  if (orgType) {
    const types = String(orgType).split(',')
    list = list.filter((j) => types.includes(j.orgType))
  }
  if (location) {
    const loc = String(location).toLowerCase()
    list = list.filter((j) => j.location?.toLowerCase().includes(loc))
  }
  if (qualification) list = list.filter((j) => j.qualification === qualification)
  if (sector) list = list.filter((j) => j.sector === sector)
  if (status) {
    const statuses = String(status).split(',')
    list = list.filter((j) => statuses.includes(j.status))
  } else {
    list = list.filter((j) => j.status !== 'closed')
  }
  if (selectionProcess) list = list.filter((j) => j.selectionProcess === selectionProcess)
  if (hasExam === 'yes') list = list.filter((j) => j.hasExam === true)
  else if (hasExam === 'no') list = list.filter((j) => j.hasExam === false)
  if (sourceId) list = list.filter((j) => j.sourceId === sourceId)

  if (sort === 'newest') {
    list.sort((a, b) => (b.notificationDate || '').localeCompare(a.notificationDate || ''))
  } else {
    list.sort((a, b) => (a.lastDate || '9999').localeCompare(b.lastDate || '9999'))
  }

  const pageNum = Math.max(1, parseInt(page, 10) || 1)
  const limitNum = Math.min(100, Math.max(1, parseInt(limit, 10) || 20))
  const total = list.length
  const start = (pageNum - 1) * limitNum
  return {
    items: list.slice(start, start + limitNum),
    total,
    page: pageNum,
    limit: limitNum,
    totalPages: Math.ceil(total / limitNum) || 1,
  }
}

function filterMeta(jobs) {
  const open = jobs.filter((j) => j.status !== 'closed')
  const uniq = (arr) => [...new Set(arr.filter(Boolean))].sort()
  return {
    orgTypes: uniq(open.map((j) => j.orgType)),
    locations: uniq(open.map((j) => j.location)),
    qualifications: uniq(open.map((j) => j.qualification)),
    sectors: uniq(open.map((j) => j.sector)),
    selectionProcesses: uniq(open.map((j) => j.selectionProcess)),
    sourceIds: uniq(open.map((j) => j.sourceId)),
    statuses: ['open', 'closing_soon', 'closed'],
  }
}

async function get(path) {
  const res = await fetch(`${BASE}${path}`)
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error(body.error || `Request failed (${res.status})`)
  }
  return res.json()
}

async function tryApiThenStatic(apiPath, staticFn) {
  try {
    return await get(apiPath)
  } catch {
    return staticFn()
  }
}

export function fetchJobs(params = {}) {
  const qs = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') qs.set(k, v)
  })
  const q = qs.toString()
  return tryApiThenStatic(`/jobs${q ? `?${q}` : ''}`, async () => {
    const { jobs } = await loadStaticBundle()
    return filterJobs(jobs, params)
  })
}

export function fetchJob(id) {
  return tryApiThenStatic(`/jobs/${id}`, async () => {
    const { jobs } = await loadStaticBundle()
    const job = jobs.find((j) => j.id === id)
    if (!job) throw new Error('Job not found')
    return job
  })
}

export function fetchStats() {
  return tryApiThenStatic('/stats', async () => {
    const { stats } = await loadStaticBundle()
    return stats
  })
}

export function fetchFilters() {
  return tryApiThenStatic('/meta/filters', async () => {
    const { jobs } = await loadStaticBundle()
    return filterMeta(jobs)
  })
}

export function fetchSources() {
  return tryApiThenStatic('/sources', async () => {
    const { sources } = await loadStaticBundle()
    return sources
  })
}

export function fetchPipeline() {
  return tryApiThenStatic('/pipeline', async () => {
    const { pipeline } = await loadStaticBundle()
    return pipeline
  })
}
