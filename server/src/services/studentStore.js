const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { hash, verify } = require('./password');
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

function warnIfUnwritable() {
  if (process.env.NODE_ENV !== 'production') return;
  for (const dir of [dataDir(), filesDir()]) {
    try {
      fs.mkdirSync(dir, { recursive: true });
      fs.accessSync(dir, fs.constants.W_OK);
    } catch {
      console.warn(
        `Student store is not writable (${dir}). Use a persistent disk. Ephemeral hosts lose accounts on sleep/redeploy.`
      );
    }
  }
}

function emptyStore() {
  return { students: [], profiles: {}, items: [], files: [], topicProgress: [], mockAttempts: [] };
}

function load() {
  try {
    const raw = JSON.parse(fs.readFileSync(storePath(), 'utf8'));
    return {
      students: Array.isArray(raw.students) ? raw.students : [],
      profiles: raw.profiles && typeof raw.profiles === 'object' ? raw.profiles : {},
      items: Array.isArray(raw.items) ? raw.items : [],
      files: Array.isArray(raw.files) ? raw.files : [],
      topicProgress: Array.isArray(raw.topicProgress) ? raw.topicProgress : [],
      mockAttempts: Array.isArray(raw.mockAttempts) ? raw.mockAttempts : [],
    };
  } catch {
    return emptyStore();
  }
}

function save(data) {
  const file = storePath();
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const tmp = `${file}.${process.pid}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2), 'utf8');
  try {
    fs.renameSync(tmp, file);
  } catch (err) {
    try {
      if (fs.existsSync(file)) fs.unlinkSync(file);
      fs.renameSync(tmp, file);
    } catch (renameErr) {
      try {
        fs.unlinkSync(tmp);
      } catch {
        /* ignore */
      }
      throw renameErr || err;
    }
  }
}

function normalizeEmail(email) {
  return String(email || '').trim().toLowerCase();
}

function publicStudent(row) {
  if (!row) return null;
  return { id: row.id, email: row.email, createdAt: row.createdAt };
}

function findByEmail(email) {
  const key = normalizeEmail(email);
  if (!key) return null;
  return load().students.find((s) => s.emailNorm === key) || null;
}

function findById(id) {
  return load().students.find((s) => s.id === id) || null;
}

function register({ email, password }) {
  const rawEmail = String(email || '').trim();
  const emailNorm = normalizeEmail(rawEmail);
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailNorm)) {
    const err = new Error('A valid email is required');
    err.code = 'VALIDATION';
    throw err;
  }
  if (typeof password !== 'string' || password.length < MIN_PASSWORD) {
    const err = new Error(`Password must be at least ${MIN_PASSWORD} characters`);
    err.code = 'VALIDATION';
    throw err;
  }
  const data = load();
  if (data.students.some((s) => s.emailNorm === emailNorm)) {
    const err = new Error('An account with this email already exists');
    err.code = 'DUPLICATE';
    throw err;
  }
  const now = new Date().toISOString();
  const student = {
    id: crypto.randomUUID(),
    email: rawEmail,
    emailNorm,
    passwordHash: hash(password),
    createdAt: now,
    lastLoginAt: now,
  };
  data.students.push(student);
  save(data);
  return publicStudent(student);
}

function verifyPassword(email, password) {
  const row = findByEmail(email);
  if (!row || !verify(password, row.passwordHash)) return null;
  const data = load();
  const idx = data.students.findIndex((s) => s.id === row.id);
  if (idx >= 0) {
    data.students[idx] = { ...data.students[idx], lastLoginAt: new Date().toISOString() };
    save(data);
    return publicStudent(data.students[idx]);
  }
  return publicStudent(row);
}

function getProfile(studentId) {
  const data = load();
  return data.profiles[studentId] || null;
}

function saveProfile(studentId, profile) {
  if (!findById(studentId)) return null;
  const data = load();
  const next = {
    ...(profile && typeof profile === 'object' ? profile : {}),
    updatedAt: new Date().toISOString(),
  };
  data.profiles[studentId] = next;
  save(data);
  return next;
}

function profileIsEmpty(profile) {
  if (!profile || typeof profile !== 'object') return true;
  return !profile.dob && !profile.highestEducation && !profile.reservationCategory;
}

function fail(code, message) {
  const err = new Error(message);
  err.code = code;
  throw err;
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

function isUuid(id) {
  return UUID_RE.test(String(id || ''));
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

function fileRow(data, itemId, kind) {
  return (data.files || []).find((f) => f.itemId === itemId && f.kind === kind) || null;
}

function itemHasFile(data, itemId, kind) {
  return Boolean(fileRow(data, itemId, kind));
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

function publicItem(row, data) {
  const catalog = row.kind === 'custom' ? null : catalogFor(row.kind, row.refId);
  const examDate = row.examDate || (catalog && catalog.examDate) || null;
  const lastDate = row.lastDate || (catalog && catalog.lastDate) || null;
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
    hasAdmit: itemHasFile(data, row.id, 'admit'),
    hasResult: itemHasFile(data, row.id, 'result'),
    admitFile: publicFileMeta(fileRow(data, row.id, 'admit')),
    resultFile: publicFileMeta(fileRow(data, row.id, 'result')),
    applyOpen: Boolean(catalog && catalog.applyOpen),
  });
}

function listItems(studentId, filter = {}) {
  const data = load();
  let items = data.items.filter((i) => i.studentId === studentId).map((i) => publicItem(i, data));
  if (filter.status && desk.STATUSES.includes(filter.status)) {
    items = items.filter((i) => i.status === filter.status);
  }
  items.sort((a, b) => {
    const ad = a.daysLeft == null ? 99999 : a.daysLeft < 0 ? 50000 - a.daysLeft : a.daysLeft;
    const bd = b.daysLeft == null ? 99999 : b.daysLeft < 0 ? 50000 - b.daysLeft : b.daysLeft;
    return ad - bd;
  });
  const upcoming = items.filter((i) => i.daysLeft != null && i.daysLeft >= 0).length;
  const admitPending = items.filter((i) => i.status === 'applied' && !i.hasAdmit).length;
  const dated = items.filter((i) => i.daysLeft != null && i.daysLeft >= 0);
  const nearest = dated.length ? Math.min(...dated.map((i) => i.daysLeft)) : null;
  return {
    items,
    total: items.length,
    stats: {
      upcoming,
      admitPending,
      nearestDays: nearest,
      mocksCompleted: (data.mockAttempts || []).filter((a) => a.studentId === studentId && a.submittedAt)
        .length,
    },
  };
}

function getItem(studentId, id) {
  const data = load();
  const row = data.items.find((i) => i.id === id && i.studentId === studentId);
  return row ? publicItem(row, data) : null;
}

function createItem(studentId, input) {
  if (!findById(studentId)) return null;
  const kind = String(input.kind || '').trim();
  if (!desk.KINDS.includes(kind)) fail('VALIDATION', 'kind must be series, opportunity, or custom');
  const status = input.status && desk.STATUSES.includes(input.status) ? input.status : 'watching';
  const now = new Date().toISOString();
  const data = load();

  if (kind === 'series' || kind === 'opportunity') {
    const refId = String(input.refId || '').trim();
    if (!refId) fail('VALIDATION', 'refId is required');
    const catalog = catalogFor(kind, refId);
    if (!catalog) fail('VALIDATION', kind === 'series' ? 'Exam series not found' : 'Job not found');
    const existing = data.items.find(
      (i) => i.studentId === studentId && i.kind === kind && i.refId === refId
    );
    if (existing) {
      if (status !== existing.status) {
        existing.status = status;
        existing.updatedAt = now;
        save(data);
      }
      return publicItem(existing, data);
    }
    const row = {
      id: crypto.randomUUID(),
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
    };
    data.items.push(row);
    save(data);
    return publicItem(row, data);
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
  const row = {
    id: crypto.randomUUID(),
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
  };
  data.items.push(row);
  save(data);
  return publicItem(row, data);
}

function updateItem(studentId, id, patch) {
  const data = load();
  const idx = data.items.findIndex((i) => i.id === id && i.studentId === studentId);
  if (idx < 0) return null;
  const row = { ...data.items[idx] };
  if (patch.status != null) {
    if (!desk.STATUSES.includes(patch.status)) fail('VALIDATION', 'invalid status');
    row.status = patch.status;
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'examDate')) {
    row.examDate = patch.examDate ? desk.formatIsoDate(patch.examDate) : null;
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'lastDate')) {
    row.lastDate = patch.lastDate ? desk.formatIsoDate(patch.lastDate) : null;
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'officialUrl')) {
    const url = String(patch.officialUrl || '').trim();
    if (url && !/^https:\/\//i.test(url)) fail('VALIDATION', 'officialUrl must be https');
    row.officialUrl = url;
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'notes')) {
    row.notes = String(patch.notes || '').trim();
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'title') && row.kind === 'custom') {
    const title = String(patch.title || '').trim();
    if (!title) fail('VALIDATION', 'title is required');
    row.title = title;
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'board') && row.kind === 'custom') {
    row.board = String(patch.board || '').trim();
  }
  row.updatedAt = new Date().toISOString();
  data.items[idx] = row;
  save(data);
  return publicItem(row, data);
}

function deleteItem(studentId, id) {
  const data = load();
  const next = data.items.filter((i) => !(i.id === id && i.studentId === studentId));
  if (next.length === data.items.length) return false;
  data.items = next;
  data.files = (data.files || []).filter((f) => f.itemId !== id);
  save(data);
  if (isUuid(studentId) && isUuid(id)) {
    fs.rmSync(itemDir(studentId, id), { recursive: true, force: true });
  }
  return true;
}

function saveFile(studentId, itemId, kind, input) {
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
  const data = load();
  const idx = data.items.findIndex((i) => i.id === itemId && i.studentId === studentId);
  if (idx < 0) return null;
  const dir = itemDir(studentId, itemId);
  fs.mkdirSync(dir, { recursive: true });
  unlinkKindFiles(dir, kind);
  const destName = `${kind}.${MIME_EXT[mime]}`;
  fs.writeFileSync(path.join(dir, destName), buffer);
  const now = new Date().toISOString();
  data.files = (data.files || []).filter((f) => !(f.itemId === itemId && f.kind === kind));
  const row = {
    id: crypto.randomUUID(),
    itemId,
    kind,
    storedPath: [studentId, itemId, destName].join('/'),
    mime,
    bytes: buffer.length,
    originalName: sanitizeName(input.originalName, kind, mime),
    uploadedAt: now,
  };
  data.files.push(row);
  data.items[idx] = { ...data.items[idx], updatedAt: now };
  save(data);
  return { item: publicItem(data.items[idx], data), file: publicFileMeta(row) };
}

function deleteFile(studentId, itemId, kind) {
  if (!FILE_KINDS.includes(kind)) fail('VALIDATION', 'kind must be admit or result');
  const data = load();
  const idx = data.items.findIndex((i) => i.id === itemId && i.studentId === studentId);
  if (idx < 0) return null;
  const before = (data.files || []).length;
  data.files = (data.files || []).filter((f) => !(f.itemId === itemId && f.kind === kind));
  if (data.files.length === before) {
    return { item: publicItem(data.items[idx], data), removed: false };
  }
  if (isUuid(studentId) && isUuid(itemId)) {
    unlinkKindFiles(itemDir(studentId, itemId), kind);
  }
  const now = new Date().toISOString();
  data.items[idx] = { ...data.items[idx], updatedAt: now };
  save(data);
  return { item: publicItem(data.items[idx], data), removed: true };
}

function resolveStored(storedPath) {
  const parts = String(storedPath || '').split(/[/\\]/).filter(Boolean);
  if (parts.length !== 3) fail('VALIDATION', 'invalid stored path');
  if (parts.some((p) => p === '..' || p === '.')) fail('VALIDATION', 'invalid stored path');
  if (!isUuid(parts[0]) || !isUuid(parts[1])) fail('VALIDATION', 'invalid stored path');
  if (!/^(admit|result)\.(pdf|jpg|png)$/.test(parts[2])) fail('VALIDATION', 'invalid stored path');
  return path.join(filesDir(), parts[0], parts[1], parts[2]);
}

function readFileForDownload(studentId, itemId, kind) {
  if (!FILE_KINDS.includes(kind)) fail('VALIDATION', 'kind must be admit or result');
  const data = load();
  const item = data.items.find((i) => i.id === itemId && i.studentId === studentId);
  if (!item) return null;
  const row = fileRow(data, itemId, kind);
  if (!row) return null;
  const abs = resolveStored(row.storedPath);
  if (!fs.existsSync(abs)) return null;
  return {
    buffer: fs.readFileSync(abs),
    mime: row.mime,
    originalName: row.originalName,
    bytes: row.bytes,
  };
}

module.exports = {
  MIN_PASSWORD,
  FILE_KINDS,
  MAX_FILE_BYTES,
  dataDir,
  filesDir,
  storePath,
  warnIfUnwritable,
  detectMime,
  normalizeEmail,
  publicStudent,
  findByEmail,
  findById,
  register,
  verifyPassword,
  getProfile,
  saveProfile,
  profileIsEmpty,
  load,
  save,
  listItems,
  getItem,
  createItem,
  updateItem,
  deleteItem,
  saveFile,
  deleteFile,
  readFileForDownload,
};
