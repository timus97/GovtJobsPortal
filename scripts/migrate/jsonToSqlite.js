/**
 * Rebuild the optional Express SQLite read cache from git JSON.
 * Not a second writer. Not run in GHA. Safe to re-run on every boot.
 *
 *   node scripts/migrate/jsonToSqlite.js
 */
const fs = require('fs');
const path = require('path');
const sqlite = require('../../server/src/db/sqlite');
const { normalizeQualification } = require('../../shared/eligibilityFacts');

const root = path.join(__dirname, '..', '..');
const jobsPath = path.join(root, 'data', 'processed', 'jobs.json');
const opportunitiesPath = path.join(root, 'data', 'processed', 'opportunities.json');
const examSeriesPath = path.join(root, 'data', 'processed', 'exam_series.json');
const collectJobsPath = path.join(root, 'data', 'processed', 'collect-jobs.json');
const collectReportPath = path.join(root, 'data', 'processed', 'collect-report.json');
const registryPath = path.join(root, 'data', 'sources', 'registry.json');

const PRIORITIES = new Set(['P0', 'P1', 'P2', 'P3']);
const FINGERPRINT_FILES = [
  jobsPath,
  opportunitiesPath,
  examSeriesPath,
  collectJobsPath,
  registryPath,
  collectReportPath,
];

function readJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return fallback;
  }
}

function asArray(value) {
  if (Array.isArray(value)) return value;
  if (value && Array.isArray(value.items)) return value.items;
  if (value && Array.isArray(value.jobs)) return value.jobs;
  return [];
}

function mapSource(source, collectById, collectFinishedAt) {
  const run = collectById[source.sourceId] || null;
  const priority = PRIORITIES.has(source.priority) ? source.priority : 'P3';
  const listUrls = Array.isArray(source.listUrls) ? source.listUrls.filter(Boolean) : [];
  return {
    id: source.sourceId,
    name: source.name || source.sourceId,
    base_url: source.baseUrl || '',
    list_urls: JSON.stringify(listUrls),
    enabled: source.enabled ? 1 : 0,
    cadence: source.cadence || null,
    category: source.category || null,
    priority,
    collector: source.collector || source.method || 'none',
    method: source.method || 'unknown',
    auto_publish: source.autoPublish ? 1 : 0,
    rate_limit_ms: Number.isFinite(source.rateLimitMs) ? source.rateLimitMs : 5000,
    last_run_at: run ? collectFinishedAt || run.finishedAt || null : null,
    last_status: run ? run.status || (run.ok ? 'ok' : 'error') : null,
    robots_notes: source.robotsNotes || null,
  };
}

function stubSourceFromJob(job) {
  return {
    id: job.sourceId,
    name: job.sourceName || job.sourceId,
    base_url: job.sourceUrl || '',
    list_urls: '[]',
    enabled: 0,
    cadence: null,
    category: null,
    priority: 'P3',
    collector: 'none',
    method: 'unknown',
    auto_publish: 0,
    rate_limit_ms: 5000,
    last_run_at: null,
    last_status: null,
    robots_notes: null,
  };
}

function eligibilityComplete(item, minEducation) {
  const ageMin = item.ageMin ?? item.age_min;
  const ageMax = item.ageMax ?? item.age_max;
  const ageAsOn = item.ageAsOnDate || item.age_as_on;
  const close = item.applicationClose || item.lastDate || item.application_close;
  return Boolean(ageMin != null && ageMax != null && ageAsOn && minEducation && close);
}

function mapOpportunity(item) {
  const minEducation = normalizeQualification(item.minEducation || item.qualification);
  const parseComplete =
    item.eligibilityParse && typeof item.eligibilityParse.complete === 'boolean'
      ? item.eligibilityParse.complete
      : eligibilityComplete(item, minEducation);
  const facts = {
    facts: {
      minEducation,
      ageMin: item.ageMin ?? null,
      ageMax: item.ageMax ?? null,
      ageAsOnDate: item.ageAsOnDate || null,
    },
    parse: { complete: parseComplete },
  };
  const now = new Date().toISOString();
  return {
    id: item.id,
    source_id: item.sourceId || item.source_id || null,
    exam_series_id: item.examSeriesId || item.exam_series_id || null,
    title: item.title,
    board: item.board || item.organization || null,
    has_exam: item.hasExam === true ? 1 : 0,
    selection_process: item.selectionProcess || item.selection_process || 'direct_recruitment',
    selection_processes: Array.isArray(item.selectionProcesses)
      ? JSON.stringify(item.selectionProcesses)
      : null,
    application_open: item.applicationOpen || item.notificationDate || null,
    application_close: item.applicationClose || item.lastDate || null,
    status: item.status || 'open',
    official_url: item.officialUrl || item.official_url,
    notification_url: item.notificationUrl || item.notification_url || null,
    age_min: item.ageMin ?? null,
    age_max: item.ageMax ?? null,
    age_as_on: item.ageAsOnDate || null,
    min_education: minEducation,
    discipline: item.discipline || item.educationDiscipline || null,
    gender_required: item.genderRequired || null,
    domicile_required:
      item.domicileRequired == null ? null : item.domicileRequired ? 1 : 0,
    domicile_states: Array.isArray(item.domicileStates)
      ? JSON.stringify(item.domicileStates)
      : null,
    pwbd_allowed: item.pwbdAllowed == null ? null : item.pwbdAllowed ? 1 : 0,
    reserved_only: item.reservedOnly == null ? null : item.reservedOnly ? 1 : 0,
    open_to_categories: Array.isArray(item.openToCategories)
      ? JSON.stringify(item.openToCategories)
      : null,
    reservation_notes: item.reservationNotes || null,
    eligibility_json: JSON.stringify(facts),
    raw: JSON.stringify(item),
    published_at: item.publishedAt || item.collectedAt || null,
    updated_at: item.updatedAt || now,
  };
}

function mapExamSeries(item) {
  return {
    id: item.id,
    source_id: item.sourceId || item.source_id || null,
    board: item.board,
    name: item.name,
    cycle: item.cycle || null,
    expected_notify: item.expectedNotify || item.expected_notify || null,
    expected_apply: item.expectedApply || item.expected_apply || null,
    expected_exam: item.expectedExam || item.expected_exam || null,
    official_url: item.officialUrl || item.official_url,
    raw: JSON.stringify(item),
    updated_at: item.updatedAt || item.updated_at || new Date().toISOString(),
  };
}

function mapCollectJob(item) {
  const extracted =
    item.extracted == null
      ? null
      : typeof item.extracted === 'string'
        ? item.extracted
        : JSON.stringify(item.extracted);
  return {
    id: item.id,
    url: item.url,
    host: item.host || '',
    state: item.state || 'pending',
    reason: item.reason || null,
    extracted,
    opportunity_id: item.opportunityId || item.opportunity_id || null,
    created_at: item.createdAt || item.created_at || new Date().toISOString(),
    updated_at: item.updatedAt || item.updated_at || new Date().toISOString(),
  };
}

function rebuildCache({ dbPath } = {}) {
  if (!sqlite.isEnabled()) {
    sqlite.setStatus('missing');
    return { status: 'off', read: {}, upserted: {} };
  }
  if (!sqlite.nativeAvailable()) {
    sqlite.setStatus('missing');
    const err = sqlite.getLoadError();
    return {
      status: 'missing',
      reason: err ? err.message : 'better-sqlite3 not installed',
      read: {},
      upserted: {},
    };
  }

  let database;
  try {
    database = sqlite.open(dbPath || sqlite.getDbPath());
  } catch (err) {
    sqlite.setStatus('missing');
    return { status: 'missing', reason: err.message, read: {}, upserted: {} };
  }
  if (!database) {
    sqlite.setStatus('missing');
    return { status: 'missing', reason: 'failed to open cache', read: {}, upserted: {} };
  }

  const registry = readJson(registryPath, { sources: [] });
  const collectReport = readJson(collectReportPath, null);
  const collectById = Object.fromEntries(
    (collectReport?.results || []).map((row) => [row.sourceId, row])
  );
  const collectFinishedAt = collectReport?.finishedAt || null;

  const jobs = asArray(readJson(jobsPath, []));
  const opportunities = asArray(readJson(opportunitiesPath, []));
  const examSeries = asArray(readJson(examSeriesPath, []));
  const collectJobs = asArray(readJson(collectJobsPath, []));

  const sourceRows = (registry.sources || [])
    .filter((source) => source && source.sourceId)
    .map((source) => mapSource(source, collectById, collectFinishedAt));
  const knownSources = new Set(sourceRows.map((row) => row.id));

  const catalogItems = [...jobs, ...opportunities];
  for (const item of catalogItems) {
    if (item && item.sourceId && !knownSources.has(item.sourceId)) {
      sourceRows.push(stubSourceFromJob(item));
      knownSources.add(item.sourceId);
    }
  }

  const opportunityRows = [];
  const postRows = [];
  for (const item of jobs) {
    if (!item || !item.id || !item.title || !item.officialUrl) continue;
    opportunityRows.push(mapOpportunity(item));
    for (const post of item.posts || []) {
      if (!post || !post.title) continue;
      postRows.push({
        id: `${item.id}:${post.id || post.title}`,
        opportunity_id: item.id,
        title: post.title,
        pwbd_allowed: post.pwbdAllowed == null ? null : post.pwbdAllowed ? 1 : 0,
        pwbd_categories: Array.isArray(post.pwbdCategories)
          ? JSON.stringify(post.pwbdCategories)
          : null,
        reserved_only: post.reservedOnly == null ? null : post.reservedOnly ? 1 : 0,
        open_to_categories: Array.isArray(post.openToCategories)
          ? JSON.stringify(post.openToCategories)
          : null,
      });
    }
  }
  for (const item of opportunities) {
    if (!item || !item.id || !item.title || !(item.officialUrl || item.official_url)) continue;
    opportunityRows.push(mapOpportunity(item));
  }

  const examRows = examSeries
    .filter((item) => item && item.id && item.board && item.name && (item.officialUrl || item.official_url))
    .map(mapExamSeries);
  const collectRows = collectJobs.filter((item) => item && item.id && item.url).map(mapCollectJob);

  const upsertSource = database.prepare(`
    INSERT OR REPLACE INTO sources (
      id, name, base_url, list_urls, enabled, cadence, category, priority,
      collector, method, auto_publish, rate_limit_ms, last_run_at, last_status, robots_notes
    ) VALUES (
      @id, @name, @base_url, @list_urls, @enabled, @cadence, @category, @priority,
      @collector, @method, @auto_publish, @rate_limit_ms, @last_run_at, @last_status, @robots_notes
    )
  `);
  const upsertExam = database.prepare(`
    INSERT OR REPLACE INTO exam_series (
      id, source_id, board, name, cycle, expected_notify, expected_apply, expected_exam,
      official_url, raw, updated_at
    ) VALUES (
      @id, @source_id, @board, @name, @cycle, @expected_notify, @expected_apply, @expected_exam,
      @official_url, @raw, @updated_at
    )
  `);
  const upsertOpp = database.prepare(`
    INSERT OR REPLACE INTO opportunities (
      id, source_id, exam_series_id, title, board, has_exam, selection_process, selection_processes,
      application_open, application_close, status, official_url, notification_url,
      age_min, age_max, age_as_on, min_education, discipline, gender_required,
      domicile_required, domicile_states, pwbd_allowed, reserved_only, open_to_categories,
      reservation_notes, eligibility_json, raw, published_at, updated_at
    ) VALUES (
      @id, @source_id, @exam_series_id, @title, @board, @has_exam, @selection_process, @selection_processes,
      @application_open, @application_close, @status, @official_url, @notification_url,
      @age_min, @age_max, @age_as_on, @min_education, @discipline, @gender_required,
      @domicile_required, @domicile_states, @pwbd_allowed, @reserved_only, @open_to_categories,
      @reservation_notes, @eligibility_json, @raw, @published_at, @updated_at
    )
  `);
  const upsertPost = database.prepare(`
    INSERT OR REPLACE INTO opportunity_posts (
      id, opportunity_id, title, pwbd_allowed, pwbd_categories, reserved_only, open_to_categories
    ) VALUES (
      @id, @opportunity_id, @title, @pwbd_allowed, @pwbd_categories, @reserved_only, @open_to_categories
    )
  `);
  const upsertCollect = database.prepare(`
    INSERT OR REPLACE INTO collect_jobs (
      id, url, host, state, reason, extracted, opportunity_id, created_at, updated_at
    ) VALUES (
      @id, @url, @host, @state, @reason, @extracted, @opportunity_id, @created_at, @updated_at
    )
  `);

  try {
    const rebuild = database.transaction(() => {
      database.pragma('foreign_keys = OFF');
      database.exec('DELETE FROM collect_jobs');
      database.exec('DELETE FROM opportunity_posts');
      database.exec('DELETE FROM opportunities');
      database.exec('DELETE FROM exam_series');
      database.exec('DELETE FROM sources');
      for (const row of sourceRows) upsertSource.run(row);
      for (const row of examRows) upsertExam.run(row);
      for (const row of opportunityRows) upsertOpp.run(row);
      for (const row of postRows) upsertPost.run(row);
      for (const row of collectRows) upsertCollect.run(row);
      database.pragma('foreign_keys = ON');
    });
    rebuild();
  } catch (err) {
    sqlite.setStatus('missing');
    sqlite.close();
    return { status: 'missing', reason: err.message, read: {}, upserted: {} };
  }

  sqlite.setStatus('ok');
  sqlite.setSourceFingerprint(FINGERPRINT_FILES);

  const report = {
    status: 'ok',
    dbPath: dbPath || sqlite.getDbPath(),
    read: {
      sources: (registry.sources || []).length,
      jobs: jobs.length,
      opportunities: opportunities.length,
      examSeries: examSeries.length,
      collectJobs: collectJobs.length,
    },
    upserted: {
      sources: sourceRows.length,
      opportunities: opportunityRows.length,
      examSeries: examRows.length,
      collectJobs: collectRows.length,
    },
  };
  return report;
}

if (require.main === module) {
  const report = rebuildCache();
  console.log(JSON.stringify(report, null, 2));
  if (report.status === 'missing') process.exit(1);
}

module.exports = {
  rebuildCache,
  mapOpportunity,
  mapSource,
  FINGERPRINT_FILES,
};
