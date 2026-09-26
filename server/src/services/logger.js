/**
 * Structured app logger for every role and core unit.
 * Never attach passwords, session tokens, profile bodies, or file bytes.
 */
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..', '..', '..');
const DEFAULT_LOG_PATH = path.join(root, 'data', 'logs', 'app.log');
const RING_MAX = 500;
const FILE_LINE_MAX = 4000;

const SENSITIVE_KEY = /pass(word)?|secret|token|cookie|authorization|profile|buffer|filebytes/i;

const ring = [];
let seq = 0;

function logPath() {
  return process.env.APP_LOG_PATH || DEFAULT_LOG_PATH;
}

function fileLoggingOn() {
  const flag = String(process.env.APP_LOG_FILE || 'on').trim().toLowerCase();
  return flag !== 'off' && flag !== '0' && flag !== 'false';
}

function nowIso() {
  return new Date().toISOString();
}

function safeMeta(meta, depth = 0) {
  if (meta == null) return undefined;
  if (depth > 3) return '[truncated]';
  if (Array.isArray(meta)) {
    return meta.slice(0, 20).map((item) => safeMeta(item, depth + 1));
  }
  if (typeof meta !== 'object') {
    if (typeof meta === 'string' && meta.length > 500) return `${meta.slice(0, 500)}…`;
    return meta;
  }
  const out = {};
  for (const [key, value] of Object.entries(meta)) {
    if (SENSITIVE_KEY.test(key)) {
      out[key] = '[redacted]';
      continue;
    }
    out[key] = safeMeta(value, depth + 1);
  }
  return out;
}

function writeFile(line) {
  if (!fileLoggingOn()) return;
  try {
    const file = logPath();
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.appendFile(file, `${line}\n`, () => {});
  } catch {
    /* never throw from logger */
  }
}

function record(level, unit, message, extra = {}) {
  seq += 1;
  const rec = {
    id: `log_${seq}_${Date.now().toString(36)}`,
    ts: nowIso(),
    level,
    unit: unit || 'app',
    message: String(message || ''),
    requestId: extra.requestId || null,
    role: extra.role || 'system',
    actor: extra.actor || null,
    action: extra.action || null,
    status: extra.status != null ? extra.status : null,
    durationMs: extra.durationMs != null ? extra.durationMs : null,
    meta: extra.meta ? safeMeta(extra.meta) : undefined,
  };
  ring.push(rec);
  if (ring.length > RING_MAX) ring.shift();

  const line = JSON.stringify(rec);
  const printable = line.length > FILE_LINE_MAX ? `${line.slice(0, FILE_LINE_MAX)}…` : line;
  if (level === 'error') console.error(printable);
  else if (level === 'warn') console.warn(printable);
  else console.log(printable);
  writeFile(printable);
  return rec;
}

function info(unit, message, extra) {
  return record('info', unit, message, extra);
}

function warn(unit, message, extra) {
  return record('warn', unit, message, extra);
}

function error(unit, message, extra) {
  return record('error', unit, message, extra);
}

function debug(unit, message, extra) {
  const flag = String(process.env.LOG_LEVEL || 'info').trim().toLowerCase();
  if (flag !== 'debug') return null;
  return record('debug', unit, message, extra);
}

function roleFromReq(req) {
  if (req && req.ops) return req.ops.role || 'operator';
  if (req && req.student) return 'student';
  return 'anon';
}

function actorFromReq(req) {
  if (req && req.ops) return req.ops.sub || req.ops.username || null;
  if (req && req.student) return req.student.sub || null;
  return null;
}

function fromReq(req, extra = {}) {
  return {
    requestId: (req && req.requestId) || extra.requestId,
    role: extra.role || roleFromReq(req),
    actor: extra.actor || actorFromReq(req),
    ...extra,
  };
}

function list({ unit, role, level, q, limit } = {}) {
  const max = Math.min(400, Math.max(1, parseInt(limit, 10) || 100));
  const unitFilter = unit ? String(unit).toLowerCase() : '';
  const roleFilter = role ? String(role).toLowerCase() : '';
  const levelFilter = level ? String(level).toLowerCase() : '';
  const term = q ? String(q).toLowerCase() : '';
  const items = [];
  for (let i = ring.length - 1; i >= 0 && items.length < max; i -= 1) {
    const rec = ring[i];
    if (unitFilter && String(rec.unit).toLowerCase() !== unitFilter && !String(rec.unit).toLowerCase().startsWith(`${unitFilter}.`)) {
      continue;
    }
    if (roleFilter && String(rec.role).toLowerCase() !== roleFilter) continue;
    if (levelFilter && String(rec.level).toLowerCase() !== levelFilter) continue;
    if (term) {
      const blob = `${rec.message} ${rec.action || ''} ${rec.actor || ''} ${rec.unit}`.toLowerCase();
      if (!blob.includes(term)) continue;
    }
    items.push(rec);
  }
  return {
    items,
    total: ring.length,
    returned: items.length,
  };
}

function resetForTests() {
  ring.length = 0;
  seq = 0;
}

module.exports = {
  info,
  warn,
  error,
  debug,
  record,
  list,
  fromReq,
  roleFromReq,
  actorFromReq,
  safeMeta,
  resetForTests,
  RING_MAX,
};
