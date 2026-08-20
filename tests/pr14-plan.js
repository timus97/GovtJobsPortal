/**
 * PR14 unofficial syllabus + even-split plan. Run: node tests/pr14-plan.js
 */
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'pr14-plan-'));
process.env.STUDENT_DATA_DIR = path.join(tmp, 'students');
process.env.STUDENT_FILES_DIR = path.join(tmp, 'files');
process.env.SESSION_SECRET = 'pr14-test-session-secret';
process.env.FEATURE_STUDENT = 'on';
delete process.env.NODE_ENV;

const desk = require('../shared/deskGuidance');
const { buildPlan, ADD_DATE_NOTE } = require('../shared/studyPlan');
const studentStore = require('../server/src/services/studentStore');
const planStore = require('../server/src/services/planStore');
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

function nextUtcDay(iso) {
  const d = desk.parseIsoDate(iso);
  d.setUTCDate(d.getUTCDate() + 1);
  return desk.formatIsoDate(d);
}

const EQUAL_TOPICS = [
  { id: 'a', title: 'A', weight: 1 },
  { id: 'b', title: 'B', weight: 1 },
  { id: 'c', title: 'C', weight: 1 },
  { id: 'd', title: 'D', weight: 1 },
];

async function main() {
  const today = new Date(Date.UTC(2026, 0, 1, 12, 0, 0));
  const exam = '2026-01-08';
  assert.strictEqual(desk.daysLeft(exam, today), 7);

  const split = buildPlan(EQUAL_TOPICS, exam, today);
  assert.strictEqual(split.unofficial, true);
  assert.strictEqual(split.days, 8);
  assert.strictEqual(split.topics.length, 4);
  for (const topic of split.topics) {
    assert.strictEqual(topic.days, 2);
    assert.ok(topic.startDate);
    assert.ok(topic.endDate);
  }
  assert.strictEqual(split.topics[0].startDate, '2026-01-01');
  assert.strictEqual(split.topics[0].endDate, '2026-01-02');
  assert.strictEqual(split.topics[1].startDate, '2026-01-03');
  assert.strictEqual(split.topics[1].endDate, '2026-01-04');
  assert.strictEqual(split.topics[2].startDate, '2026-01-05');
  assert.strictEqual(split.topics[2].endDate, '2026-01-06');
  assert.strictEqual(split.topics[3].startDate, '2026-01-07');
  assert.strictEqual(split.topics[3].endDate, exam);
  for (let i = 1; i < split.topics.length; i += 1) {
    assert.strictEqual(split.topics[i].startDate, nextUtcDay(split.topics[i - 1].endDate));
  }

  const noDate = buildPlan(EQUAL_TOPICS, null, today);
  assert.strictEqual(noDate.note, ADD_DATE_NOTE);
  assert.match(noDate.note, /Add exam date to split the plan/);
  for (const topic of noDate.topics) {
    assert.strictEqual(topic.startDate, null);
    assert.strictEqual(topic.endDate, null);
    assert.strictEqual(topic.days, null);
  }

  const todayExam = buildPlan(EQUAL_TOPICS, '2026-01-01', today);
  assert.strictEqual(todayExam.note, ADD_DATE_NOTE);
  assert.strictEqual(todayExam.topics[0].startDate, null);

  const missingPack = planStore.getSyllabusPack('ssc-chsl');
  assert.strictEqual(missingPack, null);
  assert.strictEqual(await planStore.getPlanForStudent('nobody', 'does-not-exist'), null);

  const pack = planStore.getSyllabusPack('ssc-cgl');
  assert.ok(pack);
  assert.strictEqual(pack.unofficial, true);
  assert.ok(pack.topics.length >= 6 && pack.topics.length <= 10);

  const student = studentStore.register({
    email: 'plan@example.com',
    password: 'long-enough-password',
  });
  const item = studentStore.createItem(student.id, {
    kind: 'series',
    refId: 'ssc-cgl',
    examDate: exam,
  });

  const first = pack.topics[0].id;
  const ticked = await planStore.setTopicDone(student.id, 'ssc-cgl', first, true);
  assert.ok(ticked.progress[first]);
  const stored = studentStore.load();
  assert.ok(
    stored.topicProgress.some(
      (r) => r.studentId === student.id && r.seriesId === 'ssc-cgl' && r.topicId === first && r.doneAt
    )
  );

  const unticked = await planStore.setTopicDone(student.id, 'ssc-cgl', first, false);
  assert.strictEqual(unticked.progress[first], undefined);
  const after = studentStore.load();
  assert.ok(
    !after.topicProgress.some(
      (r) => r.studentId === student.id && r.seriesId === 'ssc-cgl' && r.topicId === first
    )
  );

  try {
    await planStore.setTopicDone(student.id, 'ssc-cgl', 'not-a-real-topic', true);
    assert.fail('invented topic tick');
  } catch (err) {
    assert.strictEqual(err.code, 'NOT_FOUND');
  }

  const planned = await planStore.getPlanForStudent(student.id, 'ssc-cgl', item.id);
  assert.strictEqual(planned.unofficial, true);
  assert.deepStrictEqual(
    planned.plan.topics.map((t) => t.id),
    pack.topics.map((t) => t.id)
  );
  assert.ok(!planned.plan.topics.some((t) => t.id === 'invented'));

  const server = await new Promise((resolve) => {
    const s = app.listen(0, '127.0.0.1', () => resolve(s));
  });
  const port = server.address().port;
  const base = `http://127.0.0.1:${port}`;

  try {
    const publicPack = await fetch(`${base}/api/coaching/syllabus/ssc-cgl`);
    assert.strictEqual(publicPack.status, 200);
    const publicBody = await publicPack.json();
    assert.strictEqual(publicBody.unofficial, true);
    assert.deepStrictEqual(
      publicBody.topics.map((t) => t.id),
      pack.topics.map((t) => t.id)
    );

    const missingHttp = await fetch(`${base}/api/coaching/syllabus/ssc-chsl`);
    assert.strictEqual(missingHttp.status, 404);

    const login = await fetch(`${base}/api/account/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'plan@example.com', password: 'long-enough-password' }),
    });
    assert.strictEqual(login.status, 200);
    const cookie = cookieHeader(login);
    assert.match(cookie, /student_session=/);

    const unauth = await fetch(`${base}/api/me/plan/ssc-cgl`);
    assert.strictEqual(unauth.status, 401);

    const missingPlan = await fetch(`${base}/api/me/plan/ssc-chsl`, { headers: { cookie } });
    assert.strictEqual(missingPlan.status, 404);

    const got = await fetch(`${base}/api/me/plan/ssc-cgl?itemId=${item.id}`, { headers: { cookie } });
    assert.strictEqual(got.status, 200);
    const gotBody = await got.json();
    assert.strictEqual(gotBody.unofficial, true);
    assert.strictEqual(gotBody.seriesId, 'ssc-cgl');
    assert.deepStrictEqual(
      gotBody.plan.topics.map((t) => t.id),
      pack.topics.map((t) => t.id)
    );
    assert.ok(!gotBody.plan.topics.some((t) => !pack.topics.some((p) => p.id === t.id)));

    const put = await fetch(`${base}/api/me/plan/ssc-cgl/topics/${first}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', cookie },
      body: JSON.stringify({ done: true }),
    });
    assert.strictEqual(put.status, 200);
    const putBody = await put.json();
    assert.ok(putBody.progress[first]);

    const again = await fetch(`${base}/api/me/plan/ssc-cgl`, { headers: { cookie } });
    const againBody = await again.json();
    assert.ok(againBody.progress[first]);

    const clear = await fetch(`${base}/api/me/plan/ssc-cgl/topics/${first}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', cookie },
      body: JSON.stringify({ done: false }),
    });
    assert.strictEqual(clear.status, 200);
    const clearBody = await clear.json();
    assert.strictEqual(clearBody.progress[first], undefined);

    const fakeTopic = await fetch(`${base}/api/me/plan/ssc-cgl/topics/invented`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', cookie },
      body: JSON.stringify({ done: true }),
    });
    assert.strictEqual(fakeTopic.status, 404);
  } finally {
    await new Promise((resolve, reject) => server.close((err) => (err ? reject(err) : resolve())));
  }

  fs.rmSync(tmp, { recursive: true, force: true });
  console.log('pr14-plan: all passed');
}

main().catch((err) => fail(err.message, err));
