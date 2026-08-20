/**
 * Paste-URL collect queue only. Daily collect must not create these rows.
 */
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { fetchText } = require('../../../scripts/collect/lib/http');
const { extractLinks, extractPageTitle } = require('../../../scripts/collect/lib/htmlLinks');
const { toStagingRecord } = require('../../../scripts/collect/lib/toStaging');
const { isValidOpportunity } = require('../../../shared/opportunitySchema');
const { computeStatus, isValidJob, stableJobId } = require('../../../shared/jobSchema');

const root = path.join(__dirname, '..', '..', '..');

const EXTRA_HOSTS = new Set([
  'ibps.in',
  'www.ibps.in',
  'sbi.co.in',
  'www.sbi.co.in',
  'sbi.bank.in',
  'bank.sbi',
  'recruitment.sbi.bank.in',
  'becil.com',
  'www.becil.com',
  'nta.ac.in',
  'www.nta.ac.in',
  'ugcnet.nta.nic.in',
  'gate2027.iitm.ac.in',
  'aiimsexams.ac.in',
  'www.aiimsexams.ac.in',
  'aiims.edu',
  'www.aiims.edu',
]);

const TERMINAL = new Set(['published', 'published_local', 'rejected', 'cancelled']);

function jobsPath() {
  return process.env.COLLECT_JOBS_PATH || path.join(root, 'data', 'processed', 'collect-jobs.json');
}

function stagingDir() {
  return process.env.OPS_PASTE_DIR || path.join(root, 'data', 'staging', 'ops_paste');
}

function jobsJsonPath() {
  return process.env.OPS_JOBS_JSON || path.join(root, 'data', 'processed', 'jobs.json');
}

function opportunitiesJsonPath() {
  return process.env.OPS_OPPORTUNITIES_JSON || path.join(root, 'data', 'processed', 'opportunities.json');
}

function hostGapMs() {
  const n = Number(process.env.OPS_HOST_GAP_MS);
  return Number.isFinite(n) ? Math.max(0, n) : 5000;
}

function publishDebounceMs() {
  const n = Number(process.env.OPS_PUBLISH_DEBOUNCE_MS);
  return Number.isFinite(n) ? Math.max(0, n) : 5000;
}

function nowIso() {
  return new Date().toISOString();
}

function readJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return fallback;
  }
}

function writeAtomic(file, data) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const tmp = `${file}.${process.pid}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2), 'utf8');
  fs.renameSync(tmp, file);
}

function loadJobs() {
  const data = readJson(jobsPath(), { jobs: [] });
  if (Array.isArray(data)) return data;
  return Array.isArray(data.jobs) ? data.jobs : [];
}

function saveJobs(list) {
  writeAtomic(jobsPath(), { updatedAt: nowIso(), jobs: list });
}

function hostnameOf(url) {
  try {
    return new URL(url).hostname.toLowerCase();
  } catch {
    return '';
  }
}

function isAllowedHost(url) {
  const host = hostnameOf(url);
  if (!host) return false;
  if (host.endsWith('.gov.in') || host === 'gov.in') return true;
  if (host.endsWith('.nic.in') || host === 'nic.in') return true;
  if (EXTRA_HOSTS.has(host)) return true;
  const bare = host.replace(/^www\./, '');
  if (EXTRA_HOSTS.has(bare)) return true;
  return [...EXTRA_HOSTS].some((h) => host === h || host.endsWith(`.${h.replace(/^www\./, '')}`));
}

function parseHttpsUrl(raw) {
  const url = String(raw || '').trim();
  if (!url) return { ok: false, error: 'url is required' };
  let parsed;
  try {
    parsed = new URL(url);
  } catch {
    return { ok: false, error: 'url is not valid' };
  }
  if (parsed.protocol !== 'https:') {
    return { ok: false, error: 'url must be https' };
  }
  if (!isAllowedHost(parsed.href)) {
    return { ok: false, error: 'host_not_allowed', reason: 'host_not_allowed' };
  }
  return { ok: true, url: parsed.href, host: parsed.hostname.toLowerCase() };
}

function findJob(id) {
  return loadJobs().find((j) => j.id === id) || null;
}

function updateJob(id, patch) {
  const list = loadJobs();
  const idx = list.findIndex((j) => j.id === id);
  if (idx < 0) return null;
  const prev = list[idx];
  const next = {
    ...prev,
    ...patch,
    updatedAt: nowIso(),
  };
  if (patch.state && patch.state !== prev.state) {
    next.timeline = [
      ...(prev.timeline || []),
      { at: next.updatedAt, state: patch.state, detail: patch.timelineDetail || patch.reason || '' },
    ];
  }
  delete next.timelineDetail;
  list[idx] = next;
  saveJobs(list);
  return next;
}

function createJob({ url, host, sourceLabel }) {
  const id = crypto.randomUUID();
  const createdAt = nowIso();
  const job = {
    id,
    url,
    host,
    sourceLabel: sourceLabel || '',
    state: 'pending',
    reason: null,
    extracted: null,
    opportunityId: null,
    createdAt,
    updatedAt: createdAt,
    startedAt: createdAt,
    timeline: [{ at: createdAt, state: 'pending', detail: 'queued' }],
  };
  const list = loadJobs();
  list.unshift(job);
  saveJobs(list);
  return job;
}

function listJobs(filter = {}) {
  let items = loadJobs();
  if (filter.state) {
    const states = String(filter.state)
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    if (states.length) items = items.filter((j) => states.includes(j.state));
  }
  return items;
}

function reviewQueue() {
  const rank = { needs_review: 0, valid: 1 };
  return listJobs()
    .filter((j) => j.state === 'needs_review' || j.state === 'valid')
    .sort((a, b) => (rank[a.state] ?? 9) - (rank[b.state] ?? 9) || String(b.updatedAt).localeCompare(String(a.updatedAt)));
}

function publishedJobs() {
  return readJson(jobsJsonPath(), []);
}

function dedupeKey(job) {
  if (job.notificationNo) return `n:${job.notificationNo}`;
  if (job.officialUrl) {
    return `u:${job.officialUrl.toLowerCase().split('?')[0]}|${(job.title || '').toLowerCase()}`;
  }
  return `t:${(job.organization || '').toLowerCase()}|${(job.title || '').toLowerCase()}|${job.lastDate || ''}`;
}

function findDuplicate(extracted) {
  if (!extracted?.officialUrl) return null;
  const urlKey = extracted.officialUrl.toLowerCase().split('?')[0];
  const title = String(extracted.title || '').toLowerCase();
  const id = extracted.id;
  const notificationNo = extracted.notificationNo || null;
  for (const job of publishedJobs()) {
    const sameUrl = (job.officialUrl || '').toLowerCase().split('?')[0] === urlKey;
    if (!sameUrl) continue;
    if (
      (title && String(job.title || '').toLowerCase() === title) ||
      (id && job.id === id) ||
      (notificationNo && job.notificationNo === notificationNo) ||
      dedupeKey(job) === dedupeKey(extracted)
    ) {
      return job;
    }
  }
  return null;
}

function classifyExtracted(extracted) {
  if (!extracted || !extracted.officialUrl) {
    return { state: 'invalid', reason: 'officialUrl required' };
  }
  const oppErrors = isValidOpportunity({
    ...extracted,
    status: extracted.status || computeStatus(extracted.lastDate),
  });
  const jobErrors = isValidJob(
    { ...extracted, status: extracted.status || computeStatus(extracted.lastDate) },
    { allowNeedsReview: true }
  );
  if (oppErrors.length && jobErrors.length) {
    return { state: 'invalid', reason: [...new Set([...oppErrors, ...jobErrors])].join('; ') };
  }
  const dup = findDuplicate(extracted);
  if (dup) {
    return { state: 'needs_review', reason: `duplicate_suspicion:${dup.id}` };
  }
  if (extracted.needsReview || extracted.eligibilityParse?.complete !== true || !extracted.lastDate) {
    return { state: 'needs_review', reason: 'eligibilityParse.complete=false or missing lastDate' };
  }
  return { state: 'valid', reason: null };
}

function extractFromHtml(html, pageUrl, sourceLabel) {
  const title =
    extractPageTitle(html) ||
    decodeURIComponent(pageUrl.split('/').filter(Boolean).pop() || 'Official notification');
  const links = extractLinks(html, pageUrl, { jobLikeOnly: true, limit: 20 });
  const host = hostnameOf(pageUrl);
  const organization = sourceLabel || host.replace(/^www\./, '');
  const record = toStagingRecord(
    {
      title,
      officialUrl: pageUrl,
      href: pageUrl,
      organization,
      sourceUrl: pageUrl,
      extraText: links
        .slice(0, 8)
        .map((l) => l.title)
        .join(' '),
      eligibility: ['Eligibility must be verified on the official site.'],
    },
    {
      sourceId: 'ops_paste',
      name: sourceLabel || organization,
      orgTypeDefault: 'central',
    },
    { collectedAt: nowIso(), collectorVersion: 'ops-paste-v1', listUrl: pageUrl }
  );
  record.id = stableJobId({
    organization: record.organization,
    title: record.title,
    lastDate: record.lastDate,
    officialUrl: record.officialUrl,
  });
  record.status = computeStatus(record.lastDate);
  record.eligibilityParse = { complete: false };
  record.outboundLinks = links.slice(0, 12);
  return record;
}

let fetchHtmlImpl = async (url) => {
  const out = await fetchText(url, { delayMs: 0, retries: 1, timeoutMs: 15000 });
  return { html: out.text, finalUrl: out.url || url };
};

function setFetchHtml(fn) {
  fetchHtmlImpl = fn;
}

const lastHostAt = new Map();
let processing = false;
const waiters = [];

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function waitHost(host) {
  const gap = hostGapMs();
  if (!gap) return;
  const last = lastHostAt.get(host) || 0;
  const wait = last + gap - Date.now();
  if (wait > 0) await sleep(wait);
  lastHostAt.set(host, Date.now());
}

async function processJob(id) {
  const job = findJob(id);
  if (!job || job.state !== 'pending') return job;
  updateJob(id, { state: 'pending', timelineDetail: 'fetching' });
  try {
    await waitHost(job.host);
    const { html, finalUrl } = await fetchHtmlImpl(job.url);
    if (!isAllowedHost(finalUrl)) {
      return updateJob(id, {
        state: 'invalid',
        reason: 'host_not_allowed',
        timelineDetail: 'redirect left the allowlist',
      });
    }
    const extracted = extractFromHtml(html, finalUrl, job.sourceLabel);
    const classified = classifyExtracted(extracted);
    return updateJob(id, {
      state: classified.state,
      reason: classified.reason,
      extracted,
      opportunityId: extracted.id,
      url: finalUrl,
      host: hostnameOf(finalUrl) || job.host,
      timelineDetail: classified.reason || 'extracted',
    });
  } catch (err) {
    return updateJob(id, {
      state: 'failed',
      reason: err.message || 'fetch failed',
      timelineDetail: err.message || 'fetch failed',
    });
  }
}

async function kick() {
  if (processing) return;
  processing = true;
  try {
    let job = loadJobs().find((j) => j.state === 'pending');
    while (job) {
      await processJob(job.id);
      job = loadJobs().find((j) => j.state === 'pending');
    }
  } finally {
    processing = false;
    while (waiters.length) waiters.shift()();
  }
}

function enqueue(id) {
  if (process.env.OPS_QUEUE_AUTO === 'off') return id;
  setImmediate(() => {
    kick().catch((err) => console.warn(`collectQueue: ${err.message}`));
  });
  return id;
}

function resumePending() {
  if (loadJobs().some((j) => j.state === 'pending')) enqueue('resume');
}

function submit({ url, sourceLabel }) {
  const parsed = parseHttpsUrl(url);
  if (!parsed.ok) {
    const err = new Error(parsed.error);
    err.code = parsed.reason === 'host_not_allowed' ? 'HOST' : 'VALIDATION';
    err.reason = parsed.reason || parsed.error;
    throw err;
  }
  const job = createJob({
    url: parsed.url,
    host: parsed.host,
    sourceLabel: typeof sourceLabel === 'string' ? sourceLabel.trim() : '',
  });
  enqueue(job.id);
  return job;
}

function cancel(id) {
  const job = findJob(id);
  if (!job) return null;
  if (job.state !== 'pending') {
    const err = new Error('Only pending jobs can be cancelled');
    err.code = 'STATE';
    throw err;
  }
  return updateJob(id, { state: 'cancelled', reason: 'cancelled', timelineDetail: 'cancelled by operator' });
}

function patchReview(id, facts) {
  const job = findJob(id);
  if (!job) return null;
  if (!['needs_review', 'valid', 'extracted', 'invalid'].includes(job.state)) {
    const err = new Error(`Cannot review a job in state ${job.state}`);
    err.code = 'STATE';
    throw err;
  }
  const extracted = { ...(job.extracted || {}), ...(facts || {}) };
  if (extracted.lastDate === '') extracted.lastDate = null;
  extracted.status = computeStatus(extracted.lastDate);
  if (!extracted.id) {
    extracted.id = stableJobId({
      organization: extracted.organization,
      title: extracted.title,
      lastDate: extracted.lastDate,
      officialUrl: extracted.officialUrl,
    });
  }
  if (facts && Object.prototype.hasOwnProperty.call(facts, 'eligibilityParse')) {
    extracted.eligibilityParse = facts.eligibilityParse;
  } else if (!extracted.eligibilityParse) {
    extracted.eligibilityParse = { complete: false };
  }
  const classified = classifyExtracted(extracted);
  return updateJob(id, {
    extracted,
    opportunityId: extracted.id,
    state: classified.state,
    reason: classified.reason,
    timelineDetail: 'facts patched',
  });
}

function reject(id, reason) {
  const job = findJob(id);
  if (!job) return null;
  const why = String(reason || '').trim();
  if (!why) {
    const err = new Error('reject reason is required');
    err.code = 'VALIDATION';
    throw err;
  }
  if (TERMINAL.has(job.state) && job.state !== 'published_local') {
    const err = new Error(`Cannot reject a job in state ${job.state}`);
    err.code = 'STATE';
    throw err;
  }
  return updateJob(id, { state: 'rejected', reason: why, timelineDetail: why });
}

let lastPublishAt = 0;

async function debouncePublish() {
  const gap = publishDebounceMs();
  if (!gap) return;
  const wait = lastPublishAt + gap - Date.now();
  if (wait > 0) await sleep(wait);
  lastPublishAt = Date.now();
}

function upsertProcessed(record) {
  const jobRow = {
    ...record,
    status: computeStatus(record.lastDate),
    needsReview: false,
    updatedAt: nowIso(),
  };
  const errors = isValidJob(jobRow);
  if (errors.length) {
    const err = new Error(errors.join('; '));
    err.code = 'VALIDATION';
    throw err;
  }
  const jobs = readJson(jobsJsonPath(), []);
  const idx = jobs.findIndex((j) => j.id === jobRow.id);
  if (idx >= 0) jobs[idx] = { ...jobs[idx], ...jobRow };
  else jobs.unshift(jobRow);
  writeAtomic(jobsJsonPath(), jobs);

  const opps = readJson(opportunitiesJsonPath(), []);
  if (Array.isArray(opps)) {
    const oidx = opps.findIndex((j) => j.id === jobRow.id);
    const opp = { ...jobRow, kind: 'opportunity' };
    if (oidx >= 0) opps[oidx] = { ...opps[oidx], ...opp };
    else opps.unshift(opp);
    writeAtomic(opportunitiesJsonPath(), opps);
  }
  return jobRow;
}

function writeStaging(record) {
  const dir = stagingDir();
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, `${record.id}.json`);
  writeAtomic(file, record);
  return file;
}

function repoParts() {
  const slug = process.env.GITHUB_REPOSITORY || process.env.OPS_GITHUB_REPOSITORY || '';
  const [owner, repo] = slug.split('/');
  return { owner, repo };
}

async function ingestViaContentsApi(relPath, content, message) {
  const token = process.env.OPS_INGEST_TOKEN || process.env.GITHUB_TOKEN;
  const { owner, repo } = repoParts();
  if (!token || !owner || !repo) return { ok: false, reason: 'no_token' };
  const branch = process.env.OPS_INGEST_BRANCH || 'master';
  const api = `https://api.github.com/repos/${owner}/${repo}/contents/${relPath.replace(/\\/g, '/')}`;
  const headers = {
    Authorization: `Bearer ${token}`,
    Accept: 'application/vnd.github+json',
    'User-Agent': 'GovtJobsPortal-ops-ingest',
  };
  let sha;
  const existing = await fetch(`${api}?ref=${encodeURIComponent(branch)}`, { headers });
  if (existing.ok) {
    const body = await existing.json();
    sha = body.sha;
  }
  const res = await fetch(api, {
    method: 'PUT',
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      message,
      content: Buffer.from(content, 'utf8').toString('base64'),
      branch,
      sha,
    }),
  });
  if (!res.ok) {
    const text = await res.text();
    return { ok: false, reason: `contents_api_${res.status}`, detail: text.slice(0, 300) };
  }
  return { ok: true, method: 'contents' };
}

async function ingestViaDispatch(relPath, content, jobId) {
  const token = process.env.OPS_INGEST_TOKEN || process.env.GITHUB_TOKEN;
  const { owner, repo } = repoParts();
  if (!token || !owner || !repo) return { ok: false, reason: 'no_token' };
  const res = await fetch(`https://api.github.com/repos/${owner}/${repo}/dispatches`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'User-Agent': 'GovtJobsPortal-ops-ingest',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      event_type: 'ops-ingest',
      client_payload: { jobId, path: relPath, content },
    }),
  });
  if (!res.ok && res.status !== 204) {
    return { ok: false, reason: `dispatch_${res.status}` };
  }
  return { ok: true, method: 'dispatch' };
}

async function publish(id) {
  const job = findJob(id);
  if (!job) return null;
  if (!['valid', 'needs_review', 'published_local'].includes(job.state)) {
    const err = new Error(`Cannot publish a job in state ${job.state}`);
    err.code = 'STATE';
    throw err;
  }
  if (!job.extracted) {
    const err = new Error('No extracted facts to publish');
    err.code = 'VALIDATION';
    throw err;
  }
  await debouncePublish();
  const record = {
    ...job.extracted,
    sourceId: 'ops_paste',
    sourceName: job.sourceLabel || job.extracted.sourceName || 'Ops paste',
    collectorVersion: 'ops-paste-v1',
    status: computeStatus(job.extracted.lastDate),
    needsReview: false,
  };
  if (!record.id) {
    record.id = stableJobId({
      organization: record.organization,
      title: record.title,
      lastDate: record.lastDate,
      officialUrl: record.officialUrl,
    });
  }
  const stagingPath = writeStaging(record);
  upsertProcessed(record);
  const relPath = path.relative(root, stagingPath).replace(/\\/g, '/');
  const content = fs.readFileSync(stagingPath, 'utf8');
  const message = `chore(ops): ingest paste ${record.id}`;
  let ingest = await ingestViaContentsApi(relPath, content, message);
  if (!ingest.ok && ingest.reason !== 'no_token') {
    ingest = await ingestViaDispatch(relPath, content, job.id);
  }
  if (ingest.ok) {
    return updateJob(id, {
      state: 'published',
      opportunityId: record.id,
      extracted: record,
      reason: null,
      timelineDetail: `committed ${relPath} via ${ingest.method}`,
    });
  }
  return updateJob(id, {
    state: 'published_local',
    opportunityId: record.id,
    extracted: record,
    reason: 'not in git SoR until staging file is committed',
    timelineDetail: 'published_local — no git token',
  });
}

module.exports = {
  EXTRA_HOSTS,
  isAllowedHost,
  parseHttpsUrl,
  submit,
  cancel,
  findJob,
  listJobs,
  reviewQueue,
  patchReview,
  reject,
  publish,
  processJob,
  resumePending,
  setFetchHtml,
  extractFromHtml,
  classifyExtracted,
  jobsPath,
};
