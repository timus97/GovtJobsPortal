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
  SELECTION_PROCESSES,
} = require(path.join(root, 'shared', 'jobSchema'));
const {
  isCuetName,
  looksLikeCalendarRow,
  normalizeExamSeries,
  isValidExamSeries,
  seriesMatchesJob,
} = require(path.join(root, 'shared', 'examSeriesSchema'));

const paths = {
  seed: path.join(root, 'data', 'seed', 'jobs.json'),
  seriesSeed: path.join(root, 'data', 'seed', 'exam_series.json'),
  overrides: path.join(root, 'data', 'seed', 'overrides.json'),
  aliases: path.join(root, 'data', 'sources', 'org_aliases.json'),
  stagingDir: path.join(root, 'data', 'staging'),
  processedDir: path.join(root, 'data', 'processed'),
  jobsOut: path.join(root, 'data', 'processed', 'jobs.json'),
  opportunitiesOut: path.join(root, 'data', 'processed', 'opportunities.json'),
  seriesOut: path.join(root, 'data', 'processed', 'exam_series.json'),
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

  const curatedExam =
    hasExam === true && SELECTION_OK(selectionProcess) && raw.officialUrl && raw.title;
  const curatedNoExam =
    hasExam === false && SELECTION_OK(selectionProcess) && raw.officialUrl && raw.title;

  // Curated exam (hasExam:true + valid exam/no-exam code + url + title): trust the curator.
  // CBT/GATE in the summary is expected for exam seeds.
  // Curated no-exam: trust the curator — summaries often say "no GATE/CBT"
  // which would false-positive on exam keywords.
  if (!curatedExam && !curatedNoExam) {
    const classified = classifySelectionText(selectionText);
    if (classified.hasExam === true) {
      hasExam = true;
      if (!SELECTION_OK(selectionProcess) && classified.selectionProcess) {
        selectionProcess = classified.selectionProcess;
      }
      if (!SELECTION_OK(selectionProcess)) {
        needsReview = true;
        selectionProcess = classified.selectionProcess || selectionProcess || 'written_multi_stage';
      }
    } else if (!SELECTION_OK(selectionProcess)) {
      if (classified.selectionProcess) {
        selectionProcess = classified.selectionProcess;
        hasExam = false;
      } else {
        needsReview = true;
        selectionProcess = selectionProcess || 'interview_only';
        hasExam = false;
      }
    } else if (hasExam !== true) {
      hasExam = false;
    }
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
    hasExam: Boolean(hasExam === true),
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
    examSeriesId: raw.examSeriesId || null,
    examDate: raw.examDate || null,
    collectedAt: raw.collectedAt || now,
    collectorVersion: raw.collectorVersion || 'process-v1',
    updatedAt: now,
  };

  const isScrape = String(raw.collectorVersion || '').match(/scrape|playwright|pdf/i);

  // Scrape rows without lastDate can still publish if selection is clear.
  // Unknown/ambiguous selection stays in quarantine for human review.
  if (needsReview && isScrape && SELECTION_OK(selectionProcess)) {
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
  return SELECTION_PROCESSES.includes(code);
}

function dedupeKey(job) {
  if (job.notificationNo) return `n:${job.notificationNo}`;
  if (job.officialUrl) return `u:${job.officialUrl.toLowerCase().split('?')[0]}|${(job.title || '').toLowerCase()}`;
  return `t:${(job.organization || '').toLowerCase()}|${(job.title || '').toLowerCase()}|${job.lastDate || ''}`;
}

function sortPublished(a, b) {
  const statusRank = { closing_soon: 0, open: 1, closed: 2 };
  const sr = (statusRank[a.status] ?? 9) - (statusRank[b.status] ?? 9);
  if (sr !== 0) return sr;
  const ad = a.lastDate || '9999';
  const bd = b.lastDate || '9999';
  if (ad !== bd) return ad.localeCompare(bd);
  return (b.notificationDate || '').localeCompare(a.notificationDate || '');
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
    examSeries: reportMeta.examSeries || 0,
  };
}

function boardFromSource(raw) {
  const blob = `${raw.sourceId || ''} ${raw.organization || ''} ${raw.sourceName || ''}`.toLowerCase();
  if (/\bupsc\b/.test(blob)) return 'UPSC';
  if (/\bssc\b|staff selection/.test(blob)) return 'SSC';
  if (/\bibps\b/.test(blob)) return 'IBPS';
  if (/\bsbi\b|state bank/.test(blob)) return 'SBI';
  if (/\brrb\b|railway recruitment/.test(blob)) return 'RRB';
  if (/\bnta\b|ugc\s*net/.test(blob)) return 'NTA';
  return null;
}

function seriesFromCalendarRaw(raw, now) {
  const name = raw.title || raw.name;
  if (!name || isCuetName(name)) return null;
  const board = raw.board || boardFromSource(raw);
  if (!board) return null;
  return normalizeExamSeries(
    {
      board,
      name,
      cycle: raw.cycle || null,
      sourceId: raw.sourceId,
      officialUrl: raw.officialUrl,
      expectedNotify: raw.notificationDate,
      expectedApply: raw.lastDate,
      expectedExam: raw.examDate,
      minEducation: raw.qualification || raw.minEducation,
    },
    now
  );
}

function mergeSeries(existing, incoming) {
  if (!existing) return incoming;
  return {
    ...existing,
    ...Object.fromEntries(
      Object.entries(incoming).filter(([, v]) => v != null && v !== '' && !(Array.isArray(v) && !v.length))
    ),
    aliases: [...new Set([...(existing.aliases || []), ...(incoming.aliases || [])])],
    linkedOpportunityIds: [...new Set([...(existing.linkedOpportunityIds || []), ...(incoming.linkedOpportunityIds || [])])],
  };
}

function buildExamSeries(seedSeries, calendarRaws, jobs, now) {
  const byId = new Map();
  for (const raw of seedSeries) {
    const series = normalizeExamSeries(raw, now);
    if (!series) continue;
    if (isValidExamSeries(series).length) continue;
    byId.set(series.id, series);
  }
  for (const raw of calendarRaws) {
    const series = seriesFromCalendarRaw(raw, now);
    if (!series) continue;
    if (isValidExamSeries(series).length) continue;
    const prev = [...byId.values()].find((s) => seriesMatchesJob(s, { title: series.name, organization: series.board, sourceId: series.sourceId })) || byId.get(series.id);
    if (prev) {
      byId.set(prev.id, mergeSeries(prev, { ...series, id: prev.id }));
    } else {
      byId.set(series.id, series);
    }
  }

  for (const job of jobs) {
    const hit = [...byId.values()].find((s) => seriesMatchesJob(s, job));
    if (!hit) continue;
    job.examSeriesId = hit.id;
    const openApply = job.status === 'open' || job.status === 'closing_soon';
    if (openApply && job.lastDate && !hit.applyNever) {
      hit.linkedOpportunityIds = [...new Set([...(hit.linkedOpportunityIds || []), job.id])];
    }
  }

  return [...byId.values()].sort((a, b) => {
    const board = String(a.board).localeCompare(String(b.board));
    if (board !== 0) return board;
    return String(a.name).localeCompare(String(b.name));
  });
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
  const calendarRaws = [];
  let droppedExam = 0;
  let skippedCalendar = 0;
  let deduped = 0;

  const map = new Map();

  for (const raw of incoming) {
    if (overrides.forceExcludeIds?.includes(raw.id)) {
      droppedExam += 1;
      continue;
    }
    if (isCuetName(raw.title || raw.name)) {
      droppedExam += 1;
      continue;
    }
    if (looksLikeCalendarRow(raw) && !raw.lastDate) {
      calendarRaws.push(raw);
      skippedCalendar += 1;
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
    if (fix) {
      job = { ...job, ...fix, updatedAt: startedAt };
      job.hasExam = Boolean(job.hasExam === true);
    }

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

  published.sort(sortPublished);

  let keptPublished = 0;
  const previous = readJson(paths.jobsOut, []);
  const replacePublished =
    process.env.REPLACE_PUBLISHED === '1' || process.argv.includes('--replace-published');
  if (!replacePublished && previous.length > published.length) {
    const seen = new Set(published.map(dedupeKey));
    for (const job of previous) {
      const key = dedupeKey(job);
      if (seen.has(key)) continue;
      const errors = isValidJob(job);
      if (errors.length) continue;
      published.push(job);
      seen.add(key);
      keptPublished += 1;
    }
    if (keptPublished) {
      console.warn(
        `Kept ${keptPublished} previously published jobs (incoming set was smaller). Set REPLACE_PUBLISHED=1 to replace instead.`
      );
      published.sort(sortPublished);
    }
  }

  const finishedAt = new Date().toISOString();
  const registry = readJson(path.join(root, 'data', 'sources', 'registry.json'), { sources: [] });
  const sourcesMonitored = (registry.sources || []).filter((s) => s.enabled).length;

  const seedSeries = readJson(paths.seriesSeed, []);
  const examSeries = buildExamSeries(seedSeries, calendarRaws, published, finishedAt);

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
    skippedCalendar,
    keptPublished,
    examSeries: examSeries.length,
    deduped,
    sourcesMonitored,
  };

  const stats = buildStats(published, { ...report, examSeries: examSeries.length });

  const writeAtomic = (file, data) => {
    const tmp = `${file}.${process.pid}.tmp`;
    fs.writeFileSync(tmp, JSON.stringify(data, null, 2), 'utf8');
    fs.renameSync(tmp, file);
  };

  const opportunities = published.map((job) => ({
    ...job,
    kind: 'opportunity',
    examSeriesId: job.examSeriesId || null,
  }));

  writeAtomic(paths.jobsOut, published);
  writeAtomic(paths.opportunitiesOut, opportunities);
  writeAtomic(paths.seriesOut, examSeries);
  writeAtomic(paths.statsOut, stats);
  writeAtomic(paths.quarantineOut, quarantine);
  writeAtomic(paths.reportOut, report);

  console.log('Pipeline complete');
  console.log(JSON.stringify(report, null, 2));
  console.log(`Wrote ${published.length} jobs → data/processed/jobs.json`);
  console.log(`Wrote ${examSeries.length} exam series → data/processed/exam_series.json`);
}

if (require.main === module) {
  main();
}

module.exports = { buildExamSeries };
