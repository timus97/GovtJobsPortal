/**
 * Student password reset. Run: node tests/pr19-password-reset.js
 */
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'pr19-reset-'));
process.env.STUDENT_DATA_DIR = tmp;
process.env.STUDENT_RESET_PATH = path.join(tmp, 'reset-tokens.json');
process.env.SESSION_SECRET = 'pr19-test-session-secret';
process.env.FEATURE_STUDENT = 'on';
process.env.PUBLIC_SITE_URL = 'http://localhost:5173';
delete process.env.NODE_ENV;
process.env.SMTP_HOST = '';
process.env.RESEND_API_KEY = '';

const studentStore = require('../server/src/services/studentStore');
const passwordReset = require('../server/src/services/passwordReset');
const { router: accountRouter } = require('../server/src/routes/account');

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
    clearCookie() {},
    setHeader(name, value) {
      this.headers[name] = value;
    },
  };
}

function dispatch(method, url, { body, query } = {}) {
  return new Promise((resolve) => {
    const req = {
      method,
      url,
      path: url.split('?')[0],
      query: query || {},
      body: body || {},
      ip: '127.0.0.1',
      headers: {},
    };
    const res = mockRes();
    const finish = () => resolve(res);
    const origJson = res.json.bind(res);
    res.json = (payload) => {
      origJson(payload);
      finish();
      return res;
    };
    accountRouter(req, res, () => {
      if (res.body == null) resolve(res);
    });
  });
}

async function main() {
  const created = studentStore.register({
    email: 'reset.me@example.com',
    password: 'old-password-1',
  });
  assert.ok(created.id);

  const unknown = await dispatch('POST', '/forgot-password', { body: { email: 'missing@example.com' } });
  assert.strictEqual(unknown.statusCode, 200);
  assert.ok(unknown.body.ok);
  assert.ok(!unknown.body.devResetUrl);

  const forgot = await dispatch('POST', '/forgot-password', { body: { email: 'reset.me@example.com' } });
  assert.strictEqual(forgot.statusCode, 200, JSON.stringify(forgot.body));
  assert.ok(forgot.body.devResetUrl, 'dev host should return the one-time link when SMTP is off');
  const token = new URL(forgot.body.devResetUrl).searchParams.get('token');
  assert.ok(token);

  const peek = await dispatch('GET', '/reset-password', { query: { token } });
  assert.strictEqual(peek.statusCode, 200);
  assert.ok(peek.body.ok);

  const badPeek = await dispatch('GET', '/reset-password', { query: { token: 'not-a-real-token' } });
  assert.strictEqual(badPeek.statusCode, 200);
  assert.strictEqual(badPeek.body.ok, false);

  const short = await dispatch('POST', '/reset-password', { body: { token, password: 'short' } });
  assert.strictEqual(short.statusCode, 400);

  const reset = await dispatch('POST', '/reset-password', { body: { token, password: 'new-password-9' } });
  assert.strictEqual(reset.statusCode, 200, JSON.stringify(reset.body));
  assert.strictEqual(reset.body.student.email, 'reset.me@example.com');
  assert.ok(reset.cookies.student_session);

  assert.strictEqual(studentStore.verifyPassword('reset.me@example.com', 'old-password-1'), null);
  assert.ok(studentStore.verifyPassword('reset.me@example.com', 'new-password-9'));

  const reuse = await dispatch('POST', '/reset-password', { body: { token, password: 'another-password' } });
  assert.strictEqual(reuse.statusCode, 400);

  const issued = await passwordReset.requestReset('reset.me@example.com');
  assert.ok(issued.created);
  const again = await passwordReset.requestReset('reset.me@example.com');
  assert.ok(again.created);
  const oldPeek = passwordReset.peekReset(issued.token);
  assert.strictEqual(oldPeek.ok, false, 'a new request should replace the previous token');

  console.log('PASS pr19-password-reset');
}

main()
  .catch((err) => fail(err.message, err))
  .finally(() => {
    try {
      fs.rmSync(tmp, { recursive: true, force: true });
    } catch {
      /* ignore */
    }
  });
