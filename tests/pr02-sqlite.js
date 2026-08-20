/**
 * PR02 optional SQLite cache smoke checks. Run: node tests/pr02-sqlite.js
 * JSON remains the source of record. Cache is skipped if better-sqlite3 is missing.
 */
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'pr02-sqlite-'));
const dbPath = path.join(tmpDir, 'portal.sqlite');

process.env.SQLITE_CACHE = 'on';
process.env.SQLITE_PATH = dbPath;

const sqlite = require('../server/src/db/sqlite');
const { rebuildCache } = require('../scripts/migrate/jsonToSqlite');
const store = require('../server/src/services/jobStore');

const jobsPath = path.join(__dirname, '..', 'data', 'processed', 'jobs.json');
const EXAM_FIXTURE_ID = '912c0026508e7dca';

function fail(message, err) {
  console.error(`FAIL: ${message}`);
  if (err) console.error(err.message || err);
  cleanup();
  process.exit(1);
}

function cleanup() {
  try {
    sqlite.close();
  } catch {
    /* ignore */
  }
  try {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  } catch {
    /* ignore */
  }
}

function readJobs() {
  return JSON.parse(fs.readFileSync(jobsPath, 'utf8'));
}

try {
  const diskJobs = readJobs();
  assert.ok(Array.isArray(diskJobs) && diskJobs.length > 0, 'jobs.json should have rows');

  const fromStore = store.getJobs();
  assert.strictEqual(fromStore.length, diskJobs.length, 'getJobs() must read JSON SoR');
  assert.strictEqual(fromStore[0].id, diskJobs[0].id);

  const jsonList = store.applyJobFilters(diskJobs, { hasExam: 'yes' });
  assert.ok(
    jsonList.some((j) => j.id === EXAM_FIXTURE_ID),
    'JSON filter hasExam=yes should include the exam fixture'
  );

  process.env.SQLITE_CACHE = 'off';
  assert.strictEqual(sqlite.getStatus(), 'off', 'SQLITE_CACHE=off → sqliteCache off');
  process.env.SQLITE_CACHE = 'on';

  if (!sqlite.nativeAvailable()) {
    const report = rebuildCache({ dbPath });
    assert.strictEqual(report.status, 'missing', 'missing native addon should report missing');
    assert.strictEqual(sqlite.getStatus(), 'missing');
    const fallback = store.listJobs({ hasExam: 'yes', limit: '5' });
    assert.ok(fallback.total >= 1, 'JSON fallback listJobs must still work');
    console.log('pr02-sqlite: native addon missing; JSON-first path passed');
    cleanup();
    process.exit(0);
  }

  const report = rebuildCache({ dbPath });
  assert.strictEqual(report.status, 'ok', `rebuild should succeed: ${report.reason || ''}`);
  assert.strictEqual(sqlite.getStatus(), 'ok');
  assert.ok(sqlite.isFresh(), 'cache should be fresh vs git JSON');
  assert.strictEqual(
    report.upserted.opportunities,
    diskJobs.length,
    'opportunity upsert count should match jobs.json'
  );
  assert.ok(report.upserted.sources >= 1, 'sources should be loaded from registry');

  const db = sqlite.getDb();
  assert.ok(db, 'db should be open');

  const oppCount = db.prepare('SELECT COUNT(*) AS n FROM opportunities').get().n;
  assert.strictEqual(oppCount, diskJobs.length);

  const examRow = db.prepare('SELECT has_exam, min_education FROM opportunities WHERE id = ?').get(
    EXAM_FIXTURE_ID
  );
  assert.ok(examRow, 'exam fixture should be cached');
  assert.strictEqual(examRow.has_exam, 1, 'has_exam must stay 1 for exam fixture (do not force 0)');
  assert.strictEqual(examRow.min_education, 'graduate');

  const urlIndex = db
    .prepare("SELECT sql FROM sqlite_master WHERE type = 'index' AND name = 'opp_official_url'")
    .get();
  assert.ok(urlIndex && urlIndex.sql, 'official_url index should exist');
  assert.ok(!/unique/i.test(urlIndex.sql), 'official_url index must be non-unique');

  db.prepare(
    `INSERT INTO opportunities (
      id, title, has_exam, selection_process, status, official_url,
      eligibility_json, raw, updated_at
    ) VALUES
      ('pr02-dup-a', 'Dup A', 0, 'merit', 'open', 'https://example.gov/same', '{}', '{}', '2026-01-01'),
      ('pr02-dup-b', 'Dup B', 0, 'merit', 'open', 'https://example.gov/same', '{}', '{}', '2026-01-01')`
  ).run();
  const dups = db
    .prepare('SELECT COUNT(*) AS n FROM opportunities WHERE official_url = ?')
    .get('https://example.gov/same').n;
  assert.ok(dups >= 2, 'duplicate official_url rows must be allowed');

  const ncs = db
    .prepare(
      'SELECT list_urls, enabled, cadence, category, priority FROM sources WHERE id = ?'
    )
    .get('ncs_gov');
  assert.ok(ncs, 'ncs_gov source should be cached');
  const listUrls = JSON.parse(ncs.list_urls);
  assert.ok(Array.isArray(listUrls) && listUrls.length > 0, 'sources.list_urls should be preserved');
  assert.strictEqual(ncs.enabled, 1);
  assert.ok(ncs.cadence, 'sources.cadence should be preserved');
  assert.ok(ncs.category, 'sources.category should be preserved');
  assert.strictEqual(ncs.priority, 'P0');

  const cached = store.getJobById(EXAM_FIXTURE_ID);
  assert.ok(cached, 'getJobById should hit cache');
  assert.strictEqual(cached.hasExam, true);
  assert.strictEqual(cached.id, EXAM_FIXTURE_ID);

  const listed = store.listJobs({ hasExam: 'yes', limit: '10' });
  assert.ok(
    listed.items.some((j) => j.id === EXAM_FIXTURE_ID),
    'listJobs cache path should return the exam fixture'
  );

  const soR = store.getJobs();
  assert.strictEqual(soR.length, diskJobs.length, 'getJobs stays JSON even when cache is ok');

  const second = rebuildCache({ dbPath });
  assert.strictEqual(second.status, 'ok', 'rebuild must be idempotent');
  assert.strictEqual(second.upserted.opportunities, diskJobs.length);

  process.env.SQLITE_CACHE = 'off';
  assert.strictEqual(sqlite.getStatus(), 'off');
  const offList = store.listJobs({ hasExam: 'yes', limit: '5' });
  assert.ok(offList.total >= 1, 'listJobs still works when cache is off');
} catch (err) {
  fail(err.message, err);
}

cleanup();
console.log('pr02-sqlite: all passed');
process.exit(0);
