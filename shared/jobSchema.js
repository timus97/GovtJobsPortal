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
  'cbt',
  'written_multi_stage',
  'interview_after_exam',
  'physical',
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

const EXAM_PATTERNS = [
  {
    code: 'interview_after_exam',
    re: /interview\s+after\s+(?:a\s+)?(?:written|cbt|computer\s*[- ]?based|online\s+(?:test|exam))/i,
  },
  {
    code: 'interview_after_exam',
    re: /(?:written(?:\s+test)?|cbt|computer\s*[- ]?based\s+test).{0,48}followed\s+by\s+(?:a\s+)?(?:personal\s+)?interview/i,
  },
  { code: 'cbt', re: /\bcbt\b/i },
  { code: 'cbt', re: /computer\s*[- ]?based\s+test/i },
  { code: 'cbt', re: /online\s+test/i },
  { code: 'cbt', re: /online\s+examination/i },
  { code: 'written_multi_stage', re: /written\s+test/i },
  { code: 'written_multi_stage', re: /written\s+examination/i },
  { code: 'written_multi_stage', re: /competitive\s+exam/i },
  { code: 'written_multi_stage', re: /tier[-\s]?(i|ii|iii|1|2|3)\b/i },
  { code: 'written_multi_stage', re: /preliminary\s+exam/i },
  { code: 'written_multi_stage', re: /mains\s+examination/i },
  { code: 'written_multi_stage', re: /departmental\s+competitive/i },
  { code: 'written_multi_stage', re: /\bgate\b/i },
  { code: 'written_multi_stage', re: /\bnet\b.*exam/i },
  { code: 'written_multi_stage', re: /\bjrf\b/i },
  { code: 'written_multi_stage', re: /\bupsc\b/i },
  { code: 'written_multi_stage', re: /\bssc\b/i },
  { code: 'written_multi_stage', re: /\bibps\b/i },
  { code: 'written_multi_stage', re: /rrb\s+exam/i },
  { code: 'physical', re: /\bpet\b/i },
  { code: 'physical', re: /\bpst\b/i },
  { code: 'physical', re: /physical\s+standard/i },
  { code: 'physical', re: /physical\s+endurance/i },
];

const EXCLUDE_PATTERNS = EXAM_PATTERNS.map((p) => p.re);

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
  for (const { code, re } of EXAM_PATTERNS) {
    if (re.test(blob)) {
      return { hasExam: true, selectionProcess: code, reason: 'exam_keyword' };
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
  if (typeof job.hasExam !== 'boolean') errors.push('hasExam must be boolean');
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
