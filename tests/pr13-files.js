/**
 * PR13 private admit/result files. Run: node tests/pr13-files.js
 */
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'pr13-files-'));
process.env.STUDENT_DATA_DIR = path.join(tmp, 'students');
process.env.STUDENT_FILES_DIR = path.join(tmp, 'files');
process.env.SESSION_SECRET = 'pr13-test-session-secret';
process.env.FEATURE_STUDENT = 'on';
delete process.env.NODE_ENV;

const studentStore = require('../server/src/services/studentStore');
const multipart = require('../server/src/services/multipart');
const { app } = require('../server/src/index');

const MIN_PDF = Buffer.from('%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n');
const MIN_JPEG = Buffer.from([0xff, 0xd8, 0xff, 0xd9, 0x00, 0x01]);
const MIN_PNG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x01]);
const HTML = Buffer.from('<!DOCTYPE html><html><body>nope</body></html>');

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

function multipartBody(filename, buffer, mime) {
  const boundary = '----pr13boundary';
  const body = Buffer.concat([
    Buffer.from(
      `--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="${filename}"\r\nContent-Type: ${mime}\r\n\r\n`
    ),
    buffer,
    Buffer.from(`\r\n--${boundary}--\r\n`),
  ]);
  return { boundary, body };
}

async function main() {
  assert.strictEqual(studentStore.detectMime(MIN_PDF), 'application/pdf');
  assert.strictEqual(studentStore.detectMime(MIN_JPEG), 'image/jpeg');
  assert.strictEqual(studentStore.detectMime(MIN_PNG), 'image/png');
  assert.strictEqual(studentStore.detectMime(HTML), null);

  const parsed = multipart.extractFilePart(
    multipartBody('hall-ticket.pdf', MIN_PDF, 'application/pdf').body,
    '----pr13boundary',
    'file'
  );
  assert.strictEqual(parsed.originalName, 'hall-ticket.pdf');
  assert.ok(parsed.buffer.equals(MIN_PDF));

  const owner = studentStore.register({
    email: 'owner@example.com',
    password: 'long-enough-password',
  });
  const other = studentStore.register({
    email: 'other@example.com',
    password: 'long-enough-password',
  });
  const item = studentStore.createItem(owner.id, {
    kind: 'custom',
    title: 'SSC CGL 2026',
    examDate: '2026-12-01',
  });

  try {
    studentStore.saveFile(owner.id, item.id, 'admit', { buffer: HTML, originalName: 'x.html' });
    assert.fail('html upload');
  } catch (err) {
    assert.strictEqual(err.code, 'VALIDATION');
  }

  const huge = Buffer.alloc(studentStore.MAX_FILE_BYTES + 1, 0x25);
  huge[1] = 0x50;
  huge[2] = 0x44;
  huge[3] = 0x46;
  try {
    studentStore.saveFile(owner.id, item.id, 'admit', { buffer: huge, originalName: 'big.pdf' });
    assert.fail('oversized');
  } catch (err) {
    assert.strictEqual(err.code, 'TOO_LARGE');
  }

  const saved = studentStore.saveFile(owner.id, item.id, 'admit', {
    buffer: MIN_PDF,
    originalName: '..\\..\\etc\\hall-ticket.pdf',
  });
  assert.strictEqual(saved.item.hasAdmit, true);
  assert.strictEqual(saved.file.originalName, 'hall-ticket.pdf');
  assert.ok(!saved.file.storedPath);
  assert.ok(fs.existsSync(path.join(process.env.STUDENT_FILES_DIR, owner.id, item.id, 'admit.pdf')));

  const otherSave = studentStore.saveFile(other.id, item.id, 'admit', {
    buffer: MIN_PDF,
    originalName: 'steal.pdf',
  });
  assert.strictEqual(otherSave, null);
  assert.strictEqual(studentStore.readFileForDownload(other.id, item.id, 'admit'), null);

  const replaced = studentStore.saveFile(owner.id, item.id, 'admit', {
    buffer: MIN_JPEG,
    originalName: 'admit.jpg',
  });
  assert.strictEqual(replaced.file.mime, 'image/jpeg');
  assert.strictEqual(fs.existsSync(path.join(process.env.STUDENT_FILES_DIR, owner.id, item.id, 'admit.pdf')), false);
  assert.ok(fs.existsSync(path.join(process.env.STUDENT_FILES_DIR, owner.id, item.id, 'admit.jpg')));

  const result = studentStore.saveFile(owner.id, item.id, 'result', {
    buffer: MIN_PNG,
    originalName: 'score.png',
  });
  assert.strictEqual(result.item.hasResult, true);

  const removed = studentStore.deleteFile(owner.id, item.id, 'result');
  assert.strictEqual(removed.removed, true);
  assert.strictEqual(removed.item.hasResult, false);

  const server = await new Promise((resolve) => {
    const s = app.listen(0, '127.0.0.1', () => resolve(s));
  });
  const port = server.address().port;
  const base = `http://127.0.0.1:${port}`;

  try {
    const login = await fetch(`${base}/api/account/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'owner@example.com', password: 'long-enough-password' }),
    });
    assert.strictEqual(login.status, 200);
    const cookie = cookieHeader(login);
    assert.match(cookie, /student_session=/);

    const created = await fetch(`${base}/api/me/items`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', cookie },
      body: JSON.stringify({ kind: 'custom', title: 'UPPSC RO ARO', examDate: '2026-11-15' }),
    });
    assert.strictEqual(created.status, 201);
    const itemId = (await created.json()).item.id;

    const { boundary, body } = multipartBody('admit-card.pdf', MIN_PDF, 'application/pdf');
    const up = await fetch(`${base}/api/me/items/${itemId}/files/admit`, {
      method: 'POST',
      headers: { cookie, 'Content-Type': `multipart/form-data; boundary=${boundary}` },
      body,
    });
    assert.strictEqual(up.status, 201, JSON.stringify(await up.clone().json().catch(() => ({}))));
    const upBody = await up.json();
    assert.strictEqual(upBody.item.hasAdmit, true);
    assert.strictEqual(upBody.file.originalName, 'admit-card.pdf');

    const down = await fetch(`${base}/api/me/items/${itemId}/files/admit`, { headers: { cookie } });
    assert.strictEqual(down.status, 200);
    assert.strictEqual(down.headers.get('content-type'), 'application/pdf');
    assert.match(String(down.headers.get('cache-control')), /no-store/);
    const bytes = Buffer.from(await down.arrayBuffer());
    assert.ok(bytes.equals(MIN_PDF));

    const unauth = await fetch(`${base}/api/me/items/${itemId}/files/admit`);
    assert.strictEqual(unauth.status, 401);

    const otherLogin = await fetch(`${base}/api/account/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'other@example.com', password: 'long-enough-password' }),
    });
    const otherCookie = cookieHeader(otherLogin);
    const steal = await fetch(`${base}/api/me/items/${itemId}/files/admit`, {
      headers: { cookie: otherCookie },
    });
    assert.strictEqual(steal.status, 404);

    const badKind = await fetch(`${base}/api/me/items/${itemId}/files/photo`, {
      method: 'POST',
      headers: { cookie, 'Content-Type': `multipart/form-data; boundary=${boundary}` },
      body,
    });
    assert.strictEqual(badKind.status, 400);

    const htmlPart = multipartBody('x.html', HTML, 'text/html');
    const badType = await fetch(`${base}/api/me/items/${itemId}/files/result`, {
      method: 'POST',
      headers: { cookie, 'Content-Type': `multipart/form-data; boundary=${htmlPart.boundary}` },
      body: htmlPart.body,
    });
    assert.strictEqual(badType.status, 400);

    const del = await fetch(`${base}/api/me/items/${itemId}`, {
      method: 'DELETE',
      headers: { cookie },
    });
    assert.strictEqual(del.status, 200);
    assert.strictEqual(fs.existsSync(path.join(process.env.STUDENT_FILES_DIR, owner.id, itemId)), false);
  } finally {
    await new Promise((resolve, reject) => server.close((err) => (err ? reject(err) : resolve())));
  }

  fs.rmSync(tmp, { recursive: true, force: true });
  console.log('pr13-files: all passed');
}

main().catch((err) => fail(err.message, err));
