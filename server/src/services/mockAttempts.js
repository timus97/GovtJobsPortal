const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const studentStore = require('./studentStore');
const { publicBank, scoreAttempt } = require('../../../shared/mockScore');

const SERIES_RE = /^[a-z0-9]+(?:-[a-z0-9]+)*$/i;

function banksDir() {
  return path.join(__dirname, '..', '..', '..', 'data', 'coaching', 'mocks');
}

function normalizeSeriesId(seriesId) {
  const id = String(seriesId || '')
    .trim()
    .toLowerCase();
  return SERIES_RE.test(id) ? id : '';
}

function loadBank(seriesId) {
  const id = normalizeSeriesId(seriesId);
  if (!id) return null;
  const dir = path.resolve(banksDir());
  const file = path.resolve(dir, `${id}.json`);
  const prefix = dir.endsWith(path.sep) ? dir : dir + path.sep;
  if (!file.startsWith(prefix)) return null;
  if (!fs.existsSync(file)) return null;
  try {
    const bank = JSON.parse(fs.readFileSync(file, 'utf8'));
    if (!bank || bank.unofficial !== true || !Array.isArray(bank.questions) || !bank.questions.length) {
      return null;
    }
    return bank;
  } catch {
    return null;
  }
}

function publicAttempt(row, { includeAnswers = false } = {}) {
  if (!row) return null;
  const out = {
    id: row.id,
    studentId: row.studentId,
    seriesId: row.seriesId,
    itemId: row.itemId || null,
    startedAt: row.startedAt,
    submittedAt: row.submittedAt || null,
  };
  if (row.submittedAt) {
    out.score = row.score;
    out.total = row.total;
    if (includeAnswers) out.answers = row.answers && typeof row.answers === 'object' ? row.answers : {};
  }
  return out;
}

async function resolveItemId(studentId, itemId) {
  if (!itemId) return null;
  const item = await studentStore.getItem(studentId, String(itemId));
  return item ? item.id : null;
}

async function startAttempt(studentId, seriesId, input = {}) {
  if (!(await studentStore.findById(studentId))) return { error: 'UNAUTHORIZED' };
  const bank = loadBank(seriesId);
  if (!bank) return { error: 'NOT_FOUND' };
  const seriesKey = bank.seriesId || normalizeSeriesId(seriesId);
  const open = await studentStore.findOpenMockAttempt(studentId, seriesKey);
  if (open) {
    return {
      attempt: publicAttempt(open),
      bank: publicBank(bank),
      durationMin: Number(bank.durationMin) > 0 ? Number(bank.durationMin) : 20,
    };
  }
  const now = new Date().toISOString();
  const row = {
    id: crypto.randomUUID(),
    studentId,
    seriesId: seriesKey,
    itemId: await resolveItemId(studentId, input.itemId),
    startedAt: now,
    submittedAt: null,
    score: null,
    total: null,
    answers: {},
  };
  await studentStore.insertMockAttempt(row);
  return {
    attempt: publicAttempt(row),
    bank: publicBank(bank),
    durationMin: Number(bank.durationMin) > 0 ? Number(bank.durationMin) : 20,
  };
}

async function getAttempt(studentId, attemptId) {
  const row = await studentStore.findMockAttempt(studentId, attemptId);
  if (!row) return { error: 'NOT_FOUND' };
  const bank = loadBank(row.seriesId);
  const submitted = Boolean(row.submittedAt);
  const out = {
    attempt: publicAttempt(row, { includeAnswers: submitted }),
  };
  if (bank) {
    out.bank = publicBank(bank);
    out.durationMin = Number(bank.durationMin) > 0 ? Number(bank.durationMin) : 20;
    if (submitted) {
      out.review = scoreAttempt(bank, row.answers || {}).review;
    }
  }
  return out;
}

function cleanAnswers(bank, answers) {
  const clean = {};
  if (!answers || typeof answers !== 'object' || Array.isArray(answers)) return clean;
  const allowed = new Set((bank.questions || []).map((q) => q.id));
  for (const [key, raw] of Object.entries(answers)) {
    if (!allowed.has(key)) continue;
    const n = typeof raw === 'number' ? raw : Number(raw);
    if (!Number.isInteger(n)) continue;
    clean[key] = n;
  }
  return clean;
}

async function submitAttempt(studentId, attemptId, answers) {
  const row = await studentStore.findMockAttempt(studentId, attemptId);
  if (!row) return { error: 'NOT_FOUND' };
  if (row.submittedAt) return { error: 'ALREADY_SUBMITTED' };
  const bank = loadBank(row.seriesId);
  if (!bank) return { error: 'NOT_FOUND' };
  const clean = cleanAnswers(bank, answers);
  const scored = scoreAttempt(bank, clean);
  const next = {
    ...row,
    submittedAt: new Date().toISOString(),
    score: scored.score,
    total: scored.total,
    answers: clean,
  };
  await studentStore.saveMockAttempt(next);
  return {
    attempt: publicAttempt(next, { includeAnswers: true }),
    review: scored.review,
  };
}

module.exports = {
  loadBank,
  publicAttempt,
  startAttempt,
  getAttempt,
  submitAttempt,
};
