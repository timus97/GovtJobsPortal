/**
 * PR06 ExamSeries + Prepare-for smoke checks.
 * Run: node tests/pr06-prepare.js
 */
const assert = require('assert');
const fs = require('fs');
const path = require('path');

const {
  isValidExamSeries,
  isCuetName,
  isNetName,
  normalizeExamSeries,
  seriesMatchesJob,
  looksLikeCalendarRow,
} = require('../shared/examSeriesSchema');
const { matchExamSeries, AGE_WHEN_NOTIFIED } = require('../shared/eligibilityMatch');

const FIXTURE_ID = '912c0026508e7dca';

function fail(message, err) {
  console.error(`FAIL: ${message}`);
  if (err) console.error(err.message || err);
  process.exit(1);
}

try {
  assert.strictEqual(isCuetName('CUET (UG) 2026'), true);
  assert.strictEqual(isCuetName('Civil Services Examination'), false);
  assert.strictEqual(isNetName('UGC NET'), true);
  assert.strictEqual(normalizeExamSeries({ board: 'NTA', name: 'CUET UG', officialUrl: 'https://cuet.nta.nic.in/' }), null);

  const seed = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'data', 'seed', 'exam_series.json'), 'utf8'));
  assert.ok(seed.length >= 12, `expected a 12-month P0 seed list, got ${seed.length}`);
  const net = seed.find((s) => s.id === 'ugc-net');
  assert.ok(net, 'UGC NET must be seeded immediately');
  assert.strictEqual(net.applyNever, true);
  assert.ok(!seed.some((s) => isCuetName(s.name)), 'CUET must not be seeded');

  for (const row of seed) {
    const series = normalizeExamSeries(row);
    const errors = isValidExamSeries(series);
    assert.strictEqual(errors.length, 0, `${row.id}: ${errors.join('; ')}`);
    assert.strictEqual(series.kind, 'series');
  }

  const cglJob = {
    id: FIXTURE_ID,
    title: 'Combined Graduate Level Examination (fixture)',
    organization: 'Staff Selection Commission',
    sourceId: 'seed_manual',
    lastDate: '2026-09-30',
    status: 'open',
  };
  const cglSeries = normalizeExamSeries(seed.find((s) => s.id === 'ssc-cgl'));
  assert.ok(seriesMatchesJob(cglSeries, cglJob), 'SSC CGL fixture should link to CGL series');
  assert.ok(!seriesMatchesJob(normalizeExamSeries(net), cglJob), 'NET must never match a vacancy');

  assert.strictEqual(looksLikeCalendarRow({ sourceId: 'upsc_calendar', title: 'CSE' }), true);
  assert.strictEqual(looksLikeCalendarRow({ collectorVersion: 'calendar-v1', hasExam: true }), true);
  assert.strictEqual(looksLikeCalendarRow({ sourceId: 'becil', title: 'Office Assistant' }), false);

  const profile = {
    dob: '1998-01-15',
    highestEducation: 'pg',
    reservationCategory: 'UR',
    birthState: 'DL',
    domicileStates: ['DL'],
  };
  const decorated = seed.map((s) =>
    normalizeExamSeries({
      ...s,
      linkedOpportunityIds: s.id === 'ssc-cgl' ? [FIXTURE_ID] : [],
    })
  );
  const { matches, excluded } = matchExamSeries(profile, decorated);
  const netMatch = matches.find((m) => m.id === 'ugc-net');
  assert.ok(netMatch, 'UGC NET should appear as prepare-for for a PG profile');
  assert.strictEqual(netMatch.canApply, false);
  assert.strictEqual(netMatch.applyNever, true);
  assert.ok(netMatch.reasons.some((r) => r.detail === AGE_WHEN_NOTIFIED));
  const cglMatch = matches.find((m) => m.id === 'ssc-cgl');
  assert.ok(cglMatch, 'CGL should recommend for PG');
  assert.strictEqual(cglMatch.canApply, true);
  const tenth = {
    ...profile,
    highestEducation: '10th',
  };
  const low = matchExamSeries(tenth, decorated);
  assert.ok(low.excluded.some((m) => m.id === 'ugc-net'), '10th should fail NET education floor');
  assert.ok(low.matches.some((m) => m.id === 'ssc-mts' || m.id === 'rrb-group-d'));

  const seriesPath = path.join(__dirname, '..', 'data', 'processed', 'exam_series.json');
  assert.ok(fs.existsSync(seriesPath), 'run npm run process to write exam_series.json');
  const published = JSON.parse(fs.readFileSync(seriesPath, 'utf8'));
  assert.ok(published.some((s) => s.id === 'ugc-net'));
  assert.ok(!published.some((s) => isCuetName(s.name)));
  const publishedCgl = published.find((s) => s.id === 'ssc-cgl');
  assert.ok(publishedCgl, 'processed CGL series missing');
  assert.ok(
    (publishedCgl.linkedOpportunityIds || []).includes(FIXTURE_ID),
    'CGL series should link the open fixture opportunity'
  );

  const jobs = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'data', 'processed', 'jobs.json'), 'utf8'));
  const fixture = jobs.find((j) => j.id === FIXTURE_ID);
  assert.ok(fixture, 'SSC CGL fixture job missing');
  assert.ok(!jobs.some((j) => isNetName(j.title)));

  const store = require('../server/src/services/jobStore');
  const listed = store.listExamSeries({});
  assert.ok(listed.total >= seed.length);
  assert.ok(listed.boards.includes('NTA'));
  const apiCgl = store.getExamSeriesById('ssc-cgl');
  assert.ok(apiCgl.canApply, 'API should allow apply only when a linked window is open');
  const apiNet = store.getExamSeriesById('ugc-net');
  assert.strictEqual(apiNet.canApply, false);
  assert.strictEqual(apiNet.applyNever, true);

  const prepareSrc = fs.readFileSync(
    path.join(__dirname, '..', 'client', 'src', 'pages', 'PreparePage.jsx'),
    'utf8'
  );
  assert.ok(/Apply \(open window\)/.test(prepareSrc));
  assert.ok(/Official calendar/.test(prepareSrc));
  assert.ok(/Not an official eligibility decision/.test(prepareSrc));

  console.log('pr06-prepare: all passed');
} catch (err) {
  fail(err.message, err);
}
