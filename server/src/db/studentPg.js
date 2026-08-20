const fs = require('fs');
const path = require('path');

const SCHEMA_PATH = path.join(__dirname, 'student-schema.sql');

function databaseUrl() {
  return (
    process.env.STUDENT_DATABASE_URL ||
    process.env.DATABASE_URL ||
    'postgres://govtjobs:govtjobs@127.0.0.1:5432/govtjobs_students'
  );
}

let pool = null;
let Pool = null;

function getPool() {
  if (pool) return pool;
  if (!Pool) {
    try {
      ({ Pool } = require('pg'));
    } catch (err) {
      const wrap = new Error(
        'STUDENT_STORE=postgres requires the pg package. Run: npm --prefix server install pg'
      );
      wrap.cause = err;
      throw wrap;
    }
  }
  pool = new Pool({ connectionString: databaseUrl() });
  pool.on('error', (err) => {
    console.warn(`Student postgres pool error: ${err.message}`);
  });
  return pool;
}

async function query(text, params) {
  return getPool().query(text, params);
}

async function migrate() {
  const sql = fs.readFileSync(SCHEMA_PATH, 'utf8');
  await query(sql);
  return { ok: true, url: databaseUrl().replace(/:[^:@/]+@/, ':***@') };
}

async function close() {
  if (!pool) return;
  const p = pool;
  pool = null;
  await p.end();
}

module.exports = {
  databaseUrl,
  getPool,
  query,
  migrate,
  close,
};
