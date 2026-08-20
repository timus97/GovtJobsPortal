/**
 * PR15 unofficial timed mocks. Run: node tests/pr15-mocks.js
 */
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'pr15-mocks-'));
process.env.STUDENT_DATA_DIR = path.join(tmp, 'students');
process.env.STUDENT_FILES_DIR = path.join(tmp, 'files');
process.env.SESSION_SECRET = 'pr15-test-session-secret';
process.env.FEATURE_STUDENT = 'on';
delete process.env.NODE_ENV;

const { publicBank, scoreAttempt } = require('../shared/mockScore');
const studentStore = require('../server/src/services/studentStore');
const { app } = require('../server/src/index');

function fail(message, err) {
  console.error(`FAIL: ${message}`);
  if (err) console.error(err.stack || err.message || err);
  try {
    fs.rmSync(tmp, { recursive: true, force: true });
  } catch {
    /* ignore */
  }
  process.exit(1);
}

function cookieHeader(res) {
  const list = typeof res.headers.getSetCookie === 'function' ? res.headers.getSetCookie() : [];
  const raw = list.length ? list : [res.headers.get('set-cookie')].filter(Boolean);
  return raw.map((c) => String(c).split(';')[0]).join('; ');
}

function assertNoAnswers(questions) {
  assert.ok(Array.isArray(questions) && questions.length > 0);
  for (const q of questions) {
    assert.ok(!Object.prototype.hasOwnProperty.call(q, 'answerIndex'), 'public bank leaked answerIndex');
    assert.ok(!Object.prototype.hasOwnProperty.call(q, 'explain'), 'public bank leaked explain');
  }
}

async function main() {
  const tiny = {
    seriesId: 'tiny',
    unofficial: true,
    durationMin: 5,
    questions: [
      { id: 'q1', stem: '1+1', choices: ['1', '2', '3', '4'], answerIndex: 1, explain: 'two' },
      { id: 'q2', stem: '2+2', choices: ['1', '2', '3', '4'], answerIndex: 3, explain: 'four' },
    ],
  };
  const pub = publicBank(tiny);
  assertNoAnswers(pub.questions);
  assert.strictEqual(pub.unofficial, true);

  const perfect = scoreAttempt(tiny, { q1: 1, q2: 3 });
  assert.strictEqual(perfect.score, 2);
  assert.strictEqual(perfect.total, 2);
  assert.deepStrictEqual(perfect.correctIds, ['q1', 'q2']);
  assert.strictEqual(perfect.review.every((r) => r.ok), true);

  const none = scoreAttempt(tiny, { q1: 0, q2: 0 });
  assert.strictEqual(none.score, 0);
  assert.strictEqual(none.total, 2);
  assert.deepStrictEqual(none.correctIds, []);
  assert.strictEqual(
    scoreAttempt(tiny, {}).score,
    0,
    'unanswered counts as wrong'
  );

  studentStore.register({
    email: 'owner@example.com',
    password: 'long-enough-password',
  });
  studentStore.register({
    email: 'other@example.com',
    password: 'long-enough-password',
  });

  const server = await new Promise((resolve) => {
    const s = app.listen(0, '127.0.0.1', () => resolve(s));
  });
  const port = server.address().port;
  const base = `http://127.0.0.1:${port}`;

  try {
    const publicRes = await fetch(`${base}/api/coaching/mocks/ssc-cgl`);
    assert.strictEqual(publicRes.status, 200);
    const publicBody = await publicRes.json();
    const bank = publicBody.bank || publicBody;
    assert.strictEqual(bank.seriesId, 'ssc-cgl');
    assert.strictEqual(bank.unofficial, true);
    assertNoAnswers(bank.questions);

    const missing = await fetch(`${base}/api/coaching/mocks/no-such-series`);
    assert.strictEqual(missing.status, 404);

    const unauthStart = await fetch(`${base}/api/me/mocks/ssc-cgl/attempts`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    });
    assert.strictEqual(unauthStart.status, 401);

    const login = await fetch(`${base}/api/account/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'owner@example.com', password: 'long-enough-password' }),
    });
    assert.strictEqual(login.status, 200);
    const cookie = cookieHeader(login);
    assert.match(cookie, /student_session=/);

    const started = await fetch(`${base}/api/me/mocks/ssc-cgl/attempts`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', cookie },
      body: JSON.stringify({}),
    });
    assert.strictEqual(started.status, 201, JSON.stringify(await started.clone().json().catch(() => ({}))));
    const startedBody = await started.json();
    assert.ok(startedBody.attempt && startedBody.attempt.id);
    assert.strictEqual(startedBody.attempt.submittedAt, null);
    assert.ok(!startedBody.attempt.answers);
    assertNoAnswers(startedBody.bank.questions);
    assert.ok(Number(startedBody.durationMin) > 0);
    const attemptId = startedBody.attempt.id;

    const reuse = await fetch(`${base}/api/me/mocks/ssc-cgl/attempts`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', cookie },
      body: JSON.stringify({}),
    });
    assert.strictEqual(reuse.status, 201);
    const reuseBody = await reuse.json();
    assert.strictEqual(reuseBody.attempt.id, attemptId, 'open attempt must be reused');

    const openGet = await fetch(`${base}/api/me/mocks/attempts/${attemptId}`, { headers: { cookie } });
    assert.strictEqual(openGet.status, 200);
    const openBody = await openGet.json();
    assert.ok(!openBody.attempt.answers);
    assert.ok(!openBody.review);

    const q1 = startedBody.bank.questions[0].id;
    const q2 = startedBody.bank.questions[1].id;
    const submitted = await fetch(`${base}/api/me/mocks/attempts/${attemptId}/submit`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', cookie },
      body: JSON.stringify({ answers: { [q1]: 0, [q2]: 0 } }),
    });
    assert.strictEqual(submitted.status, 200, JSON.stringify(await submitted.clone().json().catch(() => ({}))));
    const submittedBody = await submitted.json();
    assert.ok(submittedBody.attempt.submittedAt);
    assert.strictEqual(typeof submittedBody.attempt.score, 'number');
    assert.strictEqual(submittedBody.attempt.total, startedBody.bank.questions.length);
    assert.ok(Array.isArray(submittedBody.review));
    assert.strictEqual(submittedBody.review.length, startedBody.bank.questions.length);

    const stored = studentStore.load().mockAttempts.find((a) => a.id === attemptId);
    assert.ok(stored);
    assert.ok(stored.submittedAt);
    assert.strictEqual(stored.total, startedBody.bank.questions.length);
    assert.strictEqual(studentStore.listItems(stored.studentId).stats.mocksCompleted, 1);

    const again = await fetch(`${base}/api/me/mocks/attempts/${attemptId}/submit`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', cookie },
      body: JSON.stringify({ answers: { [q1]: 0 } }),
    });
    assert.strictEqual(again.status, 409);

    const after = await fetch(`${base}/api/me/mocks/attempts/${attemptId}`, { headers: { cookie } });
    assert.strictEqual(after.status, 200);
    const afterBody = await after.json();
    assert.strictEqual(afterBody.attempt.score, submittedBody.attempt.score);
    assert.ok(Array.isArray(afterBody.review));

    const unauthGet = await fetch(`${base}/api/me/mocks/attempts/${attemptId}`);
    assert.strictEqual(unauthGet.status, 401);

    const otherLogin = await fetch(`${base}/api/account/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'other@example.com', password: 'long-enough-password' }),
    });
    const otherCookie = cookieHeader(otherLogin);
    const steal = await fetch(`${base}/api/me/mocks/attempts/${attemptId}`, {
      headers: { cookie: otherCookie },
    });
    assert.strictEqual(steal.status, 404);
  } finally {
    await new Promise((resolve, reject) => server.close((err) => (err ? reject(err) : resolve())));
  }

  fs.rmSync(tmp, { recursive: true, force: true });
  console.log('pr15-mocks: all passed');
}

main().catch((err) => fail(err.message, err));
