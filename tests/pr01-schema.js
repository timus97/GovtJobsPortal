/**
 * PR01 schema smoke checks. Run: node tests/pr01-schema.js
 */
const assert = require('assert');
const path = require('path');
const { classifySelectionText, isValidJob, SELECTION_PROCESSES } = require(path.join(
  __dirname,
  '..',
  'shared',
  'jobSchema'
));
const examFixture = require('./fixtures/exam-job.json');

function fail(message, err) {
  console.error(`FAIL: ${message}`);
  if (err) console.error(err.message || err);
  process.exit(1);
}

try {
  const examClassified = classifySelectionText('SSC CGL computer based test');
  assert.strictEqual(examClassified.hasExam, true, 'exam text should set hasExam true');
  assert.ok(
    examClassified.selectionProcess === 'cbt' ||
      examClassified.selectionProcess === 'written_multi_stage',
    `expected cbt or written_multi_stage, got ${examClassified.selectionProcess}`
  );

  const walkIn = classifySelectionText('walk-in interview only');
  assert.strictEqual(walkIn.hasExam, false, 'walk-in should set hasExam false');
  assert.ok(
    walkIn.selectionProcess === 'walk_in' || walkIn.selectionProcess === 'interview_only',
    `expected walk_in or interview_only, got ${walkIn.selectionProcess}`
  );

  const publishableExam = {
    ...examFixture,
    id: examFixture.id || 'pr01-exam-fixture',
    status: examFixture.status || 'open',
    sourceName: examFixture.sourceName || 'Manual curator seed',
    sourceUrl: examFixture.sourceUrl || examFixture.officialUrl,
  };
  const examErrors = isValidJob(publishableExam);
  assert.strictEqual(
    examErrors.length,
    0,
    `hasExam:true job should be valid, got: ${examErrors.join('; ')}`
  );
  assert.strictEqual(publishableExam.hasExam, true);
  assert.strictEqual(publishableExam.selectionProcess, 'written_multi_stage');

  const missingTitle = { ...publishableExam, title: '' };
  const titleErrors = isValidJob(missingTitle);
  assert.ok(titleErrors.length > 0, 'missing title should fail isValidJob');
  assert.ok(
    titleErrors.some((e) => /title/i.test(e)),
    `expected a title error, got: ${titleErrors.join('; ')}`
  );

  assert.ok(SELECTION_PROCESSES.includes('cbt'), 'SELECTION_PROCESSES must include cbt');
} catch (err) {
  fail(err.message, err);
}

console.log('pr01-schema: all passed');
process.exit(0);
