/**
 * Canonical job shape and helpers shared by process pipeline and API.
 */

const ORG_TYPES = ['central', 'psu', 'govt_company', 'autonomous'];

const SELECTION_PROCESSES = [
  'walk_in',
  'interview_only',
  'merit',
  'contract_interview',
  'direct_recruitment',
  'apprenticeship',
];

const STATUSES = ['open', 'closing_soon', 'closed'];

const QUALIFICATIONS = [
  'below_10',
  '10th',
  '12th',
  'iti',
  'diploma',
  'graduate',
  'pg',
  'experience',
];

const EXCLUDE_PATTERNS = [
  /written\s+test/i,
  /written\s+examination/i,
  /competitive\s+exam/i,
  /\bcbt\b/i,
  /computer\s*[- ]?based\s+test/i,
  /online\s+test/i,
  /online\s+examination/i,
  /\bgate\b/i,
  /\bnet\b.*exam/i,
  /\bjrf\b/i,
  /\bupsc\b/i,
  /\bssc\b/i,
  /\bibps\b/i,
  /rrb\s+exam/i,
  /tier[-\s]?(i|ii|iii|1|2|3)\b/i,
  /preliminary\s+exam/i,
  /mains\s+examination/i,
  /departmental\s+competitive/i,
];

const INCLUDE_PATTERNS = [
  { code: 'walk_in', re: /walk[-\s]?in/i },
  { code: 'interview_only', re: /interview\s+only|only\s+interview|personal\s+interview/i },
  { code: 'merit', re: /purely\s+on\s+merit|no\s+written\s+test|without\s+written/i },
  { code: 'contract_interview', re: /contract.*interview|consultant.*interview|project\s+staff/i },
  { code: 'direct_recruitment', re: /direct\s+recruitment/i },
  { code: 'apprenticeship', re: /apprentice|apprentices\s+act/i },
];

function classifySelectionText(text = '') {
  const blob = String(text);
  for (const re of EXCLUDE_PATTERNS) {
    if (re.test(blob)) {
      return { hasExam: true, selectionProcess: null, reason: 'exclude_keyword' };
    }
  }
  for (const { code, re } of INCLUDE_PATTERNS) {
    if (re.test(blob)) {
      return { hasExam: false, selectionProcess: code, reason: 'include_keyword' };
    }
  }
  return { hasExam: null, selectionProcess: null, reason: 'unknown' };
}

function isValidJob(job, { allowNeedsReview = false } = {}) {
  const errors = [];
  if (!job || typeof job !== 'object') return ['not an object'];
  if (!job.id) errors.push('id required');
  if (!job.title) errors.push('title required');
  if (!job.organization) errors.push('organization required');
  if (!ORG_TYPES.includes(job.orgType)) errors.push('invalid orgType');
  if (!SELECTION_PROCESSES.includes(job.selectionProcess)) errors.push('invalid selectionProcess');
  if (job.hasExam !== false) errors.push('hasExam must be false for publish');
  if (!job.officialUrl || !/^https?:\/\//i.test(job.officialUrl)) errors.push('officialUrl required');
  if (!job.sourceId) errors.push('sourceId required');
  if (!job.sourceName) errors.push('sourceName required');
  if (!job.sourceUrl) errors.push('sourceUrl required');
  if (!STATUSES.includes(job.status)) errors.push('invalid status');
  if (job.needsReview && !allowNeedsReview) errors.push('needsReview not allowed in publish set');
  return errors;
}

function computeStatus(lastDate, today = new Date()) {
  if (!lastDate) return 'open';
  const end = new Date(lastDate);
  if (Number.isNaN(end.getTime())) return 'open';
  const t = new Date(today);
  t.setHours(0, 0, 0, 0);
  end.setHours(0, 0, 0, 0);
  if (end < t) return 'closed';
  const diffDays = (end - t) / (1000 * 60 * 60 * 24);
  if (diffDays <= 7) return 'closing_soon';
  return 'open';
}

function stableJobId(parts) {
  const crypto = require('crypto');
  const raw = [parts.organization, parts.title, parts.lastDate || '', parts.officialUrl || '']
    .map((s) => String(s).toLowerCase().trim())
    .join('|');
  return crypto.createHash('sha256').update(raw).digest('hex').slice(0, 16);
}

module.exports = {
  ORG_TYPES,
  SELECTION_PROCESSES,
  STATUSES,
  QUALIFICATIONS,
  EXCLUDE_PATTERNS,
  INCLUDE_PATTERNS,
  classifySelectionText,
  isValidJob,
  computeStatus,
  stableJobId,
};
