/**
 * Minimal ExamSeries shape (Stage 0 stub).
 * Optional: cycle, expected_notify, expected_apply, expected_exam.
 */

function isValidExamSeries(s) {
  const errors = [];
  if (!s || typeof s !== 'object') return ['not an object'];
  if (!s.id) errors.push('id required');
  if (!s.board) errors.push('board required');
  if (!s.name) errors.push('name required');
  if (!s.officialUrl || !/^https:\/\//i.test(s.officialUrl)) errors.push('officialUrl must be https');
  return errors;
}

module.exports = {
  isValidExamSeries,
};
