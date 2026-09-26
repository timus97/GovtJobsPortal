const fs = require('fs');
const path = require('path');
const { root, ensureDir } = require('./rawStore');

const PROGRESS_PATH = path.join(root, 'data', 'processed', 'collect-progress.json');

function nowIso() {
  return new Date().toISOString();
}

function emptyProgress() {
  return {
    running: false,
    runId: null,
    startedAt: null,
    finishedAt: null,
    abortedAt: null,
    abortedSourceId: null,
    sourcesTotal: 0,
    sourcesDone: 0,
    sourcesOk: 0,
    sourcesFailed: 0,
    sourcesZero: 0,
    recordsWritten: 0,
    pdfsProcessed: 0,
    pdfsSkipped: 0,
    current: null,
    lastFinished: null,
    lastFinding: null,
    findings: [],
    sourcesLog: [],
    pid: null,
  };
}

const FINDINGS_CAP = 250;
const SOURCE_LOG_CAP = 50;
const SOURCE_FINDINGS_CAP = 40;

function compactFinding(raw = {}) {
  const url = raw.url || raw.officialUrl || raw.href || '';
  const kind =
    raw.kind ||
    (/pdf/i.test(String(raw.collectorVersion || '')) || /\.pdf(\?|#|$)/i.test(url) ? 'pdf' : 'html');
  return {
    at: raw.at || nowIso(),
    sourceId: raw.sourceId || null,
    sourceName: raw.sourceName || raw.name || null,
    pageUrl: raw.pageUrl || raw.sourceUrl || null,
    url,
    kind,
    status: raw.status === 'skipped' ? 'skipped' : 'kept',
    reason: raw.reason || null,
    title: String(raw.title || '').replace(/\s+/g, ' ').trim().slice(0, 180),
    organization: raw.organization || null,
    lastDate: raw.lastDate || null,
    vacancies: raw.vacancies ?? null,
    selectionProcess: raw.selectionProcess || null,
    excerpt: String(raw.excerpt || raw.summary || '')
      .replace(/\s+/g, ' ')
      .trim()
      .slice(0, 220),
  };
}

function findingKey(item) {
  return `${item.sourceId || ''}|${item.status}|${item.url || ''}|${item.title || ''}`;
}

function mergeFindings(existing, incoming) {
  const out = [...(existing || [])];
  const seen = new Set(out.map(findingKey));
  for (const item of incoming || []) {
    const row = compactFinding(item);
    const key = findingKey(row);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(row);
  }
  return out.slice(-FINDINGS_CAP);
}

function noteFindings(items) {
  const list = (Array.isArray(items) ? items : [items]).filter(Boolean);
  if (!list.length) return readProgress();
  const prev = readProgress();
  if (!prev.running) return prev;
  const findings = mergeFindings(prev.findings, list);
  return writeProgress({
    ...prev,
    lastFinding: compactFinding(list[list.length - 1]),
    findings,
  });
}

function noteFinding(item) {
  return noteFindings([item]);
}

function readProgress() {
  try {
    return { ...emptyProgress(), ...JSON.parse(fs.readFileSync(PROGRESS_PATH, 'utf8')) };
  } catch {
    return emptyProgress();
  }
}

function writeProgress(data) {
  ensureDir(path.dirname(PROGRESS_PATH));
  const tmp = `${PROGRESS_PATH}.${process.pid}.tmp`;
  fs.writeFileSync(tmp, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  fs.renameSync(tmp, PROGRESS_PATH);
  return data;
}

function startRun({ runId, sourcesTotal, pid }) {
  return writeProgress({
    ...emptyProgress(),
    running: true,
    runId,
    startedAt: nowIso(),
    sourcesTotal,
    pid: pid || process.pid,
  });
}

function setCurrent({ sourceId, name, url, phase }) {
  const prev = readProgress();
  if (!prev.running) return prev;
  return writeProgress({
    ...prev,
    current: {
      sourceId: sourceId || prev.current?.sourceId || null,
      name: name || prev.current?.name || null,
      url: url || null,
      phase: phase || prev.current?.phase || 'fetch',
      at: nowIso(),
    },
  });
}

function finishSource(entry) {
  const prev = readProgress();
  const written = Number(entry.written || 0);
  if (entry.findings && entry.findings.length) {
    prev.findings = mergeFindings(prev.findings, entry.findings);
    prev.lastFinding = compactFinding(entry.findings[entry.findings.length - 1]);
  }
  const mine = (prev.findings || []).filter((f) => f.sourceId === entry.sourceId);
  const lastFinished = {
    sourceId: entry.sourceId,
    name: entry.name,
    ok: entry.ok,
    written,
    durationMs: entry.durationMs,
    url: prev.current?.url || null,
    kept: mine.filter((f) => f.status === 'kept').length,
    skipped: mine.filter((f) => f.status === 'skipped').length,
    at: nowIso(),
  };
  const sourceRow = {
    ...lastFinished,
    errors: (entry.errors || []).slice(0, 5).map((e) => ({
      url: e.url || null,
      message: e.message || String(e),
    })),
    findings: mine.slice(-SOURCE_FINDINGS_CAP),
  };
  return writeProgress({
    ...prev,
    sourcesDone: (prev.sourcesDone || 0) + 1,
    sourcesOk: (prev.sourcesOk || 0) + (entry.ok ? 1 : 0),
    sourcesFailed: (prev.sourcesFailed || 0) + (entry.ok ? 0 : 1),
    sourcesZero: (prev.sourcesZero || 0) + (entry.ok && written === 0 ? 1 : 0),
    recordsWritten: (prev.recordsWritten || 0) + written,
    lastFinished,
    sourcesLog: [sourceRow, ...(prev.sourcesLog || []).filter((s) => s.sourceId !== entry.sourceId)].slice(
      0,
      SOURCE_LOG_CAP
    ),
  });
}

function setPdfCounts({ processed, skipped }) {
  const prev = readProgress();
  return writeProgress({
    ...prev,
    pdfsProcessed: processed != null ? processed : prev.pdfsProcessed,
    pdfsSkipped: skipped != null ? skipped : prev.pdfsSkipped,
  });
}

function finishRun(extra = {}) {
  const prev = readProgress();
  return writeProgress({
    ...prev,
    running: false,
    finishedAt: extra.abortedAt ? prev.finishedAt : nowIso(),
    abortedAt: extra.abortedAt || null,
    abortedSourceId: extra.abortedSourceId || null,
    current: extra.abortedAt
      ? prev.current
      : null,
    ...extra.patch,
  });
}

module.exports = {
  PROGRESS_PATH,
  readProgress,
  writeProgress,
  startRun,
  setCurrent,
  finishSource,
  setPdfCounts,
  finishRun,
  emptyProgress,
  compactFinding,
  noteFinding,
  noteFindings,
};
