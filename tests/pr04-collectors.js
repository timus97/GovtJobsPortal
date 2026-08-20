/**
 * PR04 IBPS / SBI / RRB / Employment News smoke checks.
 * Run: node tests/pr04-collectors.js
 * No live network.
 */
const assert = require('assert');
const fs = require('fs');
const path = require('path');

const { parseJobHighlights } = require('../scripts/collect/collectors/employmentNews');
const { isIbpsNoticeLink, isYearStampedCalendarPdf } = require('../scripts/collect/collectors/ibps');
const { parseSbiOpenings, guessSelectionProcess } = require('../scripts/collect/collectors/sbi');
const {
  isChandigarhZonalHost,
  isNationalApplyHost,
  isRrbNoticeLink,
} = require('../scripts/collect/collectors/rrb');

const HIGHLIGHTS_HTML = `
<html><body>
<h4>JOB HIGHLIGHTS</h4>
<table>
  <tr>
    <th>ORGANISATION</th>
    <th>POST</th>
    <th>METHOD OF APPOINTMENT</th>
    <th>LAST DATE (DD/MM/YYYY)</th>
  </tr>
  <tr>
    <td>FLUID CONTROL RESEARCH INSTITUTE</td>
    <td>RESEARCH ENGINEER &amp; OTHERS</td>
    <td>Recruitment</td>
    <td>24/08/2026</td>
  </tr>
  <tr>
    <td>NATIONAL COUNCIL FOR COOPERATIVE TRAINING</td>
    <td>CONSULTANT (LEGAL)</td>
    <td>Recruitment</td>
    <td>23/08/2026</td>
  </tr>
  <tr>
    <td><a href="AllJobs.aspx?k=All">View More</a></td>
    <td></td>
    <td></td>
    <td></td>
  </tr>
</table>
<p><a href="/epaper/latest.pdf">Download this week's e-paper</a></p>
</body></html>
`;

const SBI_HTML = `
<html><body>
<div class="opening">
  RECRUITMENT OF JUNIOR ASSOCIATES (CUSTOMER SUPPORT &amp; SALES)
  ADVERTISEMENT NO: CRPD/CR/2026-27/17
  LAST DATE TO APPLY : 31-08-2026
  <a href="https://ibpsreg.ibps.in/sbijajul26/">Apply Now</a>
</div>
<div class="opening">
  ENGAGEMENT OF SPECIALIST CADRE OFFICERS ON CONTRACT BASIS
  ADVERTISEMENT NO: CRPD/SCO/2026-27/13
  LAST DATE TO APPLY : 27-08-2026
  <a href="https://recruitment.sbi.bank.in/crpd-sco-2026-27-13/apply">Apply Now</a>
</div>
</body></html>
`;

function fail(message, err) {
  console.error(`FAIL: ${message}`);
  if (err) console.error(err.message || err);
  process.exit(1);
}

function main() {
  const highlights = parseJobHighlights(
    HIGHLIGHTS_HTML,
    'https://employmentnews.gov.in/newemp/Home.aspx'
  );
  assert.strictEqual(highlights.length, 2, `expected 2 highlight rows, got ${highlights.length}`);
  assert.ok(
    highlights.some((r) => /research engineer/i.test(r.title)),
    'expected Research Engineer row'
  );
  const research = highlights.find((r) => /research engineer/i.test(r.title));
  assert.strictEqual(research.lastDate, '2026-08-24');
  assert.ok(!highlights.some((r) => /view more/i.test(r.title)), 'View More is not a vacancy');
  assert.ok(
    !highlights.some((r) => /\.pdf/i.test(r.officialUrl || '')),
    'highlights parser must not pick the e-paper PDF'
  );

  const empSrc = fs.readFileSync(
    path.join(__dirname, '..', 'scripts', 'collect', 'collectors', 'employmentNews.js'),
    'utf8'
  );
  assert.ok(!/extractPdf/.test(empSrc), 'employmentNews must not download PDFs');
  assert.ok(!/e-paper|epaper/i.test(empSrc) || /do not scrape/i.test(empSrc));

  assert.strictEqual(isYearStampedCalendarPdf('https://www.ibps.in/wp-content/uploads/IBPS_CALENDAR_2026-27_final.pdf'), true);
  assert.strictEqual(isYearStampedCalendarPdf('https://www.ibps.in/index.php/crp-updates/'), false);
  assert.strictEqual(
    isIbpsNoticeLink({ title: 'Apply Online for CRP-CSA-XVI', href: 'https://www.ibps.in/crp-csa' }),
    true
  );
  assert.strictEqual(
    isIbpsNoticeLink({ title: 'Request for Proposal (RFP) For Website', href: 'https://www.ibps.in/tenders' }),
    false
  );

  const openings = parseSbiOpenings(SBI_HTML, 'https://sbi.co.in/web/careers/current-openings');
  assert.ok(openings.length >= 2, `expected >=2 SBI openings, got ${openings.length}`);
  const ja = openings.find((o) => /junior associates/i.test(o.title));
  assert.ok(ja, 'Junior Associates opening missing');
  assert.strictEqual(ja.lastDate, '2026-08-31');
  assert.ok(/sbijajul26/.test(ja.officialUrl), `expected apply URL, got ${ja.officialUrl}`);
  assert.strictEqual(guessSelectionProcess('RECRUITMENT OF JUNIOR ASSOCIATES'), 'cbt');

  assert.strictEqual(isNationalApplyHost('https://www.rrbapply.gov.in/'), true);
  assert.strictEqual(isChandigarhZonalHost('https://www.rrbcdg.gov.in/employment-notices.php'), true);
  assert.strictEqual(isChandigarhZonalHost('https://www.rrbapply.gov.in/'), false);
  assert.strictEqual(
    isRrbNoticeLink({ title: 'CEN 09/2025 Group D Apply', href: 'https://www.rrbapply.gov.in/cen' }),
    true
  );

  const { collectIbps } = require('../scripts/collect/collectors/ibps');
  const { collectSbi } = require('../scripts/collect/collectors/sbi');
  const { collectRrb } = require('../scripts/collect/collectors/rrb');
  const { collectEmploymentNews } = require('../scripts/collect/collectors/employmentNews');
  assert.strictEqual(typeof collectIbps, 'function');
  assert.strictEqual(typeof collectSbi, 'function');
  assert.strictEqual(typeof collectRrb, 'function');
  assert.strictEqual(typeof collectEmploymentNews, 'function');

  const runDailySrc = fs.readFileSync(
    path.join(__dirname, '..', 'scripts', 'collect', 'runDaily.js'),
    'utf8'
  );
  assert.ok(/ibps:\s*collectIbps/.test(runDailySrc), 'runDaily SPECIAL must register ibps');
  assert.ok(/sbi:\s*collectSbi/.test(runDailySrc), 'runDaily SPECIAL must register sbi');
  assert.ok(/rrb:\s*collectRrb/.test(runDailySrc), 'runDaily SPECIAL must register rrb');

  const registry = JSON.parse(
    fs.readFileSync(path.join(__dirname, '..', 'data', 'sources', 'registry.json'), 'utf8')
  );
  const byId = Object.fromEntries(registry.sources.map((s) => [s.sourceId, s]));
  assert.ok(byId.ibps_home && byId.ibps_home.collector === 'ibps');
  assert.ok(byId.ibps_calendar && byId.ibps_calendar.collector === 'ibps');
  assert.deepStrictEqual(byId.ibps_home.listUrls, ['https://www.ibps.in/']);
  assert.deepStrictEqual(byId.ibps_calendar.listUrls, ['https://www.ibps.in/index.php/crp-updates/']);
  assert.ok(
    !byId.ibps_calendar.listUrls.some((u) => /IBPS_CALENDAR_.*\.pdf/i.test(u)),
    'ibps_calendar must not use a year-stamped PDF as listUrl'
  );
  assert.ok(byId.sbi_openings && byId.sbi_openings.collector === 'sbi');
  assert.deepStrictEqual(byId.sbi_openings.listUrls, ['https://sbi.co.in/web/careers/current-openings']);
  assert.ok(byId.sbi_apply && byId.sbi_apply.collector === 'sbi');
  assert.deepStrictEqual(byId.sbi_apply.listUrls, ['https://recruitment.sbi.bank.in/']);
  assert.ok(byId.rrb_apply && byId.rrb_apply.collector === 'rrb' && byId.rrb_apply.render === 'browser');
  assert.deepStrictEqual(byId.rrb_apply.listUrls, ['https://www.rrbapply.gov.in/']);
  assert.ok(byId.rrb_chennai && byId.rrb_chennai.collector === 'rrb');
  assert.ok(!byId.rrbcdg, 'rrbcdg must not be registered as a national source');
  assert.ok(
    !registry.sources.some((s) => (s.listUrls || []).some((u) => /rrbcdg\.gov\.in/i.test(u))),
    'no registry listUrl may point at rrbcdg.gov.in'
  );
  assert.ok(byId.employment_news.listUrls.includes('https://employmentnews.gov.in/newemp/Home.aspx'));
  assert.ok(byId.employment_news.collector === 'employmentNews');
  assert.ok(byId.becil, 'must keep existing becil source');

  console.log('pr04-collectors: all passed');
}

try {
  main();
} catch (err) {
  fail(err.message, err);
}
