/**
 * PR07 ops auth smoke checks. Run: node tests/pr07-ops-auth.js
 * Does not listen on port 4000.
 */
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'pr07-ops-'));
const storeFile = path.join(tmpDir, 'operators.json');

process.env.OPERATOR_STORE_PATH = storeFile;
process.env.SESSION_SECRET = 'pr07-test-session-secret';
process.env.OPERATOR_PASSWORD = 'bootstrap-only-pass';
delete process.env.NODE_ENV;

const opsAuth = require('../server/src/services/opsAuth');
const operatorStore = require('../server/src/services/operatorStore');

function fail(message, err) {
  console.error(`FAIL: ${message}`);
  if (err) console.error(err.message || err);
  try {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  } catch {
    /* ignore */
  }
  process.exit(1);
}

function resetStore() {
  try {
    fs.unlinkSync(storeFile);
  } catch {
    /* missing is fine */
  }
}

function mockReq(token) {
  return {
    headers: token ? { cookie: `${opsAuth.COOKIE_NAME}=${token}` } : {},
  };
}

function mockRes() {
  return {
    statusCode: 200,
    body: null,
    cookies: {},
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
  };
}

try {
  const prevStore = process.env.OPERATOR_STORE_PATH;
  delete process.env.OPERATOR_STORE_PATH;
  assert.ok(
    operatorStore.storePath().replace(/\\/g, '/').endsWith('data/ops/operators.json'),
    'default operator store must be data/ops/operators.json'
  );
  process.env.OPERATOR_STORE_PATH = prevStore;

  const hashed = opsAuth.hash('correct-horse-battery');
  assert.ok(hashed.startsWith('scrypt$'), 'hash should use scrypt');
  assert.strictEqual(opsAuth.verify('correct-horse-battery', hashed), true, 'verify should accept the password');
  assert.strictEqual(opsAuth.verify('wrong-password', hashed), false, 'verify should reject a wrong password');
  const hashedAgain = opsAuth.hash('correct-horse-battery');
  assert.notStrictEqual(hashed, hashedAgain, 'hashes must be salted');

  const user = { id: 'op-1', username: 'admin', role: 'admin' };
  const token = opsAuth.signCookie(user);
  const session = opsAuth.readSession(mockReq(token));
  assert.ok(session, 'signed cookie should read back');
  assert.strictEqual(session.sub, 'admin');
  assert.strictEqual(session.role, 'admin');
  assert.ok(session.exp > session.iat, 'session should have a future exp');

  const tampered = `${token.slice(0, -2)}aa`;
  assert.strictEqual(opsAuth.readSession(mockReq(tampered)), null, 'tampered cookie must be rejected');
  assert.strictEqual(opsAuth.readSession(mockReq('')), null, 'missing cookie is no session');

  const expired = opsAuth.signCookie(user, { now: Date.now() - 13 * 60 * 60 * 1000 });
  assert.strictEqual(opsAuth.readSession(mockReq(expired)), null, 'idle TTL 12h: expired session rejected');

  const resOk = mockRes();
  let nextCalled = false;
  opsAuth.requireOps(mockReq(token), resOk, () => {
    nextCalled = true;
  });
  assert.strictEqual(nextCalled, true, 'requireOps should call next for a valid session');
  assert.ok(resOk.cookies[opsAuth.COOKIE_NAME], 'requireOps should mint a fresh idle cookie');

  const res401 = mockRes();
  opsAuth.requireOps(mockReq(null), res401, () => {
    throw new Error('requireOps must not next() when unauthenticated');
  });
  assert.strictEqual(res401.statusCode, 401);
  assert.strictEqual(res401.body.error, 'Unauthorized');

  resetStore();
  assert.strictEqual(operatorStore.count(), 0);
  const first = operatorStore.bootstrapIfEmpty();
  assert.strictEqual(first.created, true, 'bootstrap should create the first admin');
  assert.strictEqual(first.operator.username, 'admin');
  assert.strictEqual(first.operator.role, 'admin');
  assert.ok(!('passwordHash' in first.operator), 'bootstrap must not return the hash');
  assert.strictEqual(operatorStore.count(), 1);

  const second = operatorStore.bootstrapIfEmpty();
  assert.strictEqual(second.created, false);
  assert.strictEqual(second.reason, 'not-empty');
  assert.strictEqual(operatorStore.count(), 1, 'bootstrap must not add a second admin');

  const viaStore = operatorStore.verifyPassword('admin', process.env.OPERATOR_PASSWORD);
  assert.ok(viaStore, 'stored admin hash should match the bootstrap password');
  assert.strictEqual(operatorStore.verifyPassword('admin', 'not-the-bootstrap'), null);

  resetStore();
  const alice = operatorStore.create({
    username: 'alice',
    password: 'alice-secret-1',
    role: 'operator',
  });
  assert.strictEqual(alice.username, 'alice');
  assert.strictEqual(alice.role, 'operator');

  const skipped = operatorStore.bootstrapIfEmpty(process.env.OPERATOR_PASSWORD);
  assert.strictEqual(skipped.created, false, 'OPERATOR_PASSWORD must not bootstrap after a user exists');
  assert.strictEqual(operatorStore.verifyPassword('admin', process.env.OPERATOR_PASSWORD), null);
  assert.strictEqual(
    operatorStore.verifyPassword('alice', process.env.OPERATOR_PASSWORD),
    null,
    'env bootstrap password is not a login after the store is non-empty'
  );
  assert.ok(operatorStore.verifyPassword('alice', 'alice-secret-1'));
  assert.strictEqual(operatorStore.list().length, 1);
  assert.ok(!('passwordHash' in operatorStore.list()[0]));

  let duplicate = null;
  try {
    operatorStore.create({ username: 'Alice', password: 'another-secret' });
  } catch (err) {
    duplicate = err;
  }
  assert.ok(duplicate, 'duplicate username should throw');
  assert.strictEqual(duplicate.code, 'DUPLICATE');
} catch (err) {
  fail(err.message, err);
}

try {
  fs.rmSync(tmpDir, { recursive: true, force: true });
} catch {
  /* ignore */
}

console.log('pr07-ops-auth: all passed');
process.exit(0);
