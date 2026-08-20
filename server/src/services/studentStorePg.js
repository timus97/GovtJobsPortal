const crypto = require('crypto');
const fs = require('fs');
const { hash, verify } = require('./password');
const s = require('./studentStoreShared');
const pg = require('../db/studentPg');

const BACKEND = 'postgres';

function mapStudent(row) {
  if (!row) return null;
  return {
    id: row.id,
    email: row.email,
    emailNorm: row.email_norm,
    passwordHash: row.password_hash,
    createdAt: s.iso(row.created_at),
    lastLoginAt: s.iso(row.last_login_at),
  };
}

function mapItem(row) {
  if (!row) return null;
  return {
    id: row.id,
    studentId: row.student_id,
    kind: row.kind,
    refId: row.ref_id,
    title: row.title,
    board: row.board || '',
    status: row.status,
    examDate: s.isoDate(row.exam_date),
    lastDate: s.isoDate(row.last_date),
    officialUrl: row.official_url || '',
    notes: row.notes || '',
    createdAt: s.iso(row.created_at),
    updatedAt: s.iso(row.updated_at),
  };
}

function mapFile(row) {
  if (!row) return null;
  return {
    id: row.id,
    itemId: row.item_id,
    kind: row.kind,
    storedPath: row.stored_path,
    mime: row.mime,
    bytes: Number(row.bytes),
    originalName: row.original_name,
    uploadedAt: s.iso(row.uploaded_at),
  };
}

function mapAttempt(row) {
  if (!row) return null;
  return {
    id: row.id,
    studentId: row.student_id,
    seriesId: row.series_id,
    itemId: row.item_id,
    startedAt: s.iso(row.started_at),
    submittedAt: row.submitted_at ? s.iso(row.submitted_at) : null,
    score: row.score,
    total: row.total,
    answers: row.answers || {},
  };
}

async function filesFor(itemId) {
  const { rows } = await pg.query('SELECT * FROM desk_files WHERE item_id = $1', [itemId]);
  return rows.map(mapFile);
}

async function findByEmail(email) {
  const key = s.normalizeEmail(email);
  if (!key) return null;
  const { rows } = await pg.query('SELECT * FROM students WHERE email_norm = $1', [key]);
  return mapStudent(rows[0]);
}

async function findById(id) {
  const { rows } = await pg.query('SELECT * FROM students WHERE id = $1', [id]);
  return mapStudent(rows[0]);
}

async function register({ email, password }) {
  const { rawEmail, emailNorm } = s.validateNewAccount({ email, password });
  const now = new Date().toISOString();
  const student = {
    id: crypto.randomUUID(),
    email: rawEmail,
    emailNorm,
    passwordHash: hash(password),
    createdAt: now,
    lastLoginAt: now,
  };
  try {
    await pg.query(
      `INSERT INTO students (id, email, email_norm, password_hash, created_at, last_login_at)
       VALUES ($1, $2, $3, $4, $5, $6)`,
      [student.id, student.email, student.emailNorm, student.passwordHash, student.createdAt, student.lastLoginAt]
    );
  } catch (err) {
    if (err && err.code === '23505') s.fail('DUPLICATE', 'An account with this email already exists');
    throw err;
  }
  return s.publicStudent(student);
}

async function verifyPassword(email, password) {
  const row = await findByEmail(email);
  if (!row || !verify(password, row.passwordHash)) return null;
  const lastLoginAt = new Date().toISOString();
  await pg.query('UPDATE students SET last_login_at = $2 WHERE id = $1', [row.id, lastLoginAt]);
  return s.publicStudent({ ...row, lastLoginAt });
}

async function getProfile(studentId) {
  const { rows } = await pg.query('SELECT profile FROM student_profiles WHERE student_id = $1', [studentId]);
  return rows[0] ? rows[0].profile : null;
}

async function saveProfile(studentId, profile) {
  if (!(await findById(studentId))) return null;
  const next = {
    ...(profile && typeof profile === 'object' ? profile : {}),
    updatedAt: new Date().toISOString(),
  };
  await pg.query(
    `INSERT INTO student_profiles (student_id, profile, updated_at)
     VALUES ($1, $2::jsonb, $3)
     ON CONFLICT (student_id) DO UPDATE SET profile = EXCLUDED.profile, updated_at = EXCLUDED.updated_at`,
    [studentId, JSON.stringify(next), next.updatedAt]
  );
  return next;
}

async function countSubmittedMocks(studentId) {
  const { rows } = await pg.query(
    `SELECT COUNT(*)::int AS n FROM mock_attempts WHERE student_id = $1 AND submitted_at IS NOT NULL`,
    [studentId]
  );
  return rows[0] ? rows[0].n : 0;
}

async function listItems(studentId, filter = {}) {
  const params = [studentId];
  let sql = 'SELECT * FROM desk_items WHERE student_id = $1';
  if (filter.status && require('../../../shared/deskGuidance').STATUSES.includes(filter.status)) {
    params.push(filter.status);
    sql += ` AND status = $2`;
  }
  const { rows } = await pg.query(sql, params);
  const items = [];
  for (const row of rows) {
    items.push(s.decorateItem(mapItem(row), await filesFor(row.id)));
  }
  const sorted = s.sortItems(items);
  return {
    items: sorted,
    total: sorted.length,
    stats: s.itemStats(sorted, await countSubmittedMocks(studentId)),
  };
}

async function getItem(studentId, id) {
  const { rows } = await pg.query('SELECT * FROM desk_items WHERE id = $1 AND student_id = $2', [id, studentId]);
  if (!rows[0]) return null;
  return s.decorateItem(mapItem(rows[0]), await filesFor(id));
}

async function createItem(studentId, input) {
  if (!(await findById(studentId))) return null;
  const built = s.buildCatalogItem(studentId, input);
  if (built.kind === 'series' || built.kind === 'opportunity') {
    const { rows } = await pg.query(
      `SELECT * FROM desk_items WHERE student_id = $1 AND kind = $2 AND ref_id = $3`,
      [studentId, built.kind, built.row.refId]
    );
    if (rows[0]) {
      if (built.status !== rows[0].status) {
        await pg.query('UPDATE desk_items SET status = $2, updated_at = $3 WHERE id = $1', [
          rows[0].id,
          built.status,
          built.now,
        ]);
        rows[0].status = built.status;
        rows[0].updated_at = built.now;
      }
      return s.decorateItem(mapItem(rows[0]), await filesFor(rows[0].id));
    }
  }
  const row = { id: crypto.randomUUID(), ...built.row };
  await pg.query(
    `INSERT INTO desk_items
      (id, student_id, kind, ref_id, title, board, status, exam_date, last_date, official_url, notes, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)`,
    [
      row.id,
      row.studentId,
      row.kind,
      row.refId,
      row.title,
      row.board,
      row.status,
      row.examDate,
      row.lastDate,
      row.officialUrl,
      row.notes,
      row.createdAt,
      row.updatedAt,
    ]
  );
  return s.decorateItem(row, []);
}

async function updateItem(studentId, id, patch) {
  const { rows } = await pg.query('SELECT * FROM desk_items WHERE id = $1 AND student_id = $2', [id, studentId]);
  if (!rows[0]) return null;
  const next = s.applyItemPatch(mapItem(rows[0]), patch);
  await pg.query(
    `UPDATE desk_items SET
      status = $2, exam_date = $3, last_date = $4, official_url = $5, notes = $6,
      title = $7, board = $8, updated_at = $9
     WHERE id = $1 AND student_id = $10`,
    [
      id,
      next.status,
      next.examDate,
      next.lastDate,
      next.officialUrl,
      next.notes,
      next.title,
      next.board,
      next.updatedAt,
      studentId,
    ]
  );
  return s.decorateItem(next, await filesFor(id));
}

async function deleteItem(studentId, id) {
  const { rowCount } = await pg.query('DELETE FROM desk_items WHERE id = $1 AND student_id = $2', [id, studentId]);
  if (!rowCount) return false;
  if (s.isUuid(studentId) && s.isUuid(id)) {
    fs.rmSync(s.itemDir(studentId, id), { recursive: true, force: true });
  }
  return true;
}

async function saveFile(studentId, itemId, kind, input) {
  const { buffer, mime } = s.validateFileBuffer(kind, input);
  const { rows } = await pg.query('SELECT * FROM desk_items WHERE id = $1 AND student_id = $2', [
    itemId,
    studentId,
  ]);
  if (!rows[0]) return null;
  const storedPath = s.writeKindFile(studentId, itemId, kind, mime, buffer);
  const now = new Date().toISOString();
  const file = {
    id: crypto.randomUUID(),
    itemId,
    kind,
    storedPath,
    mime,
    bytes: buffer.length,
    originalName: s.sanitizeName(input.originalName, kind, mime),
    uploadedAt: now,
  };
  await pg.query(
    `INSERT INTO desk_files (id, item_id, kind, stored_path, mime, bytes, original_name, uploaded_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8)
     ON CONFLICT (item_id, kind) DO UPDATE SET
       id = EXCLUDED.id, stored_path = EXCLUDED.stored_path, mime = EXCLUDED.mime,
       bytes = EXCLUDED.bytes, original_name = EXCLUDED.original_name, uploaded_at = EXCLUDED.uploaded_at`,
    [file.id, file.itemId, file.kind, file.storedPath, file.mime, file.bytes, file.originalName, file.uploadedAt]
  );
  await pg.query('UPDATE desk_items SET updated_at = $2 WHERE id = $1', [itemId, now]);
  const item = mapItem(rows[0]);
  item.updatedAt = now;
  return { item: s.decorateItem(item, await filesFor(itemId)), file: s.publicFileMeta(file) };
}

async function deleteFile(studentId, itemId, kind) {
  if (!s.FILE_KINDS.includes(kind)) s.fail('VALIDATION', 'kind must be admit or result');
  const { rows } = await pg.query('SELECT * FROM desk_items WHERE id = $1 AND student_id = $2', [
    itemId,
    studentId,
  ]);
  if (!rows[0]) return null;
  const del = await pg.query('DELETE FROM desk_files WHERE item_id = $1 AND kind = $2', [itemId, kind]);
  if (!del.rowCount) {
    return { item: s.decorateItem(mapItem(rows[0]), await filesFor(itemId)), removed: false };
  }
  if (s.isUuid(studentId) && s.isUuid(itemId)) {
    s.unlinkKindFiles(s.itemDir(studentId, itemId), kind);
  }
  const now = new Date().toISOString();
  await pg.query('UPDATE desk_items SET updated_at = $2 WHERE id = $1', [itemId, now]);
  const item = mapItem(rows[0]);
  item.updatedAt = now;
  return { item: s.decorateItem(item, await filesFor(itemId)), removed: true };
}

async function readFileForDownload(studentId, itemId, kind) {
  if (!s.FILE_KINDS.includes(kind)) s.fail('VALIDATION', 'kind must be admit or result');
  const item = await pg.query('SELECT id FROM desk_items WHERE id = $1 AND student_id = $2', [itemId, studentId]);
  if (!item.rows[0]) return null;
  const { rows } = await pg.query('SELECT * FROM desk_files WHERE item_id = $1 AND kind = $2', [itemId, kind]);
  if (!rows[0]) return null;
  const row = mapFile(rows[0]);
  const abs = s.resolveStored(row.storedPath);
  if (!fs.existsSync(abs)) return null;
  return {
    buffer: fs.readFileSync(abs),
    mime: row.mime,
    originalName: row.originalName,
    bytes: row.bytes,
  };
}

async function topicProgressMap(studentId, seriesId) {
  const { rows } = await pg.query(
    `SELECT topic_id, done_at FROM topic_progress WHERE student_id = $1 AND series_id = $2`,
    [studentId, seriesId]
  );
  const out = {};
  for (const row of rows) out[row.topic_id] = s.iso(row.done_at);
  return out;
}

async function setTopicProgress(studentId, seriesId, topicId, done) {
  if (done) {
    await pg.query(
      `INSERT INTO topic_progress (student_id, series_id, topic_id, done_at)
       VALUES ($1, $2, $3, $4)
       ON CONFLICT (student_id, series_id, topic_id) DO UPDATE SET done_at = EXCLUDED.done_at`,
      [studentId, seriesId, topicId, new Date().toISOString()]
    );
  } else {
    await pg.query(
      `DELETE FROM topic_progress WHERE student_id = $1 AND series_id = $2 AND topic_id = $3`,
      [studentId, seriesId, topicId]
    );
  }
  return topicProgressMap(studentId, seriesId);
}

async function insertMockAttempt(row) {
  await pg.query(
    `INSERT INTO mock_attempts
      (id, student_id, series_id, item_id, started_at, submitted_at, score, total, answers)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9::jsonb)`,
    [
      row.id,
      row.studentId,
      row.seriesId,
      row.itemId || null,
      row.startedAt,
      row.submittedAt || null,
      row.score,
      row.total,
      JSON.stringify(row.answers || {}),
    ]
  );
  return row;
}

async function findMockAttempt(studentId, attemptId) {
  const { rows } = await pg.query('SELECT * FROM mock_attempts WHERE id = $1 AND student_id = $2', [
    attemptId,
    studentId,
  ]);
  return mapAttempt(rows[0]);
}

async function findOpenMockAttempt(studentId, seriesId) {
  const { rows } = await pg.query(
    `SELECT * FROM mock_attempts
     WHERE student_id = $1 AND series_id = $2 AND submitted_at IS NULL
     ORDER BY started_at DESC LIMIT 1`,
    [studentId, seriesId]
  );
  return mapAttempt(rows[0]);
}

async function saveMockAttempt(row) {
  const { rowCount } = await pg.query(
    `UPDATE mock_attempts SET
      submitted_at = $3, score = $4, total = $5, answers = $6::jsonb
     WHERE id = $1 AND student_id = $2`,
    [row.id, row.studentId, row.submittedAt, row.score, row.total, JSON.stringify(row.answers || {})]
  );
  return rowCount ? row : null;
}

async function load() {
  const [students, profiles, items, files, topics, attempts] = await Promise.all([
    pg.query('SELECT * FROM students'),
    pg.query('SELECT * FROM student_profiles'),
    pg.query('SELECT * FROM desk_items'),
    pg.query('SELECT * FROM desk_files'),
    pg.query('SELECT * FROM topic_progress'),
    pg.query('SELECT * FROM mock_attempts'),
  ]);
  const profileMap = {};
  for (const row of profiles.rows) profileMap[row.student_id] = row.profile;
  return {
    students: students.rows.map(mapStudent),
    profiles: profileMap,
    items: items.rows.map(mapItem),
    files: files.rows.map(mapFile),
    topicProgress: topics.rows.map((r) => ({
      studentId: r.student_id,
      seriesId: r.series_id,
      topicId: r.topic_id,
      doneAt: s.iso(r.done_at),
    })),
    mockAttempts: attempts.rows.map(mapAttempt),
  };
}

async function save() {
  s.fail('VALIDATION', 'studentStore.save is not supported for postgres; use the row APIs');
}

async function ready() {
  const report = await pg.migrate();
  return { backend: BACKEND, ...report };
}

function warnIfUnwritable() {
  s.warnIfUnwritable();
}

module.exports = {
  BACKEND,
  MIN_PASSWORD: s.MIN_PASSWORD,
  FILE_KINDS: s.FILE_KINDS,
  MAX_FILE_BYTES: s.MAX_FILE_BYTES,
  dataDir: s.dataDir,
  filesDir: s.filesDir,
  storePath: s.storePath,
  warnIfUnwritable,
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
