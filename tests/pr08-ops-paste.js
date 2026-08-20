/**
 * PR08 paste-URL queue smoke checks. Run: node tests/pr08-ops-paste.js
 * No live network.
 */
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'pr08-ops-'));
process.env.COLLECT_JOBS_PATH = path.join(tmp, 'collect-jobs.json');
process.env.OPS_PASTE_DIR = path.join(tmp, 'ops_paste');
process.env.OPS_JOBS_JSON = path.join(tmp, 'jobs.json');
process.env.OPS_OPPORTUNITIES_JSON = path.join(tmp, 'opportunities.json');
process.env.OPS_HOST_GAP_MS = '0';
process.env.OPS_PUBLISH_DEBOUNCE_MS = '0';
process.env.OPS_QUEUE_AUTO = 'off';
delete process.env.OPS_INGEST_TOKEN;
delete process.env.GITHUB_TOKEN;
delete process.env.GITHUB_REPOSITORY;

const queue = require('../server/src/services/collectQueue');

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

const HTML = `
<html><head><title>BECIL Office Assistant Recruitment 2026</title></head>
<body>
  <h1>Office Assistant (Contract)</h1>
  <p>Last date 30-09-2026. Walk-in / interview only. No written test.</p>
  <a href="https://www.becil.com/careers">Apply</a>
</body></html>
`;

async function main() {
  const badHttp = queue.parseHttpsUrl('http://upsc.gov.in/foo');
  assert.strictEqual(badHttp.ok, false);
  const badHost = queue.parseHttpsUrl('https://example.com/jobs');
  assert.strictEqual(badHost.ok, false);
  assert.strictEqual(badHost.reason, 'host_not_allowed');
  const okUrl = queue.parseHttpsUrl('https://upsc.gov.in/examinations/active-exams');
  assert.strictEqual(okUrl.ok, true);

  queue.setFetchHtml(async (url) => ({ html: HTML, finalUrl: url }));

  try {
    queue.submit({ url: 'https://evil.example/x' });
    assert.fail('expected host reject');
  } catch (err) {
    assert.strictEqual(err.code, 'HOST');
  }

  const created = queue.submit({
    url: 'https://www.becil.com/careers',
    sourceLabel: 'BECIL',
  });
  assert.ok(created.id);
  assert.strictEqual(created.state, 'pending');
  await queue.processJob(created.id);
  const fetched = queue.findJob(created.id);
  assert.ok(fetched.extracted, 'expected extracted facts');
  assert.ok(['needs_review', 'valid'].includes(fetched.state), fetched.state);
  assert.strictEqual(fetched.extracted.eligibilityParse.complete, false);
  assert.ok(fetched.extracted.officialUrl.startsWith('https://'));

  const pending = queue.submit({ url: 'https://ssc.gov.in/home/notice-board' });
  const cancelled = queue.cancel(pending.id);
  assert.strictEqual(cancelled.state, 'cancelled');
  try {
    queue.cancel(created.id);
    assert.fail('should not cancel non-pending');
  } catch (err) {
    assert.strictEqual(err.code, 'STATE');
  }

  try {
    queue.reject(created.id, '');
    assert.fail('empty reason');
  } catch (err) {
    assert.strictEqual(err.code, 'VALIDATION');
  }

  const patched = queue.patchReview(created.id, {
    title: 'Office Assistant (Contract)',
    organization: 'BECIL',
    officialUrl: 'https://www.becil.com/careers',
    lastDate: '2026-09-30',
    selectionProcess: 'contract_interview',
    hasExam: false,
  });
  assert.ok(patched.extracted.lastDate === '2026-09-30');

  const published = await queue.publish(created.id);
  assert.strictEqual(published.state, 'published_local');
  assert.ok(/not in git SoR/i.test(published.reason));
  const staging = path.join(tmp, 'ops_paste', `${published.opportunityId}.json`);
  assert.ok(fs.existsSync(staging), 'staging file missing');
  const jobs = JSON.parse(fs.readFileSync(path.join(tmp, 'jobs.json'), 'utf8'));
  assert.ok(jobs.some((j) => j.id === published.opportunityId));

  const rejected = queue.submit({ url: 'https://ncs.gov.in/' });
  await queue.processJob(rejected.id);
  const done = queue.reject(rejected.id, 'duplicate of seed');
  assert.strictEqual(done.state, 'rejected');
  assert.strictEqual(done.reason, 'duplicate of seed');

  const review = queue.reviewQueue();
  assert.ok(review.every((j) => j.state === 'needs_review' || j.state === 'valid'));

  const ingest = fs.readFileSync(
    path.join(__dirname, '..', '.github', 'workflows', 'ops-ingest.yml'),
    'utf8'
  );
  assert.ok(/ops-ingest/.test(ingest));
  assert.ok(!/npm run pipeline:daily/.test(ingest), 'ops-ingest must not run pipeline:daily');
  assert.ok(!/daily-collect/.test(ingest), 'ops-ingest must not call daily-collect');

  const daily = fs.readFileSync(
    path.join(__dirname, '..', '.github', 'workflows', 'daily-collect.yml'),
    'utf8'
  );
  assert.ok(!/collect-jobs\.json/.test(daily), 'daily collect must not write collect-jobs');

  const queueSrc = fs.readFileSync(
    path.join(__dirname, '..', 'server', 'src', 'services', 'collectQueue.js'),
    'utf8'
  );
  assert.ok(!/workflow_dispatch['"]?\s*pipeline:daily/.test(queueSrc));
  assert.ok(!/pipeline:daily/.test(queueSrc));

  console.log('pr08-ops-paste: all passed');
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
