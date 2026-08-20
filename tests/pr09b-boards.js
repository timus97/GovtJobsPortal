/**
 * PR09b bank / regulator / post registry + genericBoard.
 * Run: node tests/pr09b-boards.js
 */
const assert = require('assert');
const fs = require('fs');
const path = require('path');

const { parseBoardPage, isBoardNoticeLink, collectGenericBoard } = require('../scripts/collect/collectors/genericBoard');
const { parseHttpsUrl } = require('../server/src/services/collectQueue');

function fail(message, err) {
  console.error(`FAIL: ${message}`);
  if (err) console.error(err.message || err);
  process.exit(1);
}

try {
  assert.strictEqual(typeof collectGenericBoard, 'function');
  assert.ok(isBoardNoticeLink({ title: 'Recruitment of Officer Grade A', href: 'https://www.sebi.gov.in/x' }));
  assert.ok(!isBoardNoticeLink({ title: 'Login', href: 'https://www.sebi.gov.in/login' }));

  const parsed = parseBoardPage(
    `<html><body><a href="/vacancy/1">Recruitment of Security Coordinator</a><a href="/login">Login</a></body></html>`,
    'https://www.sebi.gov.in/sebiweb/about/AboutAction.do?doVacancies=yes'
  );
  assert.ok(parsed.parsed);
  assert.strictEqual(parsed.links.length, 1);

  const runDailySrc = fs.readFileSync(path.join(__dirname, '..', 'scripts', 'collect', 'runDaily.js'), 'utf8');
  assert.ok(/genericBoard:\s*collectGenericBoard/.test(runDailySrc));
  assert.ok(/opportunityType/.test(runDailySrc));

  const registry = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'data', 'sources', 'registry.json'), 'utf8'));
  const ids = [
    'rbi_opportunities',
    'nabard_careers',
    'sebi_vacancies',
    'india_post',
    'fci_recruitment',
    'lic_careers',
    'epfo_recruitment',
  ];
  const byId = Object.fromEntries(registry.sources.map((s) => [s.sourceId, s]));
  for (const id of ids) {
    const row = byId[id];
    assert.ok(row, `missing ${id}`);
    assert.strictEqual(row.collector, 'genericBoard');
    assert.strictEqual(row.priority, 'P1');
    assert.ok(row.listUrls?.length);
  }
  assert.ok(byId.psc_uppsc, 'must sit on PR09a registry');
  assert.strictEqual(parseHttpsUrl('https://opportunities.rbi.org.in/').ok, true);
  assert.strictEqual(parseHttpsUrl('https://www.nabard.org/careers-notices1.aspx?cid=693&id=26').ok, true);

  console.log('pr09b-boards: all passed');
} catch (err) {
  fail(err.message, err);
}
