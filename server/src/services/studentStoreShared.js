const fs = require('fs');
const path = require('path');
const desk = require('../../../shared/deskGuidance');
const jobStore = require('./jobStore');

const MIN_PASSWORD = 10;
const FILE_KINDS = ['admit', 'result'];
const MAX_FILE_BYTES = 5 * 1024 * 1024;
const MIME_EXT = {
  'application/pdf': 'pdf',
  'image/jpeg': 'jpg',
  'image/png': 'png',
};
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function dataDir() {
  return process.env.STUDENT_DATA_DIR || path.join(__dirname, '..', '..', '..', 'data', 'students');
}

function filesDir() {
  return process.env.STUDENT_FILES_DIR || path.join(__dirname, '..', '..', '..', 'data', 'student-files');
}

function storePath() {
  return path.join(dataDir(), 'students.json');
}

function fail(code, message) {
  const err = new Error(message);
  err.code = code;
  throw err;
}

function normalizeEmail(email) {
  return String(email || '').trim().toLowerCase();
}

function publicStudent(row) {
  if (!row) return null;
  return { id: row.id, email: row.email, createdAt: row.createdAt };
}

function profileIsEmpty(profile) {
  if (!profile || typeof profile !== 'object') return true;
  return !profile.dob && !profile.highestEducation && !profile.reservationCategory;
}

function isUuid(id) {
  return UUID_RE.test(String(id || ''));
}

function detectMime(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length < 4) return null;
  if (buffer[0] === 0x25 && buffer[1] === 0x50 && buffer[2] === 0x44 && buffer[3] === 0x46) {
    return 'application/pdf';
  }
  if (buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) {
    return 'image/jpeg';
  }
  if (
    buffer.length >= 8 &&
    buffer[0] === 0x89 &&
    buffer[1] === 0x50 &&
    buffer[2] === 0x4e &&
    buffer[3] === 0x47 &&
    buffer[4] === 0x0d &&
    buffer[5] === 0x0a &&
    buffer[6] === 0x1a &&
    buffer[7] === 0x0a
  ) {
    return 'image/png';
  }
  return null;
}

function sanitizeName(originalName, kind, mime) {
  const ext = MIME_EXT[mime] || 'bin';
  const fallback = kind === 'admit' ? `admit-card.${ext}` : `result.${ext}`;
  const base = String(originalName || '')
    .split(/[/\\]/)
    .pop()
    .replace(/[^\w.\- ()[\]]+/g, '_')
    .replace(/^\.+/, '')
    .slice(0, 80);
  if (base && /\.(pdf|png|jpe?g)$/i.test(base)) return base;
  return fallback;
}

function publicFileMeta(row) {
  if (!row) return null;
  return {
    kind: row.kind,
    originalName: row.originalName,
    mime: row.mime,
    bytes: row.bytes,
    uploadedAt: row.uploadedAt,
  };
}

function itemDir(studentId, itemId) {
  if (!isUuid(studentId) || !isUuid(itemId)) fail('VALIDATION', 'invalid id');
  return path.join(filesDir(), studentId, itemId);
}

function unlinkKindFiles(dir, kind) {
  if (!fs.existsSync(dir)) return;
  for (const name of fs.readdirSync(dir)) {
    if (name.startsWith(`${kind}.`)) {
      fs.unlinkSync(path.join(dir, name));
    }
  }
}

function resolveStored(storedPath) {
  const parts = String(storedPath || '').split(/[/\\]/).filter(Boolean);
  if (parts.length !== 3) fail('VALIDATION', 'invalid stored path');
  if (parts.some((p) => p === '..' || p === '.')) fail('VALIDATION', 'invalid stored path');
  if (!isUuid(parts[0]) || !isUuid(parts[1])) fail('VALIDATION', 'invalid stored path');
  if (!/^(admit|result)\.(pdf|jpg|png)$/.test(parts[2])) fail('VALIDATION', 'invalid stored path');
  return path.join(filesDir(), parts[0], parts[1], parts[2]);
}

function writeKindFile(studentId, itemId, kind, mime, buffer) {
  const dir = itemDir(studentId, itemId);
  fs.mkdirSync(dir, { recursive: true });
  unlinkKindFiles(dir, kind);
  const destName = `${kind}.${MIME_EXT[mime]}`;
  fs.writeFileSync(path.join(dir, destName), buffer);
  return [studentId, itemId, destName].join('/');
}

function catalogFor(kind, refId) {
  if (kind === 'series') {
    const series = jobStore.getExamSeriesById(refId);
    if (!series) return null;
    return {
      title: series.name,
      board: series.board,
      officialUrl: series.officialUrl,
      examDate: desk.formatIsoDate(series.expectedExam),
      lastDate: desk.formatIsoDate(series.expectedApply),
      applyOpen: Boolean(series.canApply),
    };
  }
  if (kind === 'opportunity') {
    const job = jobStore.getJobById(refId);
    if (!job) return null;
    return {
      title: job.title,
      board: job.organization || job.board || '',
      officialUrl: job.officialUrl,
      examDate: desk.formatIsoDate(job.examDate || job.walkInDate),
      lastDate: desk.formatIsoDate(job.lastDate || job.applicationClose),
      applyOpen: job.status === 'open' || job.status === 'closing_soon',
    };
  }
  return null;
}

function decorateItem(row, files) {
  const catalog = row.kind === 'custom' ? null : catalogFor(row.kind, row.refId);
  const examDate = row.examDate || (catalog && catalog.examDate) || null;
  const lastDate = row.lastDate || (catalog && catalog.lastDate) || null;
  const admit = (files || []).find((f) => f.kind === 'admit') || null;
  const result = (files || []).find((f) => f.kind === 'result') || null;
  return desk.decorateItem({
    id: row.id,
    studentId: row.studentId,
    kind: row.kind,
    refId: row.refId || null,
    title: row.title || (catalog && catalog.title) || 'Untitled',
    board: row.board || (catalog && catalog.board) || '',
    status: row.status,
    examDate,
    lastDate,
    officialUrl: row.officialUrl || (catalog && catalog.officialUrl) || '',
    notes: row.notes || '',
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
    hasAdmit: Boolean(admit),
    hasResult: Boolean(result),
    admitFile: publicFileMeta(admit),
    resultFile: publicFileMeta(result),
    applyOpen: Boolean(catalog && catalog.applyOpen),
  });
}

function sortItems(items) {
  return items.slice().sort((a, b) => {
    const ad = a.daysLeft == null ? 99999 : a.daysLeft < 0 ? 50000 - a.daysLeft : a.daysLeft;
    const bd = b.daysLeft == null ? 99999 : b.daysLeft < 0 ? 50000 - b.daysLeft : b.daysLeft;
    return ad - bd;
  });
}

function itemStats(items, mocksCompleted) {
  const upcoming = items.filter((i) => i.daysLeft != null && i.daysLeft >= 0).length;
  const admitPending = items.filter((i) => i.status === 'applied' && !i.hasAdmit).length;
  const dated = items.filter((i) => i.daysLeft != null && i.daysLeft >= 0);
  const nearest = dated.length ? Math.min(...dated.map((i) => i.daysLeft)) : null;
  return { upcoming, admitPending, nearestDays: nearest, mocksCompleted };
}

function validateNewAccount({ email, password }) {
  const rawEmail = String(email || '').trim();
  const emailNorm = normalizeEmail(rawEmail);
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailNorm)) {
    fail('VALIDATION', 'A valid email is required');
  }
  if (typeof password !== 'string' || password.length < MIN_PASSWORD) {
    fail('VALIDATION', `Password must be at least ${MIN_PASSWORD} characters`);
  }
  return { rawEmail, emailNorm };
}

function validateFileBuffer(kind, input) {
  if (!FILE_KINDS.includes(kind)) fail('VALIDATION', 'kind must be admit or result');
  const buffer = input && input.buffer;
  if (!Buffer.isBuffer(buffer) || buffer.length === 0) fail('VALIDATION', 'A file is required');
  if (buffer.length > MAX_FILE_BYTES) {
    const err = new Error('File must be 5 MB or smaller');
    err.code = 'TOO_LARGE';
    throw err;
  }
  const mime = detectMime(buffer);
  if (!mime) fail('VALIDATION', 'Only PDF, JPEG, and PNG files are allowed');
  return { buffer, mime };
}

function applyItemPatch(row, patch) {
  const next = { ...row };
  if (patch.status != null) {
    if (!desk.STATUSES.includes(patch.status)) fail('VALIDATION', 'invalid status');
    next.status = patch.status;
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'examDate')) {
    next.examDate = patch.examDate ? desk.formatIsoDate(patch.examDate) : null;
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'lastDate')) {
    next.lastDate = patch.lastDate ? desk.formatIsoDate(patch.lastDate) : null;
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'officialUrl')) {
    const url = String(patch.officialUrl || '').trim();
    if (url && !/^https:\/\//i.test(url)) fail('VALIDATION', 'officialUrl must be https');
    next.officialUrl = url;
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'notes')) {
    next.notes = String(patch.notes || '').trim();
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'title') && next.kind === 'custom') {
    const title = String(patch.title || '').trim();
    if (!title) fail('VALIDATION', 'title is required');
    next.title = title;
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'board') && next.kind === 'custom') {
    next.board = String(patch.board || '').trim();
  }
  next.updatedAt = new Date().toISOString();
  return next;
}

function buildCatalogItem(studentId, input) {
  const kind = String(input.kind || '').trim();
  if (!desk.KINDS.includes(kind)) fail('VALIDATION', 'kind must be series, opportunity, or custom');
  const status = input.status && desk.STATUSES.includes(input.status) ? input.status : 'watching';
  const now = new Date().toISOString();
  if (kind === 'series' || kind === 'opportunity') {
    const refId = String(input.refId || '').trim();
    if (!refId) fail('VALIDATION', 'refId is required');
    const catalog = catalogFor(kind, refId);
    if (!catalog) fail('VALIDATION', kind === 'series' ? 'Exam series not found' : 'Job not found');
    return {
      kind,
      status,
      now,
      catalog,
      row: {
        studentId,
        kind,
        refId,
        title: catalog.title,
        board: catalog.board,
        status,
        examDate: input.examDate || null,
        lastDate: input.lastDate || null,
        officialUrl: input.officialUrl || '',
        notes: typeof input.notes === 'string' ? input.notes.trim() : '',
        createdAt: now,
        updatedAt: now,
      },
    };
  }
  const title = String(input.title || '').trim();
  if (!title) fail('VALIDATION', 'title is required');
  const examDate = desk.formatIsoDate(input.examDate);
  const lastDate = desk.formatIsoDate(input.lastDate);
  if (!examDate && !lastDate) fail('VALIDATION', 'custom exam needs an exam date or last date');
  let officialUrl = String(input.officialUrl || '').trim();
  if (officialUrl && !/^https:\/\//i.test(officialUrl)) {
    fail('VALIDATION', 'officialUrl must be https');
  }
  return {
    kind: 'custom',
    status,
    now,
    catalog: null,
    row: {
      studentId,
      kind: 'custom',
      refId: null,
      title,
      board: String(input.board || '').trim(),
      status,
      examDate,
      lastDate,
      officialUrl,
      notes: typeof input.notes === 'string' ? input.notes.trim() : '',
      createdAt: now,
      updatedAt: now,
    },
  };
}

function warnIfUnwritable() {
  if (process.env.NODE_ENV !== 'production') return;
  for (const dir of [dataDir(), filesDir()]) {
    try {
      fs.mkdirSync(dir, { recursive: true });
      fs.accessSync(dir, fs.constants.W_OK);
    } catch {
      console.warn(
        `Student store is not writable (${dir}). Use a persistent disk or STUDENT_STORE=postgres.`
      );
    }
  }
}

function iso(value) {
  if (!value) return null;
  if (value instanceof Date) return value.toISOString();
  return String(value);
}

function isoDate(value) {
  if (!value) return null;
  if (value instanceof Date) return desk.formatIsoDate(value);
  const s = String(value);
  return s.length >= 10 ? s.slice(0, 10) : s;
}

module.exports = {
  MIN_PASSWORD,
  FILE_KINDS,
  MAX_FILE_BYTES,
  MIME_EXT,
  dataDir,
  filesDir,
  storePath,
  fail,
  normalizeEmail,
  publicStudent,
  profileIsEmpty,
  isUuid,
  detectMime,
  sanitizeName,
  publicFileMeta,
  itemDir,
  unlinkKindFiles,
  resolveStored,
  writeKindFile,
  catalogFor,
  decorateItem,
  sortItems,
  itemStats,
  validateNewAccount,
  validateFileBuffer,
  applyItemPatch,
  buildCatalogItem,
  warnIfUnwritable,
  iso,
  isoDate,
};
