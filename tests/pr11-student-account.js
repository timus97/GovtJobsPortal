/**
 * PR11 student accounts. Run: node tests/pr11-student-account.js
 */
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'pr11-student-'));
process.env.STUDENT_DATA_DIR = tmp;
process.env.SESSION_SECRET = 'pr11-test-session-secret';
process.env.FEATURE_STUDENT = 'on';
delete process.env.NODE_ENV;

const studentStore = require('../server/src/services/studentStore');
const studentAuth = require('../server/src/services/studentAuth');
const opsAuth = require('../server/src/services/opsAuth');
const { router: accountRouter, meRouter } = require('../server/src/routes/account');

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

function mockRes() {
  return {
    statusCode: 200,
    body: null,
    cookies: {},
    headers: {},
    status(code) {
      this.statusCode = code;
      return this;
    },
    json(payload) {
      this.body = payload;
      return this;
    },
    cookie(name, value) {
      this.cookies[name] = value;
    },
    clearCookie(name) {
      delete this.cookies[name];
    },
    setHeader(name, value) {
      this.headers[name] = value;
    },
  };
}

function dispatch(appRouter, method, url, { body, cookie } = {}) {
  return new Promise((resolve) => {
    const req = {
      method,
      url,
      path: url.split('?')[0],
      body: body || {},
      ip: '127.0.0.1',
      headers: cookie ? { cookie } : {},
    };
    const res = mockRes();
    const origJson = res.json.bind(res);
    const origStatus = res.status.bind(res);
    res.json = (payload) => {
      origJson(payload);
      resolve(res);
      return res;
    };
    res.status = (code) => {
      origStatus(code);
      return res;
    };
    appRouter(req, res, () => {
      if (res.body == null) {
        res.statusCode = 404;
        res.body = { error: 'not found' };
      }
      resolve(res);
    });
  });
}

async function main() {
  const created = studentStore.register({
    email: 'Ada@Example.com',
    password: 'long-enough-password',
  });
  assert.ok(created.id);
  assert.strictEqual(created.email, 'Ada@Example.com');
  assert.ok(!created.passwordHash);

  try {
    studentStore.register({ email: 'ada@example.com', password: 'long-enough-password' });
    assert.fail('duplicate email');
  } catch (err) {
    assert.strictEqual(err.code, 'DUPLICATE');
  }

  try {
    studentStore.register({ email: 'bad', password: 'long-enough-password' });
    assert.fail('bad email');
  } catch (err) {
    assert.strictEqual(err.code, 'VALIDATION');
  }

  try {
    studentStore.register({ email: 'ok@example.com', password: 'short' });
    assert.fail('short password');
  } catch (err) {
    assert.strictEqual(err.code, 'VALIDATION');
  }

  const ok = studentStore.verifyPassword('ada@example.com', 'long-enough-password');
  assert.ok(ok);
  assert.strictEqual(studentStore.verifyPassword('ada@example.com', 'wrong-password'), null);

  const token = studentAuth.signCookie(ok);
  const session = studentAuth.readSession({
    headers: { cookie: `${studentAuth.COOKIE_NAME}=${token}` },
  });
  assert.ok(session);
  assert.strictEqual(session.aud, 'student');
  assert.strictEqual(session.uid, ok.id);

  const opsToken = opsAuth.signCookie({ id: 'op-1', username: 'admin', role: 'admin' });
  assert.strictEqual(
    studentAuth.readSession({ headers: { cookie: `${opsAuth.COOKIE_NAME}=${opsToken}` } }),
    null,
    'ops cookie must not authenticate a student'
  );

  const saved = studentStore.saveProfile(ok.id, {
    dob: '1998-06-15',
    reservationCategory: 'UR',
    highestEducation: 'graduate',
    birthState: 'MH',
    domicileStates: ['MH'],
  });
  assert.strictEqual(saved.reservationCategory, 'UR');
  assert.strictEqual(studentStore.getProfile(ok.id).dob, '1998-06-15');

  const loginRes = await dispatch(accountRouter, 'POST', '/login', {
    body: { email: 'ada@example.com', password: 'long-enough-password' },
  });
  assert.strictEqual(loginRes.statusCode, 200);
  assert.ok(loginRes.cookies[studentAuth.COOKIE_NAME]);

  const cookie = `${studentAuth.COOKIE_NAME}=${loginRes.cookies[studentAuth.COOKIE_NAME]}`;
  const meRes = await dispatch(accountRouter, 'GET', '/me', { cookie });
  assert.strictEqual(meRes.statusCode, 200);
  assert.strictEqual(meRes.body.student.email, 'Ada@Example.com');

  const profRes = await dispatch(meRouter, 'GET', '/profile', { cookie });
  assert.strictEqual(profRes.statusCode, 200);
  assert.strictEqual(profRes.body.profile.reservationCategory, 'UR');

  const unauth = await dispatch(meRouter, 'GET', '/profile', {});
  assert.strictEqual(unauth.statusCode, 401);

  process.env.FEATURE_STUDENT = 'off';
  const off = await dispatch(accountRouter, 'POST', '/login', {
    body: { email: 'ada@example.com', password: 'long-enough-password' },
  });
  assert.strictEqual(off.statusCode, 404);
  process.env.FEATURE_STUDENT = 'on';

  const src = fs.readFileSync(path.join(__dirname, '..', 'server', 'src', 'index.js'), 'utf8');
  assert.ok(/\/api\/account/.test(src));
  assert.ok(/\/api\/me/.test(src));

  fs.rmSync(tmp, { recursive: true, force: true });
  console.log('pr11-student-account: all passed');
}

main().catch((err) => fail(err.message, err));
