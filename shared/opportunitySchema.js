/**
 * Minimal Opportunity shape (Stage 0 stub).
 * selectionProcess stays a primary string; selectionProcesses[] is optional.
 */

const { SELECTION_PROCESSES, STATUSES } = require('./jobSchema');

function isValidOpportunity(o) {
  const errors = [];
  if (!o || typeof o !== 'object') return ['not an object'];
  if (!o.id) errors.push('id required');
  if (!o.title) errors.push('title required');
  if (!o.organization) errors.push('organization required');
  if (!o.officialUrl || !/^https:\/\//i.test(o.officialUrl)) errors.push('officialUrl must be https');
  if (typeof o.hasExam !== 'boolean') errors.push('hasExam must be boolean');
  if (!SELECTION_PROCESSES.includes(o.selectionProcess)) errors.push('invalid selectionProcess');
  if (o.selectionProcesses !== undefined) {
    if (
      !Array.isArray(o.selectionProcesses) ||
      o.selectionProcesses.some((code) => !SELECTION_PROCESSES.includes(code))
    ) {
      errors.push('invalid selectionProcesses');
    }
  }
  if (!STATUSES.includes(o.status)) errors.push('invalid status');
  return errors;
}

module.exports = {
  isValidOpportunity,
};
