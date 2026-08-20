/**
 * PR09a generic PSC collector + registry smoke checks.
 * Run: node tests/pr09a-psc.js
 * No live network.
 */
const assert = require('assert');
const fs = require('fs');
const path = require('path');

const { parsePscPage, isPscNoticeLink, collectGenericPsc } = require('../scripts/collect/collectors/genericPsc');

const HTML = `
<html><body>
<h1>Notifications</h1>
<table>
  <tr><th>Name of Examination</th><th>Date of Notification</th><th>Last Date</th></tr>
  <tr><td>Combined State / Upper Subordinate Services Examination, 2026</td><td>01.07.2026</td><td>03.08.2026</td></tr>
</table>
<p><a href="/CandidatePages/Notifications.aspx?id=pcs-2026">PCS Advertisement 2026</a></p>
<p><a href="/login">Candidate login</a></p>
</body></html>
`;

function fail(message, err) {
  console.error(`FAIL: ${message}`);
  if (err) console.error(err.message || err);
  process.exit(1);
}

try {
  assert.strictEqual(typeof collectGenericPsc, 'function');
  assert.ok(
    isPscNoticeLink({ title: 'PCS Advertisement 2026', href: 'https://uppsc.up.nic.in/n' })
  );
  assert.ok(!isPscNoticeLink({ title: 'Candidate login', href: 'https://uppsc.up.nic.in/login' }));

  const parsed = parsePscPage(HTML, 'https://uppsc.up.nic.in/CandidatePages/Notifications.aspx');
  assert.ok(parsed.parsed);
  assert.ok(parsed.calendarRows.length >= 1, 'expected a calendar row');
  assert.ok(parsed.links.some((l) => /pcs advertisement/i.test(l.title)));
  assert.ok(!parsed.links.some((l) => /login/i.test(l.title)));

  assert.ok(
    !fs.existsSync(path.join(__dirname, '..', 'scripts', 'collect', 'registry', 'psc.json')),
    'must not add scripts/collect/registry/psc.json'
  );

  const runDailySrc = fs.readFileSync(
    path.join(__dirname, '..', 'scripts', 'collect', 'runDaily.js'),
    'utf8'
  );
  assert.ok(/genericPsc:\s*collectGenericPsc/.test(runDailySrc));

  const registry = JSON.parse(
    fs.readFileSync(path.join(__dirname, '..', 'data', 'sources', 'registry.json'), 'utf8')
  );
  const ids = [
    'psc_uppsc',
    'psc_bpsc',
    'psc_mpsc',
    'psc_tnpsc',
    'psc_wbpsc',
    'psc_rpsc',
    'psc_gpsc',
    'psc_kpsc',
    'psc_kerala',
    'psc_appsc',
  ];
  const byId = Object.fromEntries(registry.sources.map((s) => [s.sourceId, s]));
  for (const id of ids) {
    const row = byId[id];
    assert.ok(row, `missing registry ${id}`);
    assert.strictEqual(row.collector, 'genericPsc');
    assert.strictEqual(row.priority, 'P2');
    assert.ok(Array.isArray(row.listUrls) && row.listUrls.length >= 1);
    assert.ok(row.listUrls.every((u) => /^https:\/\//.test(u)));
  }
  assert.ok(byId.becil, 'must keep existing becil source');
  console.log('pr09a-psc: all passed');
} catch (err) {
  fail(err.message, err);
}
