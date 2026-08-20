/**
 * Desk tracker: kinds, statuses, days-left, next-step copy.
 * Never invent dates from free text.
 */

const KINDS = ['series', 'opportunity', 'custom'];
const STATUSES = ['watching', 'applied', 'admit_ready', 'appeared', 'result_in', 'done'];

const STATUS_LABELS = {
  watching: 'Watching',
  applied: 'Applied',
  admit_ready: 'Admit ready',
  appeared: 'Appeared',
  result_in: 'Result in',
  done: 'Done',
};

const KIND_LABELS = {
  series: 'Calendar',
  opportunity: 'Job applied',
  custom: 'Custom',
};

function parseIsoDate(value) {
  if (value == null || value === '') return null;
  const s = String(value).trim();
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(s);
  if (!m) return null;
  return new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3])));
}

function formatIsoDate(value) {
  const d = value instanceof Date ? value : parseIsoDate(value);
  if (!d) return null;
  const y = d.getUTCFullYear();
  const mo = String(d.getUTCMonth() + 1).padStart(2, '0');
  const day = String(d.getUTCDate()).padStart(2, '0');
  return `${y}-${mo}-${day}`;
}

function startOfUtcDay(date) {
  return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate());
}

/**
 * Whole UTC days from today to anchor. Negative = days ago. Null if no date.
 */
function daysLeft(anchor, today) {
  const end = parseIsoDate(anchor);
  if (!end) return null;
  const now = today instanceof Date ? today : new Date();
  const diff = startOfUtcDay(end) - startOfUtcDay(now);
  return Math.round(diff / (1000 * 60 * 60 * 24));
}

function pickAnchor(item) {
  return item.examDate || item.lastDate || null;
}

function daysLeftLabel(days) {
  if (days == null) return { number: '—', caption: 'Add exam date' };
  if (days === 0) return { number: '0', caption: 'today' };
  if (days > 0) return { number: String(days), caption: days === 1 ? 'day left' : 'days left' };
  const ago = Math.abs(days);
  return { number: String(ago), caption: ago === 1 ? 'day ago' : 'days ago' };
}

function nextStep(item) {
  const days = item.daysLeft;
  const hasAdmit = Boolean(item.hasAdmit);
  const hasResult = Boolean(item.hasResult);
  const status = item.status || 'watching';
  if (status === 'watching' && item.applyOpen) {
    return 'Apply on the official site, then mark Applied.';
  }
  if (status === 'watching') {
    return 'Prepare — exam date or notification is not open yet.';
  }
  if (status === 'applied' && !hasAdmit) {
    return 'Upload the admit card when the board releases it.';
  }
  if ((status === 'admit_ready' || hasAdmit) && days != null && days > 0) {
    return `Exam in ${days} day${days === 1 ? '' : 's'} — follow your plan.`;
  }
  if (status === 'admit_ready' && (days == null || days <= 0)) {
    return 'Admit card is on file. Sit the paper, then mark Appeared.';
  }
  if (status === 'appeared' && !hasResult) {
    return 'Upload the result when it is declared.';
  }
  if (status === 'result_in' || status === 'done') {
    return 'Review your score. Keep documents private on this host.';
  }
  return 'Open the desk to update status or dates.';
}

function decorateItem(raw, extras = {}) {
  const item = { ...raw, ...extras };
  const anchor = pickAnchor(item);
  const days = daysLeft(anchor);
  const label = daysLeftLabel(days);
  const decorated = {
    ...item,
    anchorDate: formatIsoDate(anchor),
    daysLeft: days,
    daysNumber: label.number,
    daysCaption: label.caption,
    kindLabel: KIND_LABELS[item.kind] || item.kind,
    statusLabel: STATUS_LABELS[item.status] || item.status,
  };
  decorated.nextStep = nextStep(decorated);
  return decorated;
}

module.exports = {
  KINDS,
  STATUSES,
  STATUS_LABELS,
  KIND_LABELS,
  parseIsoDate,
  formatIsoDate,
  daysLeft,
  daysLeftLabel,
  pickAnchor,
  nextStep,
  decorateItem,
};

exports.KINDS = KINDS;
exports.STATUSES = STATUSES;
exports.STATUS_LABELS = STATUS_LABELS;
exports.KIND_LABELS = KIND_LABELS;
exports.parseIsoDate = parseIsoDate;
exports.formatIsoDate = formatIsoDate;
exports.daysLeft = daysLeft;
exports.daysLeftLabel = daysLeftLabel;
exports.pickAnchor = pickAnchor;
exports.nextStep = nextStep;
exports.decorateItem = decorateItem;
