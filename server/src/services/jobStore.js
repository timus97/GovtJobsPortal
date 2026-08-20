const fs = require('fs');
const path = require('path');

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
  let jobs = getJobs();

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
    page = '1',
    limit = '20',
    sort = 'lastDate',
  } = query;

  if (q) {
    const term = String(q).toLowerCase();
    jobs = jobs.filter(
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
    jobs = jobs.filter((j) => types.includes(j.orgType));
  }
  if (location) {
    const loc = String(location).toLowerCase();
    jobs = jobs.filter((j) => j.location?.toLowerCase().includes(loc));
  }
  if (qualification) {
    jobs = jobs.filter((j) => j.qualification === qualification);
  }
  if (sector) {
    jobs = jobs.filter((j) => j.sector === sector);
  }
  if (status) {
    const statuses = String(status).split(',');
    jobs = jobs.filter((j) => statuses.includes(j.status));
  } else {
    // default: hide closed unless asked
    jobs = jobs.filter((j) => j.status !== 'closed');
  }
  if (selectionProcess) {
    jobs = jobs.filter((j) => j.selectionProcess === selectionProcess);
  }
  const examFilter = hasExam == null ? '' : String(hasExam).trim().toLowerCase();
  if (examFilter && examFilter !== 'all') {
    if (examFilter === 'yes' || examFilter === 'true' || examFilter === '1') {
      jobs = jobs.filter((j) => j.hasExam === true);
    } else if (examFilter === 'no' || examFilter === 'false' || examFilter === '0') {
      jobs = jobs.filter((j) => j.hasExam === false);
    }
  }
  if (sourceId) {
    jobs = jobs.filter((j) => j.sourceId === sourceId);
  }

  if (sort === 'newest') {
    jobs = [...jobs].sort((a, b) =>
      (b.notificationDate || '').localeCompare(a.notificationDate || '')
    );
  } else if (sort === 'lastDate') {
    jobs = [...jobs].sort((a, b) => (a.lastDate || '9999').localeCompare(b.lastDate || '9999'));
  }

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
  return getJobs().find((j) => j.id === id) || null;
}

function decorateSeries(series) {
  const jobs = getJobs();
  const linked = (series.linkedOpportunityIds || [])
    .map((id) => jobs.find((j) => j.id === id))
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

function getExamSeries() {
  return readJson(examSeriesPath, []).map(decorateSeries);
}

function getExamSeriesById(id) {
  return getExamSeries().find((s) => s.id === id) || null;
}

function listExamSeries(query = {}) {
  let items = getExamSeries();
  const board = query.board ? String(query.board).trim() : '';
  const q = query.q ? String(query.q).toLowerCase() : '';
  if (board) items = items.filter((s) => String(s.board).toLowerCase() === board.toLowerCase());
  if (q) {
    items = items.filter(
      (s) =>
        s.name.toLowerCase().includes(q) ||
        String(s.board).toLowerCase().includes(q) ||
        String(s.cycle || '').toLowerCase().includes(q)
    );
  }
  return {
    items,
    total: items.length,
    boards: [...new Set(getExamSeries().map((s) => s.board))].sort(),
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
  getExamSeries,
  getExamSeriesById,
  listExamSeries,
};
