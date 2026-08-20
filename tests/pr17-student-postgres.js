/**
 * PR17 student Postgres backend. Needs Docker student-db.
 * Run: node tests/pr17-student-postgres.js
 */
process.env.STUDENT_STORE = 'postgres';
process.env.STUDENT_DATABASE_URL =
  process.env.STUDENT_DATABASE_URL ||
  'postgres://govtjobs:govtjobs@127.0.0.1:5432/govtjobs_students';
process.env.FEATURE_STUDENT = 'on';
process.env.SESSION_SECRET = 'pr17-pg-secret';
delete process.env.NODE_ENV;

const assert = require('assert');
const crypto = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');

const tmpFiles = fs.mkdtempSync(path.join(os.tmpdir(), 'pr17-files-'));
process.env.STUDENT_FILES_DIR = tmpFiles;

const studentStore = require('../server/src/services/studentStore');
const planStore = require('../server/src/services/planStore');
const mockAttempts = require('../server/src/services/mockAttempts');

function fail(message, err) {
  console.error(`FAIL: ${message}`);
  if (err) console.error(err.stack || err.message || err);
  try {
    fs.rmSync(tmpFiles, { recursive: true, force: true });
  } catch {
    /* ignore */
  }
  process.exit(1);
}

async function main() {
  assert.strictEqual(studentStore.BACKEND, 'postgres');
  try {
    await studentStore.ready();
  } catch (err) {
    console.error('Postgres is not reachable. Start it with: npm run db:up');
    throw err;
  }

  const suffix = crypto.randomUUID().slice(0, 8);
  const email = `pg-${suffix}@example.com`;
  const student = await studentStore.register({
    email,
    password: 'long-enough-password',
  });
  assert.ok(student.id);

  try {
    await studentStore.register({ email, password: 'long-enough-password' });
    assert.fail('duplicate');
  } catch (err) {
    assert.strictEqual(err.code, 'DUPLICATE');
  }

  const ok = await studentStore.verifyPassword(email, 'long-enough-password');
  assert.ok(ok);
  assert.strictEqual(await studentStore.verifyPassword(email, 'wrong-password'), null);

  const saved = await studentStore.saveProfile(student.id, {
    dob: '1998-06-15',
    reservationCategory: 'UR',
    highestEducation: 'graduate',
  });
  assert.strictEqual(saved.reservationCategory, 'UR');
  assert.strictEqual((await studentStore.getProfile(student.id)).dob, '1998-06-15');

  const custom = await studentStore.createItem(student.id, {
    kind: 'custom',
    title: `PG custom ${suffix}`,
    examDate: '2026-12-01',
  });
  assert.strictEqual(custom.kind, 'custom');

  const series = await studentStore.createItem(student.id, { kind: 'series', refId: 'ssc-cgl' });
  assert.strictEqual(series.title, 'Combined Graduate Level Examination');

  const listed = await studentStore.listItems(student.id);
  assert.ok(listed.items.length >= 2);

  const MIN_PDF = Buffer.from('%PDF-1.4\n%%EOF\n');
  const uploaded = await studentStore.saveFile(student.id, custom.id, 'admit', {
    buffer: MIN_PDF,
    originalName: 'admit.pdf',
  });
  assert.strictEqual(uploaded.item.hasAdmit, true);
  const down = await studentStore.readFileForDownload(student.id, custom.id, 'admit');
  assert.ok(down.buffer.equals(MIN_PDF));

  const firstTopic = planStore.getSyllabusPack('ssc-cgl').topics[0].id;
  const ticked = await planStore.setTopicDone(student.id, 'ssc-cgl', firstTopic, true);
  assert.ok(ticked.progress[firstTopic]);

  const started = await mockAttempts.startAttempt(student.id, 'ssc-cgl', { itemId: series.id });
  assert.ok(started.attempt.id);
  const reused = await mockAttempts.startAttempt(student.id, 'ssc-cgl', { itemId: series.id });
  assert.strictEqual(reused.attempt.id, started.attempt.id);
  const submitted = await mockAttempts.submitAttempt(student.id, started.attempt.id, {});
  assert.ok(submitted.attempt.submittedAt);
  const after = await studentStore.listItems(student.id);
  assert.ok(after.stats.mocksCompleted >= 1);

  await studentStore.deleteItem(student.id, custom.id);
  assert.strictEqual(await studentStore.getItem(student.id, custom.id), null);

  fs.rmSync(tmpFiles, { recursive: true, force: true });
  console.log('pr17-student-postgres: all passed');
}

main().catch((err) => fail(err.message, err));
