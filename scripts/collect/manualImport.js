/**
 * Import data/seed/manual_jobs.csv (optional) into staging for the process pipeline.
 * CSV headers: title,organization,orgType,sector,location,vacancies,qualification,
 * experience,salary,selectionProcess,applicationMode,notificationDate,lastDate,
 * walkInDate,officialUrl,summary,eligibility,processSteps
 *
 * eligibility and processSteps: use | to separate bullets.
 */
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..', '..');
const csvPath = path.join(root, 'data', 'seed', 'manual_jobs.csv');
const outDir = path.join(root, 'data', 'staging', 'manual');

function parseCsv(text) {
  const lines = text.split(/\r?\n/).filter((l) => l.trim());
  if (lines.length < 2) return [];
  const headers = splitCsvLine(lines[0]);
  return lines.slice(1).map((line) => {
    const cols = splitCsvLine(line);
    const row = {};
    headers.forEach((h, i) => {
      row[h.trim()] = (cols[i] || '').trim();
    });
    return row;
  });
}

function splitCsvLine(line) {
  const out = [];
  let cur = '';
  let inQ = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (c === '"') {
      if (inQ && line[i + 1] === '"') {
        cur += '"';
        i++;
      } else inQ = !inQ;
    } else if (c === ',' && !inQ) {
      out.push(cur);
      cur = '';
    } else cur += c;
  }
  out.push(cur);
  return out;
}

function main() {
  if (!fs.existsSync(csvPath)) {
    console.log(`No CSV at ${csvPath} — nothing to import.`);
    console.log('Create manual_jobs.csv with required headers to batch-add jobs.');
    process.exit(0);
  }

  const rows = parseCsv(fs.readFileSync(csvPath, 'utf8'));
  const now = new Date().toISOString();
  const records = rows
    .filter((r) => r.title && r.organization && r.officialUrl)
    .map((r) => ({
      title: r.title,
      organization: r.organization,
      orgType: r.orgType || 'central',
      sector: r.sector || 'Other',
      location: r.location || 'All India',
      vacancies: r.vacancies ? Number(r.vacancies) : null,
      qualification: r.qualification || null,
      experience: r.experience || null,
      salary: r.salary || null,
      selectionProcess: r.selectionProcess || 'interview_only',
      hasExam: false,
      applicationMode: r.applicationMode || 'online',
      notificationDate: r.notificationDate || null,
      lastDate: r.lastDate || null,
      walkInDate: r.walkInDate || null,
      officialUrl: r.officialUrl,
      sourceId: 'seed_manual',
      sourceName: 'Manual curator seed',
      sourceUrl: r.officialUrl,
      summary: r.summary || '',
      eligibility: r.eligibility ? r.eligibility.split('|').map((s) => s.trim()).filter(Boolean) : [],
      processSteps: r.processSteps
        ? r.processSteps.split('|').map((s) => s.trim()).filter(Boolean)
        : [],
      collectedAt: now,
      collectorVersion: 'manual-csv-v1',
    }));

  fs.mkdirSync(outDir, { recursive: true });
  const outFile = path.join(outDir, `${now.slice(0, 10)}.json`);
  fs.writeFileSync(outFile, JSON.stringify(records, null, 2), 'utf8');
  console.log(`Imported ${records.length} rows → ${outFile}`);
  console.log('Run: npm run process');
}

main();
