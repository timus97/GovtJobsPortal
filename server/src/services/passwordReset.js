/**
 * One-time student password-reset tokens. Stored hashed under STUDENT_DATA_DIR.
 * Never write the raw token to disk or structured logs.
 */
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const studentStore = require('./studentStore');
const { dataDir } = require('./studentStoreShared');

const DEFAULT_TTL_MS = 60 * 60 * 1000;

function ttlMs() {
  const n = Number(process.env.STUDENT_RESET_TTL_MS);
  return Number.isFinite(n) && n > 0 ? n : DEFAULT_TTL_MS;
}

function tokenFile() {
  return process.env.STUDENT_RESET_PATH || path.join(dataDir(), 'reset-tokens.json');
}

function nowIso() {
  return new Date().toISOString();
}

function hashToken(raw) {
  return crypto.createHash('sha256').update(String(raw)).digest('hex');
}

function readStore() {
  try {
    const raw = JSON.parse(fs.readFileSync(tokenFile(), 'utf8'));
    return Array.isArray(raw.tokens) ? raw.tokens : [];
  } catch {
    return [];
  }
}

function writeStore(tokens) {
  const file = tokenFile();
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const tmp = `${file}.${process.pid}.tmp`;
  fs.writeFileSync(tmp, `${JSON.stringify({ updatedAt: nowIso(), tokens }, null, 2)}\n`, 'utf8');
  fs.renameSync(tmp, file);
}

function prune(tokens, now = Date.now()) {
  return tokens.filter((row) => {
    if (!row || row.usedAt) return false;
    const exp = Date.parse(row.expiresAt);
    return Number.isFinite(exp) && exp > now;
  });
}

function publicSiteUrl() {
  return String(process.env.PUBLIC_SITE_URL || 'http://localhost:5173').replace(/\/$/, '');
}

function resetUrl(rawToken) {
  return `${publicSiteUrl()}/account/reset?token=${encodeURIComponent(rawToken)}`;
}

async function requestReset(email) {
  const student = await studentStore.findByEmail(email);
  if (!student) {
    return { created: false };
  }
  const raw = crypto.randomBytes(32).toString('base64url');
  const now = Date.now();
  const row = {
    id: crypto.randomUUID(),
    tokenHash: hashToken(raw),
    studentId: student.id,
    emailNorm: student.emailNorm || studentStore.normalizeEmail(student.email),
    createdAt: new Date(now).toISOString(),
    expiresAt: new Date(now + ttlMs()).toISOString(),
    usedAt: null,
  };
  const tokens = prune(readStore(), now).filter((t) => t.studentId !== student.id);
  tokens.push(row);
  writeStore(tokens);
  return {
    created: true,
    token: raw,
    url: resetUrl(raw),
    expiresAt: row.expiresAt,
    student: studentStore.publicStudent(student),
  };
}

function findLive(rawToken) {
  const tokenHash = hashToken(rawToken);
  const now = Date.now();
  const tokens = prune(readStore(), now);
  return tokens.find((t) => t.tokenHash === tokenHash && !t.usedAt) || null;
}

function peekReset(rawToken) {
  const token = typeof rawToken === 'string' ? rawToken.trim() : '';
  if (!token) return { ok: false, reason: 'missing' };
  const row = findLive(token);
  if (!row) return { ok: false, reason: 'invalid' };
  return { ok: true, expiresAt: row.expiresAt };
}

async function consumeReset(rawToken, password) {
  const token = typeof rawToken === 'string' ? rawToken.trim() : '';
  if (!token) {
    const err = new Error('Reset link is missing or invalid');
    err.code = 'VALIDATION';
    throw err;
  }
  const row = findLive(token);
  if (!row) {
    const err = new Error('This reset link is invalid or has expired');
    err.code = 'EXPIRED';
    throw err;
  }
  const student = await studentStore.setPassword(row.studentId, password);
  if (!student) {
    const err = new Error('This reset link is invalid or has expired');
    err.code = 'EXPIRED';
    throw err;
  }
  const tokens = readStore().map((t) =>
    t.tokenHash === row.tokenHash ? { ...t, usedAt: nowIso() } : t
  );
  writeStore(prune(tokens));
  return student;
}

module.exports = {
  requestReset,
  peekReset,
  consumeReset,
  resetUrl,
  ttlMs,
  tokenFile,
};
