/**
 * PR12 desk tracker. Run: node tests/pr12-tracker.js
 */
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'pr12-desk-'));
process.env.STUDENT_DATA_DIR = tmp;
process.env.SESSION_SECRET = 'pr12-test-secret';
process.env.FEATURE_STUDENT = 'on';

const desk = require('../shared/deskGuidance');
const studentStore = require('../server/src/services/studentStore');

function fail(message, err) {
  console.error(`FAIL: ${message}`);
  if (err) console.error(err.message || err);
  try {
    fs.rmSync(tmp, { recursive: true, force: true });
  } catch {
    /* ignore */
  }
  process.exit(1);
}

try {
  assert.strictEqual(desk.daysLeft('2026-08-22', new Date('2026-08-20T12:00:00Z')), 2);
  assert.strictEqual(desk.daysLeft('2026-08-20', new Date('2026-08-20T23:00:00Z')), 0);
  assert.strictEqual(desk.daysLeft('2026-08-19', new Date('2026-08-20T01:00:00Z')), -1);
  assert.strictEqual(desk.daysLeft(null, new Date()), null);
  assert.strictEqual(desk.daysLeftLabel(null).caption, 'Add exam date');
  assert.match(desk.daysLeftLabel(12).caption, /days left/);

  const student = studentStore.register({
    email: 'desk@example.com',
    password: 'long-enough-password',
  });

  try {
    studentStore.createItem(student.id, { kind: 'custom', title: 'No date' });
    assert.fail('custom without date');
  } catch (err) {
    assert.strictEqual(err.code, 'VALIDATION');
  }

  const custom = studentStore.createItem(student.id, {
    kind: 'custom',
    title: 'UPPSC RO / ARO 2026',
    board: 'UPPSC',
    examDate: '2026-12-01',
    officialUrl: 'https://uppsc.up.nic.in/',
  });
  assert.strictEqual(custom.kind, 'custom');
  assert.ok(custom.daysLeft == null || typeof custom.daysLeft === 'number');
  assert.ok(custom.nextStep);

  const series = studentStore.createItem(student.id, { kind: 'series', refId: 'ssc-cgl' });
  assert.strictEqual(series.kind, 'series');
  assert.strictEqual(series.title, 'Combined Graduate Level Examination');
  assert.strictEqual(series.board, 'SSC');
  assert.match(series.officialUrl, /^https:\/\//);

  const again = studentStore.createItem(student.id, {
    kind: 'series',
    refId: 'ssc-cgl',
    status: 'applied',
  });
  assert.strictEqual(again.id, series.id);
  assert.strictEqual(again.status, 'applied');
  assert.match(again.nextStep, /admit card/i);

  const jobs = JSON.parse(
    fs.readFileSync(path.join(__dirname, '..', 'data', 'processed', 'jobs.json'), 'utf8')
  );
  const job = jobs.find((j) => j.id && j.title && j.officialUrl);
  assert.ok(job, 'need a published job');
  const opp = studentStore.createItem(student.id, {
    kind: 'opportunity',
    refId: job.id,
    status: 'applied',
  });
  assert.strictEqual(opp.kind, 'opportunity');
  assert.strictEqual(opp.title, job.title);

  const listed = studentStore.listItems(student.id);
  assert.ok(listed.items.length >= 3);
  assert.ok(listed.stats.admitPending >= 1);

  const patched = studentStore.updateItem(student.id, custom.id, { status: 'admit_ready' });
  assert.strictEqual(patched.status, 'admit_ready');

  try {
    studentStore.updateItem(student.id, custom.id, { officialUrl: 'http://insecure.example' });
    assert.fail('http url');
  } catch (err) {
    assert.strictEqual(err.code, 'VALIDATION');
  }

  assert.strictEqual(studentStore.deleteItem(student.id, custom.id), true);
  assert.strictEqual(studentStore.getItem(student.id, custom.id), null);

  try {
    studentStore.createItem(student.id, { kind: 'series', refId: 'does-not-exist' });
    assert.fail('missing series');
  } catch (err) {
    assert.strictEqual(err.code, 'VALIDATION');
  }

  const watching = desk.decorateItem({
    status: 'watching',
    applyOpen: true,
    examDate: null,
    lastDate: null,
  });
  assert.match(watching.nextStep, /Apply on the official site/);

  fs.rmSync(tmp, { recursive: true, force: true });
  console.log('pr12-tracker: all passed');
} catch (err) {
  fail(err.message, err);
}
