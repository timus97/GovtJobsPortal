/**
 * PR09c school / health / defence / GATE / NET / apprenticeship.
 * Run: node tests/pr09c-calendars.js
 */
const assert = require('assert');
const fs = require('fs');
const path = require('path');

const { isCuetName, looksLikeCalendarRow } = require('../shared/examSeriesSchema');

function fail(message, err) {
  console.error(`FAIL: ${message}`);
  if (err) console.error(err.message || err);
  process.exit(1);
}

try {
  const registry = JSON.parse(
    fs.readFileSync(path.join(__dirname, '..', 'data', 'sources', 'registry.json'), 'utf8')
  );
  const byId = Object.fromEntries(registry.sources.map((s) => [s.sourceId, s]));

  for (const id of [
    'kvs_recruitment',
    'nvs_recruitment',
    'dsssb_recruitment',
    'ctet_calendar',
    'aiims_recruitment',
    'esic_recruitment',
    'nhm_recruitment',
    'drdo_careers',
    'isro_careers',
    'barc_careers',
    'gate_calendar',
    'nta_ugc_net_calendar',
  ]) {
    assert.ok(byId[id], `missing ${id}`);
    assert.ok(byId[id].listUrls?.length);
    assert.notStrictEqual(byId[id].method, 'browser_scrape');
  }

  for (const id of ['defence_army', 'defence_navy', 'defence_airforce']) {
    const row = byId[id];
    assert.ok(row, `missing ${id}`);
    assert.strictEqual(row.method, 'manual');
    assert.strictEqual(row.enabled, false);
    assert.ok(/no captcha bypass/i.test(row.robotsNotes));
  }

  assert.strictEqual(byId.apprenticeship_india.opportunityType, 'apprenticeship');
  assert.strictEqual(byId.apprenticeship_india.enabled, true);
  assert.notStrictEqual(byId.apprenticeship_india.method, 'manual');

  assert.ok(!registry.sources.some((s) => isCuetName(s.name) || /cuet/i.test(s.sourceId)));

  assert.ok(looksLikeCalendarRow({ sourceId: 'gate_calendar', title: 'GATE 2027' }));
  assert.ok(looksLikeCalendarRow({ sourceId: 'nta_ugc_net_calendar', title: 'UGC NET' }));
  assert.ok(looksLikeCalendarRow({ sourceId: 'ctet_calendar', title: 'CTET' }));

  const series = JSON.parse(
    fs.readFileSync(path.join(__dirname, '..', 'data', 'seed', 'exam_series.json'), 'utf8')
  );
  const net = series.find((s) => s.id === 'ugc-net');
  const gate = series.find((s) => s.id === 'gate');
  const ctet = series.find((s) => s.id === 'ctet');
  assert.ok(net && net.applyNever, 'UGC NET must stay prepare-for');
  assert.ok(gate && gate.applyNever, 'GATE is a score, not a vacancy');
  assert.ok(ctet && ctet.applyNever, 'CTET is eligibility-like, not a vacancy');
  assert.ok(!series.some((s) => isCuetName(s.name)));

  const runDailySrc = fs.readFileSync(
    path.join(__dirname, '..', 'scripts', 'collect', 'runDaily.js'),
    'utf8'
  );
  assert.ok(/opportunityType/.test(runDailySrc));
  assert.ok(!/joinindianarmy/.test(runDailySrc) || !/playwright/i.test(runDailySrc));

  const psc = require('../scripts/collect/collectors/genericPsc');
  assert.ok(typeof psc.collectGenericPsc === 'function');

  console.log('pr09c-calendars: all passed');
} catch (err) {
  fail(err.message, err);
}
