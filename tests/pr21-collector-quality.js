/**
 * Collector URL quality + PDF notice parsing. Run: node tests/pr21-collector-quality.js
 * No live network.
 */
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const {
  scoreJobLink,
  isKeepableJobLink,
  filterJobLinks,
  isHomepageUrl,
  isRecruitmentPdfText,
  isGarbageJob,
} = require('../scripts/collect/lib/jobLinkQuality');
const { parsePdfJobFields } = require('../scripts/collect/lib/pdfExtract');
const { extractLinks } = require('../scripts/collect/lib/htmlLinks');
const { findLastDateHint } = require('../scripts/collect/lib/dates');
const { toStagingRecord } = require('../scripts/collect/lib/toStaging');
const { collectGenericBoard } = require('../scripts/collect/collectors/genericBoard');
const { enrichRecord } = require('../scripts/process/buildJobs');

function fail(message, err) {
  console.error(`FAIL: ${message}`);
  if (err) console.error(err.message || err);
  process.exit(1);
}

try {
  assert.strictEqual(isHomepageUrl('https://www.becil.com/'), true);
  assert.strictEqual(isHomepageUrl('https://ssc.gov.in/'), true);
  assert.strictEqual(isHomepageUrl('https://ssc.gov.in/apply/cgl-2026'), false);

  assert.ok(!isKeepableJobLink({ title: 'Login', href: 'https://www.ncs.gov.in/login' }));
  assert.ok(!isKeepableJobLink({ title: 'Post New Jobs', href: 'https://betacloud.ncs.gov.in/home/job-post' }));
  assert.ok(!isKeepableJobLink({ title: 'Tender Notification', href: 'https://tenders.bhel.com/tenders' }));
  assert.ok(!isKeepableJobLink({ title: 'Working at BHEL', href: 'https://careers.bhel.in/' }));
  assert.ok(!isKeepableJobLink({
    title: 'Ingots',
    href: 'https://nalcoindia.com/wp-content/uploads/2019/04/INGOT-Specification.pdf',
    isPdf: true,
  }));
  assert.ok(!isKeepableJobLink({
    title: 'Skip to main content',
    href: 'https://iocl.com/latest-job-opening',
  }));
  assert.ok(!isKeepableJobLink({
    title: 'हिंदी',
    href: 'https://iocl.com/Language?ReturnUrl=%2Flatest-job-opening&handler=Return',
  }));
  assert.ok(!isKeepableJobLink({
    title: 'Updated On 20-12-2023',
    href: 'https://www.aai.aero/en/careers/recruitment/release/396317',
  }));
  assert.ok(!isKeepableJobLink({
    title: 'NIT_SECI_furniture',
    href: 'https://www.seci.co.in/uploads/NIT_123.pdf',
    isPdf: true,
  }));
  assert.ok(
    isKeepableJobLink({
      title: 'Advertisement for Walk-in Interview — Medical Officer',
      href: 'https://esic.gov.in/recruitment/walkin-mo-2026.pdf',
      isPdf: true,
    })
  );

  const html = `
    <a href="/login">Login</a>
    <a href="https://example.gov.in/">Home</a>
    <a href="https://example.gov.in/tender/contract-award">Contract Award</a>
    <a href="https://example.gov.in/recruitment/advt-42-2026.pdf">Notification for Junior Engineer</a>
    <a href="https://example.gov.in/careers">Careers</a>
  `;
  const links = extractLinks(html, 'https://example.gov.in/careers', { jobLikeOnly: true, limit: 20 });
  const kept = filterJobLinks(links);
  assert.ok(kept.some((l) => /advt-42-2026\.pdf/i.test(l.href)));
  assert.ok(!kept.some((l) => /login|contract-award/i.test(l.href)));

  const noticeText = `
    GOVERNMENT OF INDIA
    Advertisement for engagement of Consultant (IT)
    Applications are invited for walk-in interview
    Last date for receipt of applications: 30.09.2026
    No. of posts: 12
  `;
  assert.strictEqual(isRecruitmentPdfText(noticeText), true);
  const fields = parsePdfJobFields(noticeText);
  assert.ok(fields.isJobNotice);
  assert.strictEqual(fields.vacancies, 12);
  assert.ok(fields.lastDate === '2026-09-30');

  const tenderText = 'Notice Inviting Tender (NIT) for supply of furniture. Bid document.';
  assert.strictEqual(isRecruitmentPdfText(tenderText), false);
  assert.strictEqual(parsePdfJobFields(tenderText).isJobNotice, false);

  const manualText = 'NCS employer portal user manual. Registration flow for ISF login.';
  assert.strictEqual(isRecruitmentPdfText(manualText), false);

  assert.ok(
    isGarbageJob({
      title: 'Post New Jobs',
      officialUrl: 'https://betacloud.ncs.gov.in/home/job-post',
      sourceId: 'ncs_gov',
    }).garbage
  );
  assert.ok(
    isGarbageJob({
      title: 'Office Assistant (Contract)',
      officialUrl: 'https://www.becil.com/',
      sourceId: 'seed_manual',
      lastDate: '2026-07-14',
    }).garbage,
    'seed homepage must be garbage'
  );
  assert.ok(
    !isGarbageJob({
      title: 'Office Assistant (Contract)',
      officialUrl: 'https://www.becil.com/recruitment/office-assistant-contract',
      sourceId: 'seed_manual',
      lastDate: '2026-07-14',
    }).garbage
  );

  assert.strictEqual(findLastDateHint('Updated On 20-12-2023'), null);
  assert.strictEqual(findLastDateHint('Last date to apply: 20-12-2023'), '2023-12-20');

  const cleaned = toStagingRecord(
    {
      title: 'Engagement of Technician ( | PDF | 1.1 KB | English)',
      href: 'https://grse.in/career/advt-technician-2026.pdf',
    },
    { sourceId: 'psu_grse', name: 'GRSE Careers', orgTypeDefault: 'psu' }
  );
  assert.ok(!/\|\s*PDF/i.test(cleaned.title));
  assert.ok(!/1\.1 KB/i.test(cleaned.title));

  assert.strictEqual(typeof collectGenericBoard, 'function');
  const boardSrc = fs.readFileSync(
    path.join(__dirname, '..', 'scripts', 'collect', 'collectors', 'genericBoard.js'),
    'utf8'
  );
  assert.ok(/dedupeByUrl/.test(boardSrc), 'genericBoard must import dedupeByUrl');

  const noticeSrc = fs.readFileSync(
    path.join(__dirname, '..', 'scripts', 'collect', 'lib', 'collectNotices.js'),
    'utf8'
  );
  assert.ok(/pdf_skipped|pdf_parse_failed/.test(noticeSrc));
  assert.ok(/if \(!pdf\.ok\)/.test(noticeSrc));

  const dateless = enrichRecord(
    {
      title: 'Advertisement for Junior Engineer',
      organization: 'Test PSU',
      orgType: 'psu',
      officialUrl: 'https://example.gov.in/recruitment/je-2026.pdf',
      sourceId: 'psu_test',
      sourceName: 'Test',
      sourceUrl: 'https://example.gov.in/recruitment',
      collectorVersion: 'scrape-v1',
      selectionProcess: 'interview_only',
      hasExam: false,
      summary: 'Scraped vacancy',
    },
    { aliases: {} },
    new Date().toISOString()
  );
  assert.ok(dateless.quarantine, 'dateless scrape must quarantine');
  assert.ok(/dateless|needs_review/.test(dateless.reason));

  console.log('PASS pr21-collector-quality');
} catch (err) {
  fail(err.message, err);
}
