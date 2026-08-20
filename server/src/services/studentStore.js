/**
 * Student SoR facade.
 * STUDENT_STORE=json (default) — atomic host JSON, used by unit tests.
 * STUDENT_STORE=postgres — Docker/Postgres. Also selected when STUDENT_DATABASE_URL is set
 * and STUDENT_STORE is unset.
 */
function resolveName() {
  const raw = String(process.env.STUDENT_STORE || '').trim().toLowerCase();
  if (raw === 'postgres' || raw === 'pg' || raw === 'postgresql') return 'postgres';
  if (raw === 'json' || raw === 'file') return 'json';
  if (!raw && (process.env.STUDENT_DATABASE_URL || process.env.DATABASE_URL)) return 'postgres';
  if (raw && raw !== 'json') {
    throw new Error(`Unknown STUDENT_STORE=${raw} (use json or postgres)`);
  }
  return 'json';
}

const BACKEND_NAME = resolveName();
const impl = BACKEND_NAME === 'postgres' ? require('./studentStorePg') : require('./studentStoreJson');

module.exports = impl;
module.exports.BACKEND_NAME = BACKEND_NAME;
module.exports.resolveName = resolveName;
