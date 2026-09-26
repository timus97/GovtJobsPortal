/**
 * Daily collect progress API + file. Run: node tests/pr22-collect-progress.js
 * No live network collect.
 */
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const progress = require('../scripts/collect/lib/collectProgress');
const dailyCollect = require('../server/src/services/dailyCollect');

function fail(message, err) {
  console.error(`FAIL: ${message}`);
  if (err) console.error(err.message || err);
  process.exit(1);
}

try {
  const opsSrc = fs.readFileSync(path.join(__dirname, '..', 'server', 'src', 'routes', 'ops.js'), 'utf8');
  assert.ok(/collect-progress/.test(opsSrc));
  assert.ok(/collect\/daily/.test(opsSrc));
  assert.ok(!/pipeline:daily/.test(opsSrc), 'ops must not dispatch pipeline:daily');

  const dashSrc = fs.readFileSync(
    path.join(__dirname, '..', 'client', 'src', 'components', 'CollectProgressBar.jsx'),
    'utf8'
  );
  assert.ok(/currentUrl|current\?\.url/.test(dashSrc));
  assert.ok(/Start daily collect/.test(dashSrc));
  const findingsSrc = fs.readFileSync(
    path.join(__dirname, '..', 'client', 'src', 'components', 'CollectFindings.jsx'),
    'utf8'
  );
  assert.ok(/Scraped this run/.test(findingsSrc));
  assert.ok(/lastDate/.test(findingsSrc));

  const prev = progress.readProgress();
  const started = progress.startRun({ runId: 'test-progress', sourcesTotal: 4, pid: process.pid });
  assert.strictEqual(started.running, true);
  assert.strictEqual(started.sourcesTotal, 4);
  progress.setCurrent({
    sourceId: 'becil',
    name: 'BECIL Careers',
    url: 'https://www.becil.com/careers',
    phase: 'fetch',
  });
  const current = progress.readProgress();
  assert.strictEqual(current.current.url, 'https://www.becil.com/careers');
  assert.strictEqual(current.current.phase, 'fetch');
  progress.noteFinding({
    sourceId: 'becil',
    sourceName: 'BECIL Careers',
    pageUrl: 'https://www.becil.com/careers',
    url: 'https://www.becil.com/recruitment/office-assistant.pdf',
    kind: 'pdf',
    status: 'kept',
    title: 'Office Assistant (Contract)',
    lastDate: '2026-09-30',
    vacancies: 6,
    excerpt: 'Applications are invited for walk-in interview',
  });
  progress.noteFinding({
    sourceId: 'becil',
    sourceName: 'BECIL Careers',
    url: 'https://www.becil.com/uploads/brochure.pdf',
    kind: 'pdf',
    status: 'skipped',
    reason: 'pdf_not_recruitment',
    title: 'Brochure',
  });
  const withRows = progress.readProgress();
  assert.ok(withRows.findings.some((f) => f.title === 'Office Assistant (Contract)' && f.lastDate === '2026-09-30'));
  assert.ok(withRows.findings.some((f) => f.status === 'skipped' && f.reason === 'pdf_not_recruitment'));
  progress.finishSource({ sourceId: 'becil', name: 'BECIL Careers', ok: true, written: 1, durationMs: 10 });
  const logged = progress.readProgress();
  assert.ok(logged.sourcesLog[0].findings.length >= 2);
  assert.strictEqual(logged.sourcesLog[0].kept, 1);
  assert.strictEqual(logged.sourcesLog[0].skipped, 1);
  progress.finishRun();
  const done = progress.readProgress();
  assert.strictEqual(done.running, false);
  assert.strictEqual(done.sourcesDone, 1);

  const status = dailyCollect.status();
  assert.ok(status && typeof status.running === 'boolean');
  assert.ok(Object.prototype.hasOwnProperty.call(status, 'current'));

  if (prev && prev.runId && prev.runId !== 'test-progress') {
    progress.writeProgress(prev);
  }

  console.log('PASS pr22-collect-progress');
} catch (err) {
  fail(err.message, err);
}
