/**
 * PR10 hardening: post-wise PwBD, reserved-only, unpublish, fixtures, p95, disk.
 * Run: node tests/pr10-hardening.js
 */
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');

const golden = require('./fixtures/golden-opportunities.json');
const { extractOpportunityFacts } = require('../shared/eligibilityFacts');
const { matchOpportunities } = require('../shared/eligibilityMatch');

function fail(message, err) {
  console.error(`FAIL: ${message}`);
  if (err) console.error(err.message || err);
  process.exit(1);
}

function byId(id) {
  const row = golden.find((o) => o.id === id);
  assert.ok(row, `missing golden fixture ${id}`);
  return row;
}

function reason(row, rule) {
  return (row.reasons || []).find((r) => r.rule === rule);
}

function dirBytes(dir) {
  if (!fs.existsSync(dir)) return 0;
  let total = 0;
  const stack = [dir];
  while (stack.length) {
    const current = stack.pop();
    for (const name of fs.readdirSync(current)) {
      const full = path.join(current, name);
      const st = fs.statSync(full);
      if (st.isDirectory()) stack.push(full);
      else total += st.size;
    }
  }
  return total;
}

const baseProfile = {
  dob: '1998-06-15',
  highestEducation: 'graduate',
  educationDiscipline: 'engineering',
  birthState: 'MH',
  domicileStates: ['MH'],
  gender: 'male',
  pwbd: { hasDisability: false, category: 'none' },
  reservationCategory: 'UR',
};

try {
  assert.ok(golden.length >= 20, `need ≥20 golden fixtures, got ${golden.length}`);

  const orgs = golden.map((row) => `${row.organization} ${row.title} ${row.officialUrl}`).join(' ');
  for (const board of [
    /Union Public Service Commission/i,
    /Staff Selection Commission/i,
    /Institute of Banking Personnel Selection/i,
    /State Bank of India/i,
    /Railway Recruitment Board/i,
    /Uttar Pradesh Public Service Commission/i,
    /Bihar Public Service Commission/i,
    /Oil and Natural Gas Corporation/i,
    /NTPC Limited/i,
  ]) {
    assert.ok(board.test(orgs), `fixture pack missing ${board}`);
  }

  for (const row of golden) {
    const facts = extractOpportunityFacts(row);
    if (!row.ageMin && /Age \d+/i.test((row.eligibility || []).join(' '))) {
      assert.strictEqual(facts.ageMin, null, `${row.id} must not NLP eligibility[]`);
    }
    assert.ok(row.officialUrl && row.officialUrl.startsWith('https://'), `${row.id} officialUrl`);
  }

  const ohProfile = {
    ...baseProfile,
    pwbd: { hasDisability: true, category: 'OH' },
  };
  const vhProfile = {
    ...baseProfile,
    pwbd: { hasDisability: true, category: 'VH' },
  };

  const postwise = matchOpportunities(ohProfile, [byId('golden-pwbd-postwise')]);
  const postwiseRow = postwise.matches.find((m) => m.id === 'golden-pwbd-postwise');
  assert.ok(postwiseRow, 'OH must match when listed posts include OH');
  assert.strictEqual(reason(postwiseRow, 'pwbd').outcome, 'pass');
  assert.match(reason(postwiseRow, 'pwbd').detail, /Junior Engineer/);

  const vhNone = matchOpportunities(vhProfile, [byId('golden-pwbd-postwise-none')]);
  const vhFail = vhNone.excluded.find((m) => m.id === 'golden-pwbd-postwise-none');
  assert.ok(vhFail, 'VH must fail when listed posts omit VH');
  assert.strictEqual(reason(vhFail, 'pwbd').outcome, 'fail');

  const ambiguous = matchOpportunities(ohProfile, [byId('golden-pwbd-ambiguous-posts')]);
  const ambRow = ambiguous.matches.find((m) => m.id === 'golden-pwbd-ambiguous-posts');
  assert.ok(ambRow, 'ambiguous post list must not exclude');
  assert.strictEqual(reason(ambRow, 'pwbd').outcome, 'unknown');
  assert.match(reason(ambRow, 'pwbd').detail, /verify official/i);

  const urReserved = matchOpportunities(baseProfile, [byId('golden-reserved-scst')]);
  const urFail = urReserved.excluded.find((m) => m.id === 'golden-reserved-scst');
  assert.ok(urFail, 'structured reserved-only must fail UR');
  assert.strictEqual(reason(urFail, 'category').outcome, 'fail');

  const scReserved = matchOpportunities(
    { ...baseProfile, reservationCategory: 'SC' },
    [byId('golden-reserved-scst')]
  );
  assert.ok(scReserved.matches.some((m) => m.id === 'golden-reserved-scst'));

  const unstructured = matchOpportunities(baseProfile, [byId('golden-reserved-unstructured')]);
  const unstructuredRow = unstructured.matches.find((m) => m.id === 'golden-reserved-unstructured');
  assert.ok(unstructuredRow, 'text-only reserved mention must not fail UR');
  assert.strictEqual(reason(unstructuredRow, 'category').outcome, 'pass');

  const unknownOnly = extractOpportunityFacts({
    id: 'text-reserved',
    officialUrl: 'https://uppsc.up.nic.in/',
    eligibility: ['Reserved for SC only'],
  });
  assert.strictEqual(unknownOnly.reservedOnly, null);
  assert.strictEqual(unknownOnly.openToCategories, null);

  const TWO_GB = 2 * 1024 * 1024 * 1024;
  const root = path.join(__dirname, '..');
  const disk = dirBytes(path.join(root, 'data', 'processed')) + dirBytes(path.join(root, 'data', 'raw'));
  assert.ok(disk < TWO_GB, `processed + raw is ${disk} bytes (need < 2 GB)`);

  const synth = [];
  for (let i = 0; i < 10000; i += 1) {
    synth.push({
      id: `pr10-synth-${i}`,
      title: `Synthetic ${i}`,
      organization: 'Board',
      officialUrl: 'https://ssc.gov.in/',
      lastDate: i % 3 === 0 ? null : '2027-12-31',
      qualification: i % 2 === 0 ? 'graduate' : null,
      ageMin: 18,
      ageMax: 32,
      ageAsOnDate: '2026-08-01',
      reservedOnly: i % 17 === 0,
      openToCategories: i % 17 === 0 ? ['SC', 'ST'] : null,
      posts:
        i % 11 === 0
          ? [
              { title: 'Post A', pwbdAllowed: true, pwbdCategories: ['OH'] },
              { title: 'Post B', pwbdAllowed: false },
            ]
          : null,
    });
  }
  const samples = [];
  for (let run = 0; run < 5; run += 1) {
    const t0 = process.hrtime.bigint();
    const out = matchOpportunities(baseProfile, synth);
    const ms = Number(process.hrtime.bigint() - t0) / 1e6;
    assert.strictEqual(out.matches.length + out.excluded.length, 10000);
    samples.push(ms);
  }
  samples.sort((a, b) => a - b);
  const p95 = samples[samples.length - 1];
  assert.ok(p95 < 200, `10k match p95 ${p95.toFixed(1)}ms (need < 200)`);

  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'pr10-ops-'));
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
  process.env.FEATURE_UNPUBLISH = 'on';

  const queuePath = require.resolve('../server/src/services/collectQueue');
  delete require.cache[queuePath];
  const queue = require('../server/src/services/collectQueue');
  assert.strictEqual(queue.featureUnpublishOn(), true);

  queue.setFetchHtml(async (url) => ({
    html: '<html><head><title>Paste</title></head><body><h1>Office Assistant</h1></body></html>',
    finalUrl: url,
  }));

  (async () => {
    const created = queue.submit({ url: 'https://www.becil.com/careers', sourceLabel: 'BECIL' });
    await queue.processJob(created.id);
    queue.patchReview(created.id, {
      title: 'Office Assistant (Contract)',
      organization: 'BECIL',
      officialUrl: 'https://www.becil.com/careers',
      lastDate: '2026-09-30',
      selectionProcess: 'contract_interview',
      hasExam: false,
    });
    const published = await queue.publish(created.id);
    assert.strictEqual(published.state, 'published_local');
    const oppId = published.opportunityId;
    const staging = path.join(tmp, 'ops_paste', `${oppId}.json`);
    assert.ok(fs.existsSync(staging));
    assert.ok(JSON.parse(fs.readFileSync(path.join(tmp, 'jobs.json'), 'utf8')).some((j) => j.id === oppId));

    assert.strictEqual(await queue.unpublish('missing-id'), null);

    const pending = queue.submit({ url: 'https://ssc.gov.in/home/notice-board' });
    try {
      await queue.unpublish(pending.id);
      assert.fail('pending must not unpublish');
    } catch (err) {
      assert.strictEqual(err.code, 'STATE');
    }

    const gone = await queue.unpublish(created.id);
    assert.strictEqual(gone.state, 'unpublished');
    assert.ok(!fs.existsSync(staging));
    assert.ok(!JSON.parse(fs.readFileSync(path.join(tmp, 'jobs.json'), 'utf8')).some((j) => j.id === oppId));

    process.env.FEATURE_UNPUBLISH = 'off';
    try {
      await queue.unpublish(created.id);
      assert.fail('flag off must throw');
    } catch (err) {
      assert.strictEqual(err.code, 'FEATURE');
    }

    const opsSrc = fs.readFileSync(path.join(__dirname, '..', 'server', 'src', 'routes', 'ops.js'), 'utf8');
    assert.ok(/review\/:id\/unpublish/.test(opsSrc));

    fs.rmSync(tmp, { recursive: true, force: true });
    console.log(`pr10-hardening: all passed (${golden.length} fixtures, p95 ${p95.toFixed(1)}ms, disk ${disk} B)`);
  })().catch((err) => {
    try {
      fs.rmSync(tmp, { recursive: true, force: true });
    } catch {
      /* ignore */
    }
    fail(err.message, err);
  });
} catch (err) {
  fail(err.message, err);
}
