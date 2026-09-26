/**
 * Daily collection orchestrator: registry → collectors → staging + collect-report
 */
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const { root, writeStaging, writeCollectReport } = require('./lib/rawStore');
const progress = require('./lib/collectProgress');
const { collectBecil } = require('./collectors/becil');
const { collectNcs } = require('./collectors/ncs');
const { collectEmploymentNews } = require('./collectors/employmentNews');
const { collectGenericCareers } = require('./collectors/genericCareers');
const { collectUpsc } = require('./collectors/upsc');
const { collectSsc } = require('./collectors/ssc');
const { collectIbps } = require('./collectors/ibps');
const { collectSbi } = require('./collectors/sbi');
const { collectRrb } = require('./collectors/rrb');
const { collectGenericPsc } = require('./collectors/genericPsc');
const { collectGenericBoard } = require('./collectors/genericBoard');

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
  genericPsc: collectGenericPsc,
  genericBoard: collectGenericBoard,
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

function buildPdfCounter(seed) {
  return {
    count: 0,
    max: Number(process.env.PDF_MAX_PER_RUN || 200),
    perSourceMax: Number(process.env.PDF_MAX_PER_SOURCE || 6),
    perSource: {},
    skipped: 0,
    sourceId: '',
    ...(seed && typeof seed === 'object' ? seed : {}),
  };
}

function findSource(sourceId) {
  const registry = loadRegistry();
  return (registry.sources || []).find((s) => s.sourceId === sourceId) || null;
}

function selectSources() {
  const registry = loadRegistry();
  const sources = (registry.sources || []).filter(shouldCollect);
  const srcArgIdx = process.argv.indexOf('--source');
  const only =
    srcArgIdx >= 0 ? process.argv[srcArgIdx + 1] : process.env.COLLECT_SOURCE || null;
  let selected = only ? sources.filter((s) => s.sourceId === only) : sources;
  if (process.argv.includes('--psu-only')) {
    selected = selected.filter((s) => s.category === 'psu_careers' || s.orgTypeDefault === 'psu');
  }
  const limIdx = process.argv.indexOf('--limit');
  if (limIdx >= 0) {
    const n = parseInt(process.argv[limIdx + 1], 10);
    if (n > 0) selected = selected.slice(0, n);
  }
  return { allEnabled: sources.length, selected };
}

async function collectOne(source, ctx) {
  ctx.pdfCounter.sourceId = source.sourceId;
  const collector = pickCollector(source);
  const out = await collector(source, ctx);
  const records = (out.records || []).map((r) => ({
    ...r,
    sector: r.sector && r.sector !== 'Other' ? r.sector : source.sector || r.sector,
    opportunityType: r.opportunityType || source.opportunityType || null,
  }));
  const stagingPath = writeStaging(source.sourceId, ctx.runId, records);
  return {
    ok: true,
    records,
    written: records.length,
    errors: out.errors || [],
    metrics: out.metrics || {},
    stagingPath,
    pdfCounter: ctx.pdfCounter,
  };
}

function persistReport(state) {
  return writeCollectReport({
    startedAt: state.startedAt,
    finishedAt: state.finishedAt || null,
    abortedAt: state.abortedAt || null,
    abortedSourceId: state.abortedSourceId || null,
    runId: state.runId,
    pdfsProcessed: state.pdfCounter.count,
    pdfsSkipped: state.pdfCounter.skipped || 0,
    pdfMax: state.pdfCounter.max,
    sourcesAttempted: state.finalSelected.length,
    sourcesOk: state.results.filter((r) => r.ok).length,
    sourcesFailed: state.results.filter((r) => !r.ok).length,
    sourcesZero: state.results.filter((r) => r.ok && !r.written).length,
    totalWritten: state.results.reduce((n, r) => n + (r.written || 0), 0),
    results: state.results,
  });
}

function spawnIsolated(source, ctx) {
  return new Promise((resolve) => {
    const child = spawn(
      process.execPath,
      [__filename, '--source-worker'],
      {
        env: {
          ...process.env,
          COLLECT_SOURCE: source.sourceId,
          COLLECT_RUN_ID: ctx.runId,
          COLLECT_AT: ctx.collectedAt,
          COLLECT_PDF_COUNTER: JSON.stringify(ctx.pdfCounter),
          COLLECT_HEADLESS: process.env.COLLECT_HEADLESS || 'true',
        },
        stdio: ['ignore', 'pipe', 'pipe'],
      }
    );
    let out = '';
    const timer = setTimeout(() => {
      try {
        child.kill('SIGTERM');
      } catch {
        /* ignore */
      }
    }, Number(process.env.COLLECT_SOURCE_TIMEOUT_MS || 120000));
    child.stdout.on('data', (buf) => {
      const s = buf.toString();
      out += s;
      process.stdout.write(s);
    });
    child.stderr.on('data', (buf) => process.stderr.write(buf));
    child.on('exit', () => {
      clearTimeout(timer);
      const match = out.match(/__COLLECT_RESULT__(\{.*\})\s*$/m);
      if (match) {
        try {
          resolve(JSON.parse(match[1]));
          return;
        } catch {
          /* fall through */
        }
      }
      resolve({
        ok: false,
        written: 0,
        errors: [{ message: 'source worker crashed or timed out' }],
        metrics: {},
        pdfCounter: ctx.pdfCounter,
      });
    });
  });
}

async function runWorker() {
  const source = findSource(process.env.COLLECT_SOURCE);
  if (!source) {
    process.stdout.write(`\n__COLLECT_RESULT__${JSON.stringify({ ok: false, error: 'unknown source' })}\n`);
    process.exit(2);
  }
  let pdfCounter = buildPdfCounter();
  try {
    if (process.env.COLLECT_PDF_COUNTER) pdfCounter = buildPdfCounter(JSON.parse(process.env.COLLECT_PDF_COUNTER));
  } catch {
    /* keep default */
  }
  const ctx = {
    runId: process.env.COLLECT_RUN_ID,
    collectedAt: process.env.COLLECT_AT,
    pdfCounter,
    sector: source.sector,
  };
  progress.setCurrent({
    sourceId: source.sourceId,
    name: source.name,
    url: (source.listUrls && source.listUrls[0]) || source.baseUrl || '',
    phase: 'source',
  });
  try {
    const out = await collectOne(source, ctx);
    process.stdout.write(`\n__COLLECT_RESULT__${JSON.stringify({
      ok: true,
      written: out.written,
      errors: (out.errors || []).slice(0, 20),
      metrics: out.metrics,
      stagingPath: out.stagingPath,
      pdfCounter: out.pdfCounter,
      findings: (out.records || []).slice(0, 40).map((r) => progress.compactFinding({
        ...r,
        pageUrl: r.sourceUrl,
        url: r.officialUrl,
        status: 'kept',
        excerpt: r.summary,
      })),
    })}\n`);
  } catch (err) {
    process.stdout.write(`\n__COLLECT_RESULT__${JSON.stringify({
      ok: false,
      written: 0,
      errors: [{ message: err.message }],
      pdfCounter,
    })}\n`);
    process.exitCode = 1;
  }
}

async function main() {
  if (process.argv.includes('--source-worker')) {
    return runWorker();
  }

  const startedAt = new Date().toISOString();
  const runId = startedAt.replace(/[:.]/g, '-');
  const collectedAt = startedAt;
  const { allEnabled, selected: finalSelected } = selectSources();
  const pdfCounter = buildPdfCounter();
  const isolate = process.env.COLLECT_ISOLATE !== 'off';

  console.log(`Daily collect run ${runId}`);
  console.log(`Will run ${finalSelected.length} collectors (of ${allEnabled} enabled scrape sources)`);

  const state = {
    startedAt,
    runId,
    collectedAt,
    finalSelected,
    pdfCounter,
    results: [],
    finishedAt: null,
    abortedAt: null,
    abortedSourceId: null,
  };

  progress.startRun({ runId, sourcesTotal: finalSelected.length, pid: process.pid });
  persistReport(state);

  const onFatal = (err) => {
    console.error(`uncaught during collect: ${err && err.message}`);
    state.abortedAt = new Date().toISOString();
    state.abortedSourceId = progress.readProgress().current?.sourceId || null;
    persistReport(state);
    progress.finishRun({ abortedAt: state.abortedAt, abortedSourceId: state.abortedSourceId });
    process.exit(1);
  };
  process.on('uncaughtException', onFatal);
  process.on('unhandledRejection', onFatal);

  for (const source of finalSelected) {
    const t0 = Date.now();
    console.log(`\n→ ${source.sourceId} (${source.name})`);
    progress.setCurrent({
      sourceId: source.sourceId,
      name: source.name,
      url: (source.listUrls && source.listUrls[0]) || source.baseUrl || '',
      phase: 'source',
    });
    let out;
    try {
      out = isolate
        ? await spawnIsolated(source, { runId, collectedAt, pdfCounter })
        : await collectOne(source, { runId, collectedAt, pdfCounter, sector: source.sector });
    } catch (err) {
      out = { ok: false, written: 0, errors: [{ message: err.message }], pdfCounter };
    }
    if (out.pdfCounter) {
      state.pdfCounter = buildPdfCounter(out.pdfCounter);
    }
    const entry = {
      sourceId: source.sourceId,
      name: source.name,
      ok: Boolean(out.ok),
      written: out.written || 0,
      errors: out.errors || [],
      metrics: out.metrics || {},
      stagingPath: out.stagingPath || null,
      durationMs: Date.now() - t0,
      findings: out.findings || (out.records || []).slice(0, 40).map((r) => progress.compactFinding({
        ...r,
        pageUrl: r.sourceUrl,
        url: r.officialUrl,
        status: 'kept',
        excerpt: r.summary,
      })),
    };
    if (entry.ok && entry.written === 0 && (!entry.errors || !entry.errors.length)) {
      entry.errors = [{ message: 'no keepable notices' }];
    }
    state.results.push(entry);
    progress.finishSource(entry);
    progress.setPdfCounts({ processed: state.pdfCounter.count, skipped: state.pdfCounter.skipped });
    persistReport(state);
    if (entry.ok) console.log(`  wrote ${entry.written} records (${entry.durationMs}ms)`);
    else console.error(`  FAILED: ${(entry.errors[0] && entry.errors[0].message) || 'unknown'}`);
    if (entry.errors.length) console.log(`  warnings: ${entry.errors.length}`);
  }

  state.finishedAt = new Date().toISOString();
  const reportPath = persistReport(state);
  progress.finishRun();
  console.log(`\nCollect report → ${reportPath}`);
  console.log(JSON.stringify({
    totalWritten: state.results.reduce((n, r) => n + (r.written || 0), 0),
    sourcesOk: state.results.filter((r) => r.ok).length,
    sourcesFailed: state.results.filter((r) => !r.ok).length,
    pdfs: state.pdfCounter.count,
  }, null, 2));

  const hardFailures = state.results.filter((r) => !r.ok).length;
  if (finalSelected.length > 0 && hardFailures === finalSelected.length) process.exitCode = 1;
}

main().catch((err) => {
  console.error(err);
  try {
    progress.finishRun({ abortedAt: new Date().toISOString() });
  } catch {
    /* ignore */
  }
  process.exit(1);
});
