/**
 * ExamSeries: official calendar / prepare-for rows.
 * Not a vacancy. Apply is allowed only via a linked open Opportunity.
 */
const crypto = require('crypto');
const { QUALIFICATIONS } = require('./jobSchema');

const BOARDS = ['UPSC', 'SSC', 'IBPS', 'SBI', 'RRB', 'NTA'];

const CUET_RE = /\bcuet\b/i;
const NET_RE = /\bugc\s*net\b|\bnta\s*net\b/i;

function isCuetName(name) {
  return CUET_RE.test(String(name || ''));
}

function isNetName(name) {
  return NET_RE.test(String(name || ''));
}

function stableSeriesId(parts) {
  const raw = [parts.board, parts.name, parts.cycle || '']
    .map((s) => String(s || '').toLowerCase().trim())
    .join('|');
  return crypto.createHash('sha256').update(raw).digest('hex').slice(0, 16);
}

function normalizeTitle(value) {
  return String(value || '')
    .toLowerCase()
    .replace(/\(fixture\)/g, ' ')
    .replace(/\b(examination|exam|recruitment|notification|advertisement|common recruitment process|crp)\b/g, ' ')
    .replace(/[^a-z0-9]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function isValidExamSeries(s) {
  const errors = [];
  if (!s || typeof s !== 'object') return ['not an object'];
  if (!s.id) errors.push('id required');
  if (!s.board) errors.push('board required');
  if (!s.name) errors.push('name required');
  if (isCuetName(s.name)) errors.push('CUET is excluded');
  if (!s.officialUrl || !/^https:\/\//i.test(s.officialUrl)) errors.push('officialUrl must be https');
  if (s.kind && s.kind !== 'series') errors.push('kind must be series');
  if (s.minEducation && !QUALIFICATIONS.includes(s.minEducation) && s.minEducation !== 'phd' && s.minEducation !== 'postgraduate') {
    errors.push('invalid minEducation');
  }
  return errors;
}

function normalizeExamSeries(raw, now = new Date().toISOString()) {
  if (!raw || typeof raw !== 'object') return null;
  if (isCuetName(raw.name)) return null;
  const board = String(raw.board || '').trim();
  const name = String(raw.name || '').trim();
  const officialUrl = raw.officialUrl || raw.official_url || '';
  if (!board || !name || !/^https:\/\//i.test(officialUrl)) return null;
  const applyNever = raw.applyNever === true || isNetName(name);
  const id = raw.id || stableSeriesId({ board, name, cycle: raw.cycle });
  return {
    id,
    kind: 'series',
    board,
    name,
    cycle: raw.cycle || null,
    sourceId: raw.sourceId || raw.source_id || null,
    officialUrl,
    expectedNotify: raw.expectedNotify || raw.expected_notify || raw.notificationDate || null,
    expectedApply: raw.expectedApply || raw.expected_apply || raw.lastDate || null,
    expectedExam: raw.expectedExam || raw.expected_exam || raw.examDate || null,
    minEducation: raw.minEducation || raw.min_education || null,
    applyNever,
    aliases: Array.isArray(raw.aliases) ? raw.aliases : [],
    linkedOpportunityIds: Array.isArray(raw.linkedOpportunityIds) ? raw.linkedOpportunityIds : [],
    updatedAt: raw.updatedAt || now,
  };
}

const BOARD_ORG_RE = {
  upsc: /union public service|\bupsc\b/,
  ssc: /staff selection|\bssc\b/,
  ibps: /\bibps\b|banking personnel/,
  sbi: /state bank|\bsbi\b/,
  rrb: /railway recruitment|\brrb\b/,
  nta: /\bnta\b|ugc\s*net/,
};

function boardMatchesJob(series, job) {
  const board = String(series.board || '').toLowerCase();
  if (!board) return true;
  const blob = `${job.organization || ''} ${job.sourceId || ''} ${job.sourceName || ''}`.toLowerCase();
  if (blob.includes(board)) return true;
  const re = BOARD_ORG_RE[board];
  return re ? re.test(blob) : true;
}

function seriesMatchesJob(series, job) {
  if (!series || !job || series.applyNever) return false;
  const jobTitle = normalizeTitle(job.title);
  if (!jobTitle) return false;
  const names = [series.name, ...(series.aliases || [])].map(normalizeTitle).filter(Boolean);
  if (!names.some((n) => jobTitle.includes(n) || n.includes(jobTitle))) return false;
  return boardMatchesJob(series, job);
}

function looksLikeCalendarRow(raw) {
  if (!raw || typeof raw !== 'object') return false;
  if (isCuetName(raw.title || raw.name)) return true;
  const sourceId = String(raw.sourceId || '');
  const version = String(raw.collectorVersion || '');
  if (/_calendar$/i.test(sourceId)) return true;
  if (/calendar/i.test(version)) return true;
  if (raw.examDate && raw.hasExam === true && !raw.lastDate) return true;
  return false;
}

module.exports = {
  BOARDS,
  CUET_RE,
  isCuetName,
  isNetName,
  stableSeriesId,
  normalizeTitle,
  isValidExamSeries,
  normalizeExamSeries,
  seriesMatchesJob,
  looksLikeCalendarRow,
};
