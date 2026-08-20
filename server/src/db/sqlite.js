/**
 * Optional better-sqlite3 read cache. JSON remains the source of record.
 * Never required by the GHA / root pipeline. Missing native addon → status "missing".
 */
const fs = require('fs');
const path = require('path');

const DEFAULT_DB_PATH = path.join(__dirname, '..', '..', '..', 'data', 'cache', 'portal.sqlite');

let Database = null;
let loadError = null;
try {
  Database = require('better-sqlite3');
} catch (err) {
  loadError = err;
}

const INIT_SQL = `
PRAGMA journal_mode=WAL;
PRAGMA foreign_keys=ON;

CREATE TABLE IF NOT EXISTS sources (
  id            TEXT PRIMARY KEY,
  name          TEXT NOT NULL,
  base_url      TEXT NOT NULL,
  list_urls     TEXT NOT NULL DEFAULT '[]',
  enabled       INTEGER NOT NULL DEFAULT 1,
  cadence       TEXT,
  category      TEXT,
  priority      TEXT NOT NULL CHECK (priority IN ('P0','P1','P2','P3')),
  collector     TEXT NOT NULL,
  method        TEXT NOT NULL,
  auto_publish  INTEGER NOT NULL DEFAULT 0,
  rate_limit_ms INTEGER NOT NULL DEFAULT 5000,
  last_run_at   TEXT,
  last_status   TEXT,
  robots_notes  TEXT
);

CREATE TABLE IF NOT EXISTS exam_series (
  id              TEXT PRIMARY KEY,
  source_id       TEXT REFERENCES sources(id),
  board           TEXT NOT NULL,
  name            TEXT NOT NULL,
  cycle           TEXT,
  expected_notify TEXT,
  expected_apply  TEXT,
  expected_exam   TEXT,
  official_url    TEXT NOT NULL,
  raw             TEXT NOT NULL,
  updated_at      TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS opportunities (
  id                 TEXT PRIMARY KEY,
  source_id          TEXT REFERENCES sources(id),
  exam_series_id     TEXT REFERENCES exam_series(id),
  title              TEXT NOT NULL,
  board              TEXT,
  has_exam           INTEGER NOT NULL DEFAULT 0,
  selection_process  TEXT NOT NULL,
  selection_processes TEXT,
  application_open   TEXT,
  application_close  TEXT,
  status             TEXT NOT NULL,
  official_url       TEXT NOT NULL,
  notification_url   TEXT,
  age_min            INTEGER,
  age_max            INTEGER,
  age_as_on          TEXT,
  min_education      TEXT,
  discipline         TEXT,
  gender_required    TEXT,
  domicile_required  INTEGER,
  domicile_states    TEXT,
  pwbd_allowed       INTEGER,
  reservation_notes  TEXT,
  eligibility_json   TEXT NOT NULL,
  raw                TEXT NOT NULL,
  published_at       TEXT,
  updated_at         TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS opp_status_close ON opportunities(status, application_close);
CREATE INDEX IF NOT EXISTS opp_has_exam ON opportunities(has_exam, status);
CREATE INDEX IF NOT EXISTS opp_board ON opportunities(board);
CREATE INDEX IF NOT EXISTS opp_official_url ON opportunities(official_url);

CREATE TABLE IF NOT EXISTS collect_jobs (
  id           TEXT PRIMARY KEY,
  url          TEXT NOT NULL,
  host         TEXT NOT NULL,
  state        TEXT NOT NULL,
  reason       TEXT,
  extracted    TEXT,
  opportunity_id TEXT,
  created_at   TEXT NOT NULL,
  updated_at   TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS cj_state ON collect_jobs(state, updated_at);
`;

const MIGRATIONS = [
  { name: '001_init_cache', sql: INIT_SQL },
  {
    name: '002_posts_reserved',
    sql: `
ALTER TABLE opportunities ADD COLUMN reserved_only INTEGER;
ALTER TABLE opportunities ADD COLUMN open_to_categories TEXT;
CREATE TABLE IF NOT EXISTS opportunity_posts (
  id TEXT PRIMARY KEY,
  opportunity_id TEXT NOT NULL,
  title TEXT NOT NULL,
  pwbd_allowed INTEGER,
  pwbd_categories TEXT,
  reserved_only INTEGER,
  open_to_categories TEXT
);
CREATE INDEX IF NOT EXISTS opp_posts_opp ON opportunity_posts(opportunity_id);
`,
  },
];

let db = null;
let lastStatus = 'missing';
let fingerprintFiles = [];
let sourceFingerprint = null;

function isEnabled() {
  const flag = String(process.env.SQLITE_CACHE || 'on').trim().toLowerCase();
  return flag !== 'off' && flag !== '0' && flag !== 'false';
}

function getDbPath() {
  return process.env.SQLITE_PATH || DEFAULT_DB_PATH;
}

function nativeAvailable() {
  return Boolean(Database);
}

function getLoadError() {
  return loadError;
}

function getStatus() {
  if (!isEnabled()) return 'off';
  return lastStatus;
}

function setStatus(status) {
  lastStatus = status;
}

function fileFingerprint(filePath) {
  try {
    const st = fs.statSync(filePath);
    return `${st.size}:${Math.trunc(st.mtimeMs)}`;
  } catch {
    return 'missing';
  }
}

function computeFingerprint(files) {
  return (files || []).map(fileFingerprint).join('|');
}

function setSourceFingerprint(files) {
  fingerprintFiles = Array.isArray(files) ? files : [];
  sourceFingerprint = computeFingerprint(fingerprintFiles);
}

function isFresh() {
  if (!db || !sourceFingerprint || !fingerprintFiles.length) return false;
  return sourceFingerprint === computeFingerprint(fingerprintFiles);
}

function applyMigrations(database) {
  database.exec(`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      id INTEGER PRIMARY KEY,
      name TEXT NOT NULL UNIQUE,
      applied_at TEXT NOT NULL
    )
  `);
  const applied = new Set(
    database.prepare('SELECT name FROM schema_migrations').all().map((row) => row.name)
  );
  const insert = database.prepare(
    'INSERT INTO schema_migrations (name, applied_at) VALUES (?, ?)'
  );
  for (const migration of MIGRATIONS) {
    if (applied.has(migration.name)) continue;
    database.exec(migration.sql);
    insert.run(migration.name, new Date().toISOString());
  }
}

function open(dbPath = getDbPath()) {
  if (!isEnabled()) {
    lastStatus = 'missing';
    return null;
  }
  if (!Database) {
    lastStatus = 'missing';
    return null;
  }
  if (db) {
    try {
      db.close();
    } catch {
      /* ignore */
    }
    db = null;
  }
  fs.mkdirSync(path.dirname(dbPath), { recursive: true });
  const database = new Database(dbPath);
  database.pragma('journal_mode = WAL');
  database.pragma('foreign_keys = ON');
  applyMigrations(database);
  db = database;
  return db;
}

function close() {
  if (db) {
    try {
      db.close();
    } catch {
      /* ignore */
    }
    db = null;
  }
  sourceFingerprint = null;
  fingerprintFiles = [];
}

function getDb() {
  return db;
}

module.exports = {
  DEFAULT_DB_PATH,
  open,
  close,
  getDb,
  getDbPath,
  getStatus,
  setStatus,
  isEnabled,
  isFresh,
  setSourceFingerprint,
  applyMigrations,
  nativeAvailable,
  getLoadError,
};
