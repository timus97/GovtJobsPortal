/**
 * Daily collection orchestrator: registry → collectors → staging + collect-report
 */
const fs = require('fs');
const path = require('path');
const { root, writeStaging, writeCollectReport } = require('./lib/rawStore');
const { collectBecil } = require('./collectors/becil');
const { collectNcs } = require('./collectors/ncs');
const { collectEmploymentNews } = require('./collectors/employmentNews');
const { collectGenericCareers } = require('./collectors/genericCareers');
const { collectUpsc } = require('./collectors/upsc');
const { collectSsc } = require('./collectors/ssc');
const { collectIbps } = require('./collectors/ibps');
const { collectSbi } = require('./collectors/sbi');
const { collectRrb } = require('./collectors/rrb');

const SPECIAL = {
  becil: collectBecil,
  ncs_gov: collectNcs,
  employment_news: collectEmploymentNews,
  employmentNews: collectEmploymentNews,
  upsc: collectUpsc,
  ssc: collectSsc,
  ibps: collectIbps,
  sbi: collectSbi,
  rrb: collectRrb,
};

function loadRegistry() {
  const p = path.join(root, 'data', 'sources', 'registry.json');
  return JSON.parse(fs.readFileSync(p, 'utf8'));
}

function shouldCollect(source) {
  if (!source.enabled) return false;
  if (source.method === 'manual') return false;
  return ['html_scrape', 'browser_scrape', 'pdf_watch'].includes(source.method);
}

function pickCollector(source) {
  const key = source.collector || source.sourceId;
  if (SPECIAL[key]) return SPECIAL[key];
  if (SPECIAL[source.sourceId]) return SPECIAL[source.sourceId];
  if (source.method === 'html_scrape' || source.method === 'browser_scrape' || source.render === 'browser') {
    return collectGenericCareers;
  }
  return collectGenericCareers;
}

async function main() {
  const startedAt = new Date().toISOString();
  const runId = startedAt.replace(/[:.]/g, '-');
  const collectedAt = startedAt;
  const pdfMax = Number(process.env.PDF_MAX_PER_RUN || 25);
  const pdfCounter = { count: 0, max: pdfMax };

  const registry = loadRegistry();
  const sources = (registry.sources || []).filter(shouldCollect);

  // Optional CLI filter: node runDaily.js --source becil
  const srcArgIdx = process.argv.indexOf('--source');
  const only =
    srcArgIdx >= 0 ? process.argv[srcArgIdx + 1] : process.env.COLLECT_SOURCE || null;
  const selected = only ? sources.filter((s) => s.sourceId === only) : sources;

  // Optional: only PSU sources
  const psuOnly = process.argv.includes('--psu-only');
  let finalSelected = selected;
  if (psuOnly) {
    finalSelected = selected.filter((s) => s.category === 'psu_careers' || s.orgTypeDefault === 'psu');
  }

  // Cap for smoke tests: --limit 20
  const limIdx = process.argv.indexOf('--limit');
  if (limIdx >= 0) {
    const n = parseInt(process.argv[limIdx + 1], 10);
    if (n > 0) finalSelected = finalSelected.slice(0, n);
  }

  console.log(`Daily collect run ${runId}`);
  console.log(`Will run ${finalSelected.length} collectors (of ${selected.length} enabled scrape sources)`);

  const results = [];
  let hardFailures = 0;

  for (const source of finalSelected) {
    const t0 = Date.now();
    const collector = pickCollector(source);
    console.log(`\n→ ${source.sourceId} (${source.name})`);
    try {
      const out = await collector(source, { runId, collectedAt, pdfCounter, sector: source.sector });
      const records = (out.records || []).map((r) => ({
        ...r,
        sector: r.sector && r.sector !== 'Other' ? r.sector : source.sector || r.sector,
      }));
      const stagingPath = writeStaging(source.sourceId, runId, records);
      const entry = {
        sourceId: source.sourceId,
        name: source.name,
        ok: true,
        written: records.length,
        errors: out.errors || [],
        metrics: out.metrics || {},
        stagingPath,
        durationMs: Date.now() - t0,
      };
      results.push(entry);
      console.log(`  wrote ${records.length} records (${entry.durationMs}ms)`);
      if (entry.errors.length) console.log(`  warnings: ${entry.errors.length}`);
    } catch (err) {
      hardFailures += 1;
      results.push({
        sourceId: source.sourceId,
        name: source.name,
        ok: false,
        written: 0,
        errors: [{ message: err.message }],
        metrics: {},
        durationMs: Date.now() - t0,
      });
      console.error(`  FAILED: ${err.message}`);
    }
  }

  const finishedAt = new Date().toISOString();
  const report = {
    startedAt,
    finishedAt,
    runId,
    pdfsProcessed: pdfCounter.count,
    pdfMax,
    sourcesAttempted: finalSelected.length,
    sourcesOk: results.filter((r) => r.ok).length,
    sourcesFailed: results.filter((r) => !r.ok).length,
    totalWritten: results.reduce((n, r) => n + (r.written || 0), 0),
    results,
  };

  const reportPath = writeCollectReport(report);
  console.log(`\nCollect report → ${reportPath}`);
  console.log(JSON.stringify({ totalWritten: report.totalWritten, sourcesOk: report.sourcesOk, sourcesFailed: report.sourcesFailed, pdfs: report.pdfsProcessed }, null, 2));

  // Exit 1 only if every attempted source hard-failed
  if (selected.length > 0 && hardFailures === selected.length) {
    process.exitCode = 1;
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
