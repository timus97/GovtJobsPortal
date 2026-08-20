const fs = require('fs');
const path = require('path');
const sqlite = require('../db/sqlite');

const root = path.join(__dirname, '..', '..', '..');
const jobsPath = path.join(root, 'data', 'processed', 'jobs.json');
const opportunitiesPath = path.join(root, 'data', 'processed', 'opportunities.json');
const statsPath = path.join(root, 'data', 'processed', 'stats.json');
const reportPath = path.join(root, 'data', 'processed', 'run-report.json');
const collectReportPath = path.join(root, 'data', 'processed', 'collect-report.json');
const alertReportPath = path.join(root, 'data', 'processed', 'alert-report.json');
const registryPath = path.join(root, 'data', 'sources', 'registry.json');
const examSeriesPath = path.join(root, 'data', 'processed', 'exam_series.json');

function readJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return fallback;
  }
}

function getJobs() {
  return readJson(jobsPath, []);
}

function tryLoadJobsFromCache() {
  if (sqlite.getStatus() !== 'ok' || !sqlite.isFresh()) return null;
  const db = sqlite.getDb();
  if (!db) return null;
  try {
    const rows = db.prepare('SELECT raw FROM opportunities').all();
    const jobs = [];
    for (const row of rows) {
      jobs.push(JSON.parse(row.raw));
    }
    return jobs;
  } catch {
    return null;
  }
}

function loadJobsForList() {
  // JSON is cheaper than SELECT raw for every row. Cache is used for PK lookups.
  return getJobs();
}

function applyJobFilters(jobs, query = {}) {
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
    sort = 'lastDate',
  } = query;

  let next = jobs;

  if (q) {
    const term = String(q).toLowerCase();
    next = next.filter(
      (j) =>
        j.title?.toLowerCase().includes(term) ||
        j.organization?.toLowerCase().includes(term) ||
        j.location?.toLowerCase().includes(term) ||
        j.sector?.toLowerCase().includes(term) ||
        j.summary?.toLowerCase().includes(term)
    );
  }

  if (orgType) {
    const types = String(orgType).split(',');
    next = next.filter((j) => types.includes(j.orgType));
  }
  if (location) {
    const loc = String(location).toLowerCase();
    next = next.filter((j) => j.location?.toLowerCase().includes(loc));
  }
  if (qualification) {
    next = next.filter((j) => j.qualification === qualification);
  }
  if (sector) {
    next = next.filter((j) => j.sector === sector);
  }
  if (status) {
    const statuses = String(status).split(',');
    next = next.filter((j) => statuses.includes(j.status));
  } else {
    next = next.filter((j) => j.status !== 'closed');
  }
  if (selectionProcess) {
    next = next.filter((j) => j.selectionProcess === selectionProcess);
  }
  const examFilter = hasExam == null ? '' : String(hasExam).trim().toLowerCase();
  if (examFilter && examFilter !== 'all') {
    if (examFilter === 'yes' || examFilter === 'true' || examFilter === '1') {
      next = next.filter((j) => j.hasExam === true);
    } else if (examFilter === 'no' || examFilter === 'false' || examFilter === '0') {
      next = next.filter((j) => j.hasExam === false);
    }
  }
  if (sourceId) {
    next = next.filter((j) => j.sourceId === sourceId);
  }

  if (sort === 'newest') {
    next = [...next].sort((a, b) =>
      (b.notificationDate || '').localeCompare(a.notificationDate || '')
    );
  } else if (sort === 'lastDate') {
    next = [...next].sort((a, b) => (a.lastDate || '9999').localeCompare(b.lastDate || '9999'));
  }

  return next;
}

function getOpportunities() {
  const opps = readJson(opportunitiesPath, null);
  if (Array.isArray(opps) && opps.length) return opps;
  return getJobs();
}

function getStats() {
  return readJson(statsPath, { total: 0 });
}

function getPipeline() {
  return {
    process: readJson(reportPath, null),
    collect: readJson(collectReportPath, null),
    alert: readJson(alertReportPath, null),
  };
}

function getRegistry() {
  return readJson(registryPath, { sources: [] });
}

function getSourcesView() {
  const registry = getRegistry();
  const pipeline = getPipeline();
  const collectById = Object.fromEntries(
    (pipeline.collect?.results || []).map((r) => [r.sourceId, r])
  );
  const stats = getStats();
  const jobsBySource = stats.bySource || {};

  const sources = (registry.sources || []).map((s) => {
    const run = collectById[s.sourceId] || null;
    const listUrls = Array.isArray(s.listUrls) ? s.listUrls.filter(Boolean) : [];
    const urls = [...new Set([s.baseUrl, ...listUrls].filter(Boolean))];
    return {
      sourceId: s.sourceId,
      name: s.name,
      category: s.category || 'other',
      orgType: s.orgTypeDefault || null,
      baseUrl: s.baseUrl || '',
      listUrls,
      urls,
      priority: s.priority,
      method: s.method,
      cadence: s.cadence,
      enabled: Boolean(s.enabled),
      render: s.render || null,
      publishedJobs: jobsBySource[s.sourceId] || 0,
      lastScrape: run
        ? {
            ok: run.ok,
            written: run.written ?? 0,
            errors: run.errors || [],
            durationMs: run.durationMs,
            metrics: run.metrics || {},
          }
        : null,
    };
  });

  return {
    updatedAt: registry.updatedAt || null,
    lastCollectAt: pipeline.collect?.finishedAt || null,
    sources,
    groups: {
      central_gov: sources.filter(
        (s) =>
          ['aggregator', 'manual', 'apprenticeship'].includes(s.category) || s.orgType === 'central'
      ),
      govt_company: sources.filter((s) => s.orgType === 'govt_company' || s.category === 'staffing'),
      psu: sources.filter((s) => s.category === 'psu_careers' || s.orgType === 'psu'),
    },
  };
}

function listJobs(query = {}) {
  const {
    page = '1',
    limit = '20',
  } = query;

  const jobs = applyJobFilters(loadJobsForList(), query);
  const pageNum = Math.max(1, parseInt(page, 10) || 1);
  const limitNum = Math.min(100, Math.max(1, parseInt(limit, 10) || 20));
  const total = jobs.length;
  const start = (pageNum - 1) * limitNum;
  const items = jobs.slice(start, start + limitNum);

  return {
    items,
    total,
    page: pageNum,
    limit: limitNum,
    totalPages: Math.ceil(total / limitNum) || 1,
  };
}

function getJobById(id) {
  if (sqlite.getStatus() === 'ok' && sqlite.isFresh()) {
    try {
      const db = sqlite.getDb();
      const row = db && db.prepare('SELECT raw FROM opportunities WHERE id = ?').get(id);
      if (row && row.raw) return JSON.parse(row.raw);
    } catch {
      /* JSON SoR fallback */
    }
  }
  return getJobs().find((j) => j.id === id) || null;
}

function decorateSeries(series, jobsById) {
  const linked = (series.linkedOpportunityIds || [])
    .map((id) => jobsById.get(id))
    .filter(Boolean);
  const openLinked = linked.filter((j) => j.status === 'open' || j.status === 'closing_soon');
  return {
    ...series,
    kind: 'series',
    linkedOpportunities: openLinked.map((j) => ({
      id: j.id,
      title: j.title,
      status: j.status,
      lastDate: j.lastDate,
      officialUrl: j.officialUrl,
    })),
    canApply: !series.applyNever && openLinked.length > 0,
  };
}

function jobsByIdMap() {
  return new Map(getJobs().map((j) => [j.id, j]));
}

function getExamSeries() {
  const byId = jobsByIdMap();
  return readJson(examSeriesPath, []).map((s) => decorateSeries(s, byId));
}

function getExamSeriesById(id) {
  const series = readJson(examSeriesPath, []).find((s) => s.id === id);
  if (!series) return null;
  return decorateSeries(series, jobsByIdMap());
}

function listExamSeries(query = {}) {
  const items = getExamSeries();
  const board = query.board ? String(query.board).trim() : '';
  const q = query.q ? String(query.q).toLowerCase() : '';
  let filtered = items;
  if (board) filtered = filtered.filter((s) => String(s.board).toLowerCase() === board.toLowerCase());
  if (q) {
    filtered = filtered.filter(
      (s) =>
        s.name.toLowerCase().includes(q) ||
        String(s.board).toLowerCase().includes(q) ||
        String(s.cycle || '').toLowerCase().includes(q)
    );
  }
  return {
    items: filtered,
    total: filtered.length,
    boards: [...new Set(items.map((s) => s.board))].sort(),
  };
}

function getFilterMeta() {
  const jobs = getJobs().filter((j) => j.status !== 'closed');
  const uniq = (arr) => [...new Set(arr.filter(Boolean))].sort();
  return {
    orgTypes: uniq(jobs.map((j) => j.orgType)),
    locations: uniq(jobs.map((j) => j.location)),
    qualifications: uniq(jobs.map((j) => j.qualification)),
    sectors: uniq(jobs.map((j) => j.sector)),
    selectionProcesses: uniq(jobs.map((j) => j.selectionProcess)),
    sourceIds: uniq(jobs.map((j) => j.sourceId)),
    statuses: ['open', 'closing_soon', 'closed'],
    hasExam: ['all', 'yes', 'no'],
  };
}

module.exports = {
  listJobs,
  getJobById,
  getJobs,
  getOpportunities,
  getStats,
  getPipeline,
  getRegistry,
  getSourcesView,
  getFilterMeta,
  applyJobFilters,
  getExamSeries,
  getExamSeriesById,
  listExamSeries,
};
