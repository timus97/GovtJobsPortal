const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { hash, verify } = require('./password');

const MIN_PASSWORD = 10;

function dataDir() {
  return process.env.STUDENT_DATA_DIR || path.join(__dirname, '..', '..', '..', 'data', 'students');
}

function storePath() {
  return path.join(dataDir(), 'students.json');
}

function warnIfUnwritable() {
  if (process.env.NODE_ENV !== 'production') return;
  const dir = dataDir();
  try {
    fs.mkdirSync(dir, { recursive: true });
    fs.accessSync(dir, fs.constants.W_OK);
  } catch {
    console.warn(
      `Student store is not writable (${dir}). Use a persistent disk. Ephemeral hosts lose accounts on sleep/redeploy.`
    );
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

module.exports = {
  MIN_PASSWORD,
  dataDir,
  storePath,
  warnIfUnwritable,
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
};
