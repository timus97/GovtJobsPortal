/**
 * PR03 calendar parser + collector wiring smoke checks.
 * Run: node tests/pr03-collectors.js
 * No live network.
 */
const assert = require('assert');
const path = require('path');
const fs = require('fs');
const {
  parseCalendarHtml,
  parseCalendarText,
  parseCalendarPdfBuffer,
  findCalendarPdfLinks,
  looksBlocked,
  isFullIsoDate,
  officialUrlForRow,
} = require(path.join(__dirname, '..', 'scripts', 'collect', 'lib', 'calendarPdf'));

const HTML_TABLE = `
<html><body>
<table>
  <thead>
    <tr>
      <th>Name of Examination</th>
      <th>Date of Notification</th>
      <th>Last Date for Receipt of Applications</th>
      <th>Date of commencement of Exam</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><a href="/examinations/cse-prelims-2026">Civil Services (Preliminary) Examination, 2026</a></td>
      <td>22.01.2026</td>
      <td>11.02.2026</td>
      <td>24.05.2026</td>
    </tr>
    <tr>
      <td>Indian Forest Service (Preliminary) Examination, 2026</td>
      <td>22.01.2026</td>
      <td>11.02.2026</td>
      <td>24.05.2026</td>
    </tr>
    <tr>
      <td>Combined Geo-Scientist Examination, 2026</td>
      <td>To be notified</td>
      <td>—</td>
      <td>Sept-Oct, 2026</td>
    </tr>
  </tbody>
</table>
<p><a href="/sites/default/files/exam_calendar_2026.pdf">Annual Calendar 2026 (PDF)</a></p>
</body></html>
`;

const SSC_MONTH_TABLE = `
<table>
  <tr>
    <th>Name of Examination</th>
    <th>Tentative Date of Notification</th>
    <th>Tentative Date of Completion of Application Form</th>
    <th>Tentative Month of Exam</th>
  </tr>
  <tr>
    <td>Combined Graduate Level Examination, 2026</td>
    <td>June, 2026</td>
    <td>July, 2026</td>
    <td>Sept-Oct, 2026</td>
  </tr>
</table>
`;

const TEXT_FIXTURE = `
UNION PUBLIC SERVICE COMMISSION
Annual Calendar
Name of Examination    Date of Notification    Last Date for Receipt of Applications    Date of commencement of Exam
Civil Services (Preliminary) Examination, 2026    22.01.2026    11.02.2026    24.05.2026
NDA & NA Examination (I), 2026    11.12.2025    31.12.2025    12.04.2026
Page 1
`;

function fail(message, err) {
  console.error(`FAIL: ${message}`);
  if (err) console.error(err.message || err);
  process.exit(1);
}

async function main() {
  const pageUrl = 'https://upsc.gov.in/examinations/exam-calendar';
  const htmlRows = parseCalendarHtml(HTML_TABLE, pageUrl);
  assert.ok(htmlRows.length >= 3, `expected >=3 HTML rows, got ${htmlRows.length}`);

  const cse = htmlRows.find((r) => /civil services/i.test(r.name));
  assert.ok(cse, 'Civil Services row missing');
  assert.strictEqual(cse.notificationDate, '2026-01-22');
  assert.strictEqual(cse.lastDate, '2026-02-11');
  assert.strictEqual(cse.examDate, '2026-05-24');
  assert.ok(
    /cse-prelims-2026/.test(cse.officialUrl),
    `expected officialUrl from table link, got ${cse.officialUrl}`
  );
  assert.ok(isFullIsoDate(cse.lastDate));

  const geo = htmlRows.find((r) => /geo-scientist/i.test(r.name));
  assert.ok(geo, 'Geo-Scientist row missing');
  assert.strictEqual(geo.notificationDate, null);
  assert.strictEqual(geo.lastDate, null);
  assert.strictEqual(geo.examDate, '2026-09');

  const monthRows = parseCalendarHtml(SSC_MONTH_TABLE, 'https://ssc.gov.in/for-candidates/examination-calendar');
  assert.strictEqual(monthRows.length, 1, `expected 1 SSC month row, got ${monthRows.length}`);
  assert.ok(/combined graduate level/i.test(monthRows[0].name));
  assert.strictEqual(monthRows[0].notificationDate, '2026-06');
  assert.strictEqual(monthRows[0].lastDate, '2026-07');
  assert.strictEqual(monthRows[0].examDate, '2026-09');

  const textRows = parseCalendarText(TEXT_FIXTURE, { pageUrl });
  assert.ok(textRows.length >= 2, `expected >=2 text rows, got ${textRows.length}`);
  const nda = textRows.find((r) => /nda/i.test(r.name));
  assert.ok(nda, 'NDA text row missing');
  assert.strictEqual(nda.notificationDate, '2025-12-11');
  assert.strictEqual(nda.lastDate, '2025-12-31');
  assert.strictEqual(nda.examDate, '2026-04-12');

  const emptyHtml = parseCalendarHtml('', pageUrl);
  assert.deepStrictEqual(emptyHtml, []);
  const emptyText = parseCalendarText('');
  assert.deepStrictEqual(emptyText, []);

  const pdfFail = await parseCalendarPdfBuffer(Buffer.from('not-a-pdf'), { pageUrl });
  assert.strictEqual(pdfFail.ok, false);
  assert.deepStrictEqual(pdfFail.rows, []);
  assert.ok(pdfFail.error, 'expected parse error for garbage PDF');

  const pdfLinks = findCalendarPdfLinks(HTML_TABLE, pageUrl);
  assert.ok(
    pdfLinks.some((l) => /\.pdf/i.test(l.href)),
    'expected calendar PDF link from fixture'
  );

  assert.strictEqual(looksBlocked('<div class="g-recaptcha"></div>'), true);
  assert.strictEqual(looksBlocked('<html><p>Civil Services Examination calendar</p></html>'), false);

  const frag = officialUrlForRow({ name: 'Civil Services (Preliminary) Examination, 2026' }, pageUrl);
  assert.ok(frag.startsWith(pageUrl + '#'));

  const { collectUpsc } = require(path.join(__dirname, '..', 'scripts', 'collect', 'collectors', 'upsc'));
  const { collectSsc } = require(path.join(__dirname, '..', 'scripts', 'collect', 'collectors', 'ssc'));
  assert.strictEqual(typeof collectUpsc, 'function');
  assert.strictEqual(typeof collectSsc, 'function');

  const runDailySrc = fs.readFileSync(
    path.join(__dirname, '..', 'scripts', 'collect', 'runDaily.js'),
    'utf8'
  );
  assert.ok(/upsc:\s*collectUpsc/.test(runDailySrc), 'runDaily SPECIAL must register upsc');
  assert.ok(/ssc:\s*collectSsc/.test(runDailySrc), 'runDaily SPECIAL must register ssc');

  const registry = JSON.parse(
    fs.readFileSync(path.join(__dirname, '..', 'data', 'sources', 'registry.json'), 'utf8')
  );
  assert.ok(registry.sources.length >= 269, `expected >= 269 sources, got ${registry.sources.length}`);
  const byId = Object.fromEntries(registry.sources.map((s) => [s.sourceId, s]));
  assert.ok(!byId.upsc && !byId.ssc, 'combined upsc/ssc sourceIds must be removed');
  const upscCalendar = byId.upsc_calendar;
  const upscActive = byId.upsc_active;
  const upscOnline = byId.upsc_online;
  const sscCalendar = byId.ssc_calendar;
  const sscNotices = byId.ssc_notices;
  assert.ok(upscCalendar && upscCalendar.collector === 'upsc');
  assert.ok(upscActive && upscActive.collector === 'upsc');
  assert.ok(upscOnline && upscOnline.collector === 'upsc');
  assert.ok(sscCalendar && sscCalendar.collector === 'ssc' && sscCalendar.render === 'browser');
  assert.ok(sscNotices && sscNotices.collector === 'ssc' && sscNotices.render === 'browser');
  assert.deepStrictEqual(upscCalendar.listUrls, ['https://upsc.gov.in/examinations/exam-calendar']);
  assert.deepStrictEqual(upscActive.listUrls, ['https://upsc.gov.in/examinations/active-exams']);
  assert.deepStrictEqual(upscOnline.listUrls, ['https://upsconline.nic.in/']);
  assert.ok(sscCalendar.listUrls.includes('https://ssc.gov.in/for-candidates/examination-calendar'));
  assert.ok(sscCalendar.listUrls.includes('https://ssc.gov.in/home/ssc-calendar'));
  assert.deepStrictEqual(sscNotices.listUrls, ['https://ssc.gov.in/home/notice-board']);
  assert.ok(!/www\.upsc\.gov\.in/.test(JSON.stringify(upscCalendar.listUrls)));
  assert.ok(!/www\.upsc\.gov\.in/.test(JSON.stringify(upscActive.listUrls)));
  assert.ok(registry.sources.some((s) => s.sourceId === 'becil'), 'must keep existing becil source');

  console.log('pr03-collectors: all passed');
  process.exit(0);
}

main().catch((err) => fail(err.message, err));
