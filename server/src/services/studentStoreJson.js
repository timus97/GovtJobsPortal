const crypto = require('crypto');
const fs = require('fs');
const { hash, verify } = require('./password');
const s = require('./studentStoreShared');

const BACKEND = 'json';

function emptyStore() {
  return { students: [], profiles: {}, items: [], files: [], topicProgress: [], mockAttempts: [] };
}

function load() {
  try {
    const raw = JSON.parse(fs.readFileSync(s.storePath(), 'utf8'));
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
  const file = s.storePath();
  fs.mkdirSync(require('path').dirname(file), { recursive: true });
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

function filesFor(data, itemId) {
  return (data.files || []).filter((f) => f.itemId === itemId);
}

function findByEmail(email) {
  const key = s.normalizeEmail(email);
  if (!key) return null;
  return load().students.find((row) => row.emailNorm === key) || null;
}

function findById(id) {
  return load().students.find((row) => row.id === id) || null;
}

function register({ email, password }) {
  const { rawEmail, emailNorm } = s.validateNewAccount({ email, password });
  const data = load();
  if (data.students.some((row) => row.emailNorm === emailNorm)) {
    s.fail('DUPLICATE', 'An account with this email already exists');
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
  return s.publicStudent(student);
}

function verifyPassword(email, password) {
  const row = findByEmail(email);
  if (!row || !verify(password, row.passwordHash)) return null;
  const data = load();
  const idx = data.students.findIndex((st) => st.id === row.id);
  if (idx >= 0) {
    data.students[idx] = { ...data.students[idx], lastLoginAt: new Date().toISOString() };
    save(data);
    return s.publicStudent(data.students[idx]);
  }
  return s.publicStudent(row);
}

function getProfile(studentId) {
  return load().profiles[studentId] || null;
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

function countSubmittedMocks(data, studentId) {
  return (data.mockAttempts || []).filter((a) => a.studentId === studentId && a.submittedAt).length;
}

function listItems(studentId, filter = {}) {
  const data = load();
  let items = data.items
    .filter((i) => i.studentId === studentId)
    .map((i) => s.decorateItem(i, filesFor(data, i.id)));
  if (filter.status && require('../../../shared/deskGuidance').STATUSES.includes(filter.status)) {
    items = items.filter((i) => i.status === filter.status);
  }
  items = s.sortItems(items);
  return {
    items,
    total: items.length,
    stats: s.itemStats(items, countSubmittedMocks(data, studentId)),
  };
}

function getItem(studentId, id) {
  const data = load();
  const row = data.items.find((i) => i.id === id && i.studentId === studentId);
  return row ? s.decorateItem(row, filesFor(data, row.id)) : null;
}

function createItem(studentId, input) {
  if (!findById(studentId)) return null;
  const built = s.buildCatalogItem(studentId, input);
  const data = load();
  if (built.kind === 'series' || built.kind === 'opportunity') {
    const existing = data.items.find(
      (i) => i.studentId === studentId && i.kind === built.kind && i.refId === built.row.refId
    );
    if (existing) {
      if (built.status !== existing.status) {
        existing.status = built.status;
        existing.updatedAt = built.now;
        save(data);
      }
      return s.decorateItem(existing, filesFor(data, existing.id));
    }
  }
  const row = { id: crypto.randomUUID(), ...built.row };
  data.items.push(row);
  save(data);
  return s.decorateItem(row, filesFor(data, row.id));
}

function updateItem(studentId, id, patch) {
  const data = load();
  const idx = data.items.findIndex((i) => i.id === id && i.studentId === studentId);
  if (idx < 0) return null;
  data.items[idx] = s.applyItemPatch(data.items[idx], patch);
  save(data);
  return s.decorateItem(data.items[idx], filesFor(data, id));
}

function deleteItem(studentId, id) {
  const data = load();
  const next = data.items.filter((i) => !(i.id === id && i.studentId === studentId));
  if (next.length === data.items.length) return false;
  data.items = next;
  data.files = (data.files || []).filter((f) => f.itemId !== id);
  save(data);
  if (s.isUuid(studentId) && s.isUuid(id)) {
    fs.rmSync(s.itemDir(studentId, id), { recursive: true, force: true });
  }
  return true;
}

function saveFile(studentId, itemId, kind, input) {
  const { buffer, mime } = s.validateFileBuffer(kind, input);
  const data = load();
  const idx = data.items.findIndex((i) => i.id === itemId && i.studentId === studentId);
  if (idx < 0) return null;
  const storedPath = s.writeKindFile(studentId, itemId, kind, mime, buffer);
  const now = new Date().toISOString();
  data.files = (data.files || []).filter((f) => !(f.itemId === itemId && f.kind === kind));
  const row = {
    id: crypto.randomUUID(),
    itemId,
    kind,
    storedPath,
    mime,
    bytes: buffer.length,
    originalName: s.sanitizeName(input.originalName, kind, mime),
    uploadedAt: now,
  };
  data.files.push(row);
  data.items[idx] = { ...data.items[idx], updatedAt: now };
  save(data);
  return { item: s.decorateItem(data.items[idx], filesFor(data, itemId)), file: s.publicFileMeta(row) };
}

function deleteFile(studentId, itemId, kind) {
  if (!s.FILE_KINDS.includes(kind)) s.fail('VALIDATION', 'kind must be admit or result');
  const data = load();
  const idx = data.items.findIndex((i) => i.id === itemId && i.studentId === studentId);
  if (idx < 0) return null;
  const before = (data.files || []).length;
  data.files = (data.files || []).filter((f) => !(f.itemId === itemId && f.kind === kind));
  if (data.files.length === before) {
    return { item: s.decorateItem(data.items[idx], filesFor(data, itemId)), removed: false };
  }
  if (s.isUuid(studentId) && s.isUuid(itemId)) {
    s.unlinkKindFiles(s.itemDir(studentId, itemId), kind);
  }
  const now = new Date().toISOString();
  data.items[idx] = { ...data.items[idx], updatedAt: now };
  save(data);
  return { item: s.decorateItem(data.items[idx], filesFor(data, itemId)), removed: true };
}

function readFileForDownload(studentId, itemId, kind) {
  if (!s.FILE_KINDS.includes(kind)) s.fail('VALIDATION', 'kind must be admit or result');
  const data = load();
  const item = data.items.find((i) => i.id === itemId && i.studentId === studentId);
  if (!item) return null;
  const row = (data.files || []).find((f) => f.itemId === itemId && f.kind === kind);
  if (!row) return null;
  const abs = s.resolveStored(row.storedPath);
  if (!fs.existsSync(abs)) return null;
  return {
    buffer: fs.readFileSync(abs),
    mime: row.mime,
    originalName: row.originalName,
    bytes: row.bytes,
  };
}

function topicProgressMap(studentId, seriesId) {
  const out = {};
  for (const row of load().topicProgress || []) {
    if (row.studentId === studentId && row.seriesId === seriesId && row.topicId && row.doneAt) {
      out[row.topicId] = row.doneAt;
    }
  }
  return out;
}

function setTopicProgress(studentId, seriesId, topicId, done) {
  const data = load();
  data.topicProgress = (data.topicProgress || []).filter(
    (r) => !(r.studentId === studentId && r.seriesId === seriesId && r.topicId === topicId)
  );
  if (done) {
    data.topicProgress.push({
      studentId,
      seriesId,
      topicId,
      doneAt: new Date().toISOString(),
    });
  }
  save(data);
  return topicProgressMap(studentId, seriesId);
}

function insertMockAttempt(row) {
  const data = load();
  data.mockAttempts = Array.isArray(data.mockAttempts) ? data.mockAttempts : [];
  data.mockAttempts.push(row);
  save(data);
  return row;
}

function findMockAttempt(studentId, attemptId) {
  return (load().mockAttempts || []).find((a) => a.id === attemptId && a.studentId === studentId) || null;
}

function findOpenMockAttempt(studentId, seriesId) {
  return (
    (load().mockAttempts || []).find(
      (a) => a.studentId === studentId && a.seriesId === seriesId && !a.submittedAt
    ) || null
  );
}

function saveMockAttempt(row) {
  const data = load();
  data.mockAttempts = Array.isArray(data.mockAttempts) ? data.mockAttempts : [];
  const idx = data.mockAttempts.findIndex((a) => a.id === row.id && a.studentId === row.studentId);
  if (idx < 0) return null;
  data.mockAttempts[idx] = row;
  save(data);
  return data.mockAttempts[idx];
}

function ready() {
  return Promise.resolve({ backend: BACKEND });
}

module.exports = {
  BACKEND,
  MIN_PASSWORD: s.MIN_PASSWORD,
  FILE_KINDS: s.FILE_KINDS,
  MAX_FILE_BYTES: s.MAX_FILE_BYTES,
  dataDir: s.dataDir,
  filesDir: s.filesDir,
  storePath: s.storePath,
  warnIfUnwritable: s.warnIfUnwritable,
  detectMime: s.detectMime,
  normalizeEmail: s.normalizeEmail,
  publicStudent: s.publicStudent,
  findByEmail,
  findById,
  register,
  verifyPassword,
  getProfile,
  saveProfile,
  profileIsEmpty: s.profileIsEmpty,
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
  topicProgressMap,
  setTopicProgress,
  insertMockAttempt,
  findMockAttempt,
  findOpenMockAttempt,
  saveMockAttempt,
  ready,
};
