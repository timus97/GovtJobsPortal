const path = require('path');
const { classifySelectionText } = require(path.join(
  __dirname,
  '..',
  '..',
  '..',
  'shared',
  'jobSchema'
));
const { findLastDateHint } = require('./dates');

function toStagingRecord(item, source, meta = {}) {
  const title = String(item.title || '').replace(/\s+/g, ' ').trim();
  const officialUrl = item.href || item.officialUrl;
  const summary = String(item.summary || item.text || title).slice(0, 800);
  const blob = `${title} ${summary} ${item.extraText || ''} ${item.pdfText || ''}`;

  const classified = classifySelectionText(blob);
  let selectionProcess = item.selectionProcess || null;
  let hasExam = item.hasExam;
  let needsReview = false;

  if (classified.hasExam === true || hasExam === true) {
    hasExam = true;
  } else if (classified.selectionProcess) {
    selectionProcess = classified.selectionProcess;
    hasExam = false;
  } else if (/walk[\s-]?in/i.test(blob)) {
    selectionProcess = 'walk_in';
    hasExam = false;
  } else if (/apprentice/i.test(blob)) {
    selectionProcess = 'apprenticeship';
    hasExam = false;
  } else if (/consultant|contract/i.test(blob)) {
    selectionProcess = 'contract_interview';
    hasExam = false;
  } else if (/interview|shortlist/i.test(blob)) {
    selectionProcess = 'interview_only';
    hasExam = false;
  } else if (/direct\s+recruitment/i.test(blob)) {
    selectionProcess = 'direct_recruitment';
    hasExam = false;
  } else {
    needsReview = true;
    selectionProcess = selectionProcess || 'interview_only';
    hasExam = false;
  }

  const lastDate =
    item.lastDate || findLastDateHint(blob) || findLastDateHint(title) || null;

  const organization =
    item.organization ||
    (source.name || '').replace(/\s+Careers$/i, '').trim() ||
    source.sourceId;

  return {
    title: title || 'Untitled vacancy',
    organization,
    orgType: item.orgType || source.orgTypeDefault || 'central',
    sector: item.sector || meta.sector || source.sector || 'Other',
    location: item.location || 'All India',
    vacancies: item.vacancies ?? null,
    qualification: item.qualification || null,
    experience: item.experience || null,
    salary: item.salary || null,
    selectionProcess,
    hasExam: hasExam === true,
    applicationMode: /walk[\s-]?in/i.test(blob) ? 'walk_in' : 'online',
    notificationDate: item.notificationDate || null,
    lastDate,
    walkInDate: item.walkInDate || null,
    officialUrl,
    sourceId: source.sourceId,
    sourceName: source.name,
    sourceUrl: item.sourceUrl || meta.listUrl || source.baseUrl || officialUrl,
    summary:
      summary ||
      `Scraped vacancy from ${source.name}. Verify selection process on the official page.`,
    eligibility: item.eligibility || [],
    processSteps: item.processSteps || [
      'Open the official notification link',
      'Confirm eligibility and that selection has no written exam/CBT',
      'Apply only through the official channel before the last date',
    ],
    documentsRequired: item.documentsRequired || [],
    needsReview: Boolean(needsReview || !lastDate || hasExam === true),
    pdfHash: item.pdfHash || null,
    collectedAt: meta.collectedAt || new Date().toISOString(),
    collectorVersion: meta.collectorVersion || 'scrape-v1',
  };
}

function dedupeByUrl(records) {
  const map = new Map();
  for (const r of records) {
    const key = (r.officialUrl || '').toLowerCase().split('?')[0];
    if (!key) continue;
    if (!map.has(key)) map.set(key, r);
  }
  return [...map.values()];
}

module.exports = { toStagingRecord, dedupeByUrl };
