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
  getFilterMeta,
};
