/**
 * Collect staging + seed → classify → filter → dedupe → expire → publish jobs.json
 */
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..', '..');
const {
  classifySelectionText,
  isValidJob,
  computeStatus,
  stableJobId,
  ORG_TYPES,
} = require(path.join(root, 'shared', 'jobSchema'));

const paths = {
  seed: path.join(root, 'data', 'seed', 'jobs.json'),
  overrides: path.join(root, 'data', 'seed', 'overrides.json'),
  aliases: path.join(root, 'data', 'sources', 'org_aliases.json'),
  stagingDir: path.join(root, 'data', 'staging'),
  processedDir: path.join(root, 'data', 'processed'),
  jobsOut: path.join(root, 'data', 'processed', 'jobs.json'),
  statsOut: path.join(root, 'data', 'processed', 'stats.json'),
  quarantineOut: path.join(root, 'data', 'processed', 'quarantine.json'),
  reportOut: path.join(root, 'data', 'processed', 'run-report.json'),
};

function readJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return fallback;
  }
}

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function loadStagingRecords() {
  const records = [];
  if (!fs.existsSync(paths.stagingDir)) return records;
  const walk = (dir) => {
    for (const name of fs.readdirSync(dir)) {
      const full = path.join(dir, name);
      const stat = fs.statSync(full);
      if (stat.isDirectory()) walk(full);
      else if (name.endsWith('.json') || name.endsWith('.jsonl')) {
        const text = fs.readFileSync(full, 'utf8');
        if (name.endsWith('.jsonl')) {
          for (const line of text.split('\n')) {
            if (!line.trim()) continue;
            try {
              records.push(JSON.parse(line));
            } catch {
              /* skip bad line */
            }
          }
        } else {
          const data = JSON.parse(text);
          if (Array.isArray(data)) records.push(...data);
          else if (data && Array.isArray(data.records)) records.push(...data.records);
          else if (data) records.push(data);
        }
      }
    }
  };
  walk(paths.stagingDir);
  return records;
}

function normalizeOrg(name, aliases) {
  const key = String(name || '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
  for (const [alias, meta] of Object.entries(aliases.aliases || {})) {
    if (key.includes(alias)) {
      return {
        organization: meta.name,
        orgType: meta.orgType,
        sector: meta.sector,
      };
    }
  }
  return null;
}

function enrichRecord(raw, aliases, collectedAt) {
  const selectionText = [
    raw.summary,
    raw.selectionProcess,
    ...(raw.processSteps || []),
    ...(raw.eligibility || []),
  ].join(' ');

  let selectionProcess = raw.selectionProcess;
  let hasExam = raw.hasExam;
  let needsReview = Boolean(raw.needsReview);

  // Explicit exam flag always drops
  if (hasExam === true) {
    return { drop: true, reason: 'hasExam_true', raw };
  }

  const curated =
    hasExam === false && SELECTION_OK(selectionProcess) && raw.officialUrl && raw.title;

  // For automated/incomplete rows, classify from text.
  // For curated seed (hasExam:false + valid selectionProcess), trust the curator —
  // summaries often say "no GATE/CBT" which would false-positive on exclude keywords.
  if (!curated) {
    const classified = classifySelectionText(selectionText);
    if (classified.hasExam === true) {
      return { drop: true, reason: 'exclude_keyword', raw };
    }
    if (!SELECTION_OK(selectionProcess)) {
      if (classified.selectionProcess) {
        selectionProcess = classified.selectionProcess;
        hasExam = false;
      } else {
        needsReview = true;
        selectionProcess = selectionProcess || 'interview_only';
        hasExam = false;
      }
    } else {
      hasExam = false;
    }
  } else {
    hasExam = false;
  }

  const aliasHit = normalizeOrg(raw.organization, aliases);
  const organization = aliasHit?.organization || raw.organization;
  let orgType = raw.orgType || aliasHit?.orgType || 'central';
  if (!ORG_TYPES.includes(orgType)) orgType = 'central';
  const sector = raw.sector || aliasHit?.sector || 'Other';

  const officialUrl = raw.officialUrl;
  const lastDate = raw.lastDate || null;
  const id =
    raw.id ||
    stableJobId({
      organization,
      title: raw.title,
      lastDate,
      officialUrl,
    });

  const status = computeStatus(lastDate);
  const now = collectedAt || new Date().toISOString();

  const job = {
    id,
    title: String(raw.title || '').trim(),
    organization,
    orgType,
    sector,
    location: raw.location || 'All India',
    vacancies: raw.vacancies ?? null,
    qualification: raw.qualification || null,
    experience: raw.experience || null,
    salary: raw.salary || null,
    selectionProcess,
    hasExam: false,
    applicationMode: raw.applicationMode || null,
    notificationDate: raw.notificationDate || null,
    lastDate,
    walkInDate: raw.walkInDate || null,
    officialUrl,
    sourceId: raw.sourceId || 'seed_manual',
    sourceName: raw.sourceName || 'Manual curator seed',
    sourceUrl: raw.sourceUrl || officialUrl || '',
    summary: raw.summary || '',
    eligibility: Array.isArray(raw.eligibility) ? raw.eligibility : [],
    processSteps: Array.isArray(raw.processSteps) ? raw.processSteps : [],
    documentsRequired: Array.isArray(raw.documentsRequired) ? raw.documentsRequired : [],
    status,
    needsReview,
    notificationNo: raw.notificationNo || null,
    collectedAt: raw.collectedAt || now,
    collectorVersion: raw.collectorVersion || 'process-v1',
    updatedAt: now,
  };

  const isScrape = String(raw.collectorVersion || '').match(/scrape|playwright|pdf/i);

  // Scrape rows without lastDate can still publish if selection is clear and no exam.
  // Unknown/ambiguous selection stays in quarantine for human review.
  if (needsReview && isScrape && SELECTION_OK(selectionProcess) && hasExam !== true) {
    // Clear review when collector already inferred a no-exam selection type
    if (!/unknown|review/i.test(String(raw.summary || ''))) {
      needsReview = false;
      job.needsReview = false;
    }
  }

  if (needsReview) {
    return { quarantine: true, job, reason: 'needs_review' };
  }

  const errors = isValidJob(job);
  if (errors.length) {
    return { quarantine: true, job, reason: errors.join('; ') };
  }

  return { ok: true, job };
}

function SELECTION_OK(code) {
  return [
    'walk_in',
    'interview_only',
    'merit',
    'contract_interview',
    'direct_recruitment',
    'apprenticeship',
  ].includes(code);
}

function dedupeKey(job) {
  if (job.notificationNo) return `n:${job.notificationNo}`;
  if (job.officialUrl) return `u:${job.officialUrl.toLowerCase().split('?')[0]}|${(job.title || '').toLowerCase()}`;
  return `t:${(job.organization || '').toLowerCase()}|${(job.title || '').toLowerCase()}|${job.lastDate || ''}`;
}

function richer(a, b) {
  const score = (j) =>
    (j.summary ? 2 : 0) +
    (j.eligibility?.length || 0) +
    (j.processSteps?.length || 0) +
    (j.vacancies ? 1 : 0);
  return score(a) >= score(b) ? a : b;
}

function buildStats(jobs, reportMeta) {
  const byOrgType = {};
  const bySector = {};
  const byStatus = {};
  const bySource = {};
  const bySelection = {};
  for (const j of jobs) {
    byOrgType[j.orgType] = (byOrgType[j.orgType] || 0) + 1;
    bySector[j.sector] = (bySector[j.sector] || 0) + 1;
    byStatus[j.status] = (byStatus[j.status] || 0) + 1;
    bySource[j.sourceId] = (bySource[j.sourceId] || 0) + 1;
    bySelection[j.selectionProcess] = (bySelection[j.selectionProcess] || 0) + 1;
  }
  return {
    total: jobs.length,
    open: byStatus.open || 0,
    closingSoon: byStatus.closing_soon || 0,
    closed: byStatus.closed || 0,
    byOrgType,
    bySector,
    byStatus,
    bySource,
    bySelection,
    lastPipelineRunAt: reportMeta.finishedAt,
    sourcesMonitored: reportMeta.sourcesMonitored,
  };
}

function main() {
  const startedAt = new Date().toISOString();
  const t0 = Date.now();
  ensureDir(paths.processedDir);

  const seed = readJson(paths.seed, []);
  const overrides = readJson(paths.overrides, {});
  const aliases = readJson(paths.aliases, { aliases: {} });
  const staging = loadStagingRecords();
  const incoming = [...seed, ...staging];

  const published = [];
  const quarantine = [];
  let droppedExam = 0;
  let deduped = 0;

  const map = new Map();

  for (const raw of incoming) {
    if (overrides.forceExcludeIds?.includes(raw.id)) {
      droppedExam += 1;
      continue;
    }

    const result = enrichRecord(raw, aliases, startedAt);
    if (result.drop) {
      droppedExam += 1;
      continue;
    }
    if (result.quarantine) {
      quarantine.push({ reason: result.reason, job: result.job });
      continue;
    }

    let job = result.job;
    const fix = overrides.fieldFixes?.[job.id];
    if (fix) job = { ...job, ...fix, hasExam: false, updatedAt: startedAt };

    if (overrides.forceIncludeIds?.includes(job.id)) {
      job.needsReview = false;
    }

    const key = dedupeKey(job);
    if (map.has(key)) {
      deduped += 1;
      map.set(key, richer(map.get(key), job));
    } else {
      map.set(key, job);
    }
  }

  for (const job of map.values()) {
    job.status = computeStatus(job.lastDate);
    job.needsReview = false;
    const errors = isValidJob(job);
    if (errors.length) {
      quarantine.push({ reason: errors.join('; '), job });
    } else {
      published.push(job);
    }
  }

  published.sort((a, b) => {
    const statusRank = { closing_soon: 0, open: 1, closed: 2 };
    const sr = (statusRank[a.status] ?? 9) - (statusRank[b.status] ?? 9);
    if (sr !== 0) return sr;
    const ad = a.lastDate || '9999';
    const bd = b.lastDate || '9999';
    if (ad !== bd) return ad.localeCompare(bd);
    return (b.notificationDate || '').localeCompare(a.notificationDate || '');
  });

  const finishedAt = new Date().toISOString();
  const registry = readJson(path.join(root, 'data', 'sources', 'registry.json'), { sources: [] });
  const sourcesMonitored = (registry.sources || []).filter((s) => s.enabled).length;

  const report = {
    startedAt,
    finishedAt,
    durationMs: Date.now() - t0,
    inputSeed: seed.length,
    inputStaging: staging.length,
    inputTotal: incoming.length,
    published: published.length,
    quarantine: quarantine.length,
    droppedExam,
    deduped,
    sourcesMonitored,
  };

  const stats = buildStats(published, report);

  const writeAtomic = (file, data) => {
    const tmp = `${file}.${process.pid}.tmp`;
    fs.writeFileSync(tmp, JSON.stringify(data, null, 2), 'utf8');
    fs.renameSync(tmp, file);
  };

  writeAtomic(paths.jobsOut, published);
  writeAtomic(paths.statsOut, stats);
  writeAtomic(paths.quarantineOut, quarantine);
  writeAtomic(paths.reportOut, report);

  console.log('Pipeline complete');
  console.log(JSON.stringify(report, null, 2));
  console.log(`Wrote ${published.length} jobs → data/processed/jobs.json`);
}

main();
