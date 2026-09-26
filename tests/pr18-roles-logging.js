/**
 * PR18: structured logger + admin source registry writes.
 * Run: node tests/pr18-roles-logging.js
 */
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'pr18-roles-'));
const registryFile = path.join(tmpDir, 'registry.json');
const logFile = path.join(tmpDir, 'app.log');

process.env.REGISTRY_PATH = registryFile;
process.env.APP_LOG_PATH = logFile;
process.env.APP_LOG_FILE = 'on';

const logger = require('../server/src/services/logger');
const sourceRegistry = require('../server/src/services/sourceRegistry');

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

try {
  logger.resetForTests();

  const rec = logger.info('auth.student', 'Student signed in', {
    role: 'student',
    actor: 'ada@example.com',
    action: 'student.login',
    meta: { password: 'super-secret', token: 'abc', note: 'ok' },
  });
  assert.strictEqual(rec.unit, 'auth.student');
  assert.strictEqual(rec.role, 'student');
  assert.strictEqual(rec.meta.password, '[redacted]');
  assert.strictEqual(rec.meta.token, '[redacted]');
  assert.strictEqual(rec.meta.note, 'ok');

  logger.warn('collect', 'Queue worker failed', { role: 'system', action: 'collect.worker_failed' });
  const listed = logger.list({ unit: 'auth', role: 'student' });
  assert.ok(listed.items.some((item) => item.action === 'student.login'));
  assert.ok(listed.items.every((item) => item.unit.startsWith('auth')));

  fs.writeFileSync(
    registryFile,
    JSON.stringify(
      {
        version: 2,
        updatedAt: '2026-01-01',
        sources: [
          {
            sourceId: 'demo_board',
            name: 'Demo Board',
            category: 'board',
            baseUrl: 'https://demo.gov.in/',
            listUrls: ['https://demo.gov.in/jobs'],
            orgTypeDefault: 'central',
            priority: 'P1',
            method: 'html_scrape',
            enabled: true,
          },
        ],
      },
      null,
      2
    ),
    'utf8'
  );

  const updated = sourceRegistry.updateSource('demo_board', { enabled: false, name: 'Demo Board (paused)' });
  assert.strictEqual(updated.enabled, false);
  assert.strictEqual(updated.name, 'Demo Board (paused)');
  assert.strictEqual(sourceRegistry.getSource('missing'), null);

  const created = sourceRegistry.createSource({
    sourceId: 'new_psc',
    name: 'New PSC',
    category: 'psc',
    baseUrl: 'https://psc.gov.in/',
    listUrls: 'https://psc.gov.in/list\nhttps://psc.gov.in/more',
    priority: 'P2',
    method: 'html_scrape',
  });
  assert.strictEqual(created.sourceId, 'new_psc');
  assert.deepStrictEqual(created.listUrls, ['https://psc.gov.in/list', 'https://psc.gov.in/more']);
  assert.strictEqual(sourceRegistry.collectUrlOf(created), 'https://psc.gov.in/list');

  let dup = null;
  try {
    sourceRegistry.createSource({ sourceId: 'new_psc', name: 'Dup' });
  } catch (err) {
    dup = err;
  }
  assert.ok(dup && dup.code === 'DUPLICATE');

  let bad = null;
  try {
    sourceRegistry.updateSource('demo_board', { baseUrl: 'http://insecure.example' });
  } catch (err) {
    bad = err;
  }
  assert.ok(bad && bad.code === 'VALIDATION');

  console.log('PASS pr18-roles-logging');
} catch (err) {
  fail(err.message, err);
} finally {
  try {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  } catch {
    /* ignore */
  }
}
