/**
 * Rebuild data/processed/collect-report.json from staging folders + registry.
 * Use when a long scrape wrote staging files but crashed before the final report.
 */
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..', '..');
const stagingDir = path.join(root, 'data', 'staging');
const registryPath = path.join(root, 'data', 'sources', 'registry.json');
const outPath = path.join(root, 'data', 'processed', 'collect-report.json');

function listStagingBySource() {
  const map = {};
  if (!fs.existsSync(stagingDir)) return map;
  for (const sourceId of fs.readdirSync(stagingDir)) {
    const dir = path.join(stagingDir, sourceId);
    if (!fs.statSync(dir).isDirectory()) continue;
    const files = fs
      .readdirSync(dir)
      .filter((f) => f.endsWith('.json'))
      .map((f) => {
        const full = path.join(dir, f);
        const st = fs.statSync(full);
        return { file: f, full, mtime: st.mtimeMs };
      })
      .sort((a, b) => b.mtime - a.mtime);
    if (!files.length) continue;
    const latest = files[0];
    let records = [];
    try {
      const data = JSON.parse(fs.readFileSync(latest.full, 'utf8'));
      records = Array.isArray(data) ? data : data.records || [];
    } catch {
      records = [];
    }
    map[sourceId] = {
      written: records.length,
      stagingPath: latest.full,
      runFile: latest.file,
      mtime: latest.mtime,
    };
  }
  return map;
}

function main() {
  const registry = JSON.parse(fs.readFileSync(registryPath, 'utf8'));
  const staging = listStagingBySource();
  const now = new Date().toISOString();

  const results = (registry.sources || [])
    .filter((s) => s.enabled && s.method !== 'manual')
    .map((s) => {
      const st = staging[s.sourceId];
      if (!st) {
        return {
          sourceId: s.sourceId,
          name: s.name,
          ok: true,
          written: 0,
          errors: [],
          metrics: { note: 'no_staging_file' },
          durationMs: 0,
          status: 'not_attempted_or_empty',
        };
      }
      return {
        sourceId: s.sourceId,
        name: s.name,
        ok: true,
        written: st.written,
        errors: [],
        metrics: { fromStaging: true, runFile: st.runFile },
        stagingPath: st.stagingPath,
        durationMs: 0,
        status: st.written > 0 ? 'scraped' : 'attempted_zero_rows',
      };
    });

  const mtimes = Object.values(staging).map((s) => s.mtime);
  const report = {
    startedAt: mtimes.length ? new Date(Math.min(...mtimes)).toISOString() : now,
    finishedAt: mtimes.length ? new Date(Math.max(...mtimes)).toISOString() : now,
    runId: 'rebuilt-from-staging',
    rebuiltAt: now,
    pdfsProcessed: null,
    pdfMax: null,
    sourcesAttempted: results.length,
    sourcesOk: results.filter((r) => r.ok).length,
    sourcesFailed: 0,
    sourcesWithRows: results.filter((r) => r.written > 0).length,
    sourcesZeroRows: results.filter((r) => r.written === 0).length,
    totalWritten: results.reduce((n, r) => n + r.written, 0),
    results,
  };

  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, JSON.stringify(report, null, 2), 'utf8');
  console.log(
    `Rebuilt collect-report: ${report.sourcesWithRows} with rows, ${report.sourcesZeroRows} zero, ${report.totalWritten} total staging rows`
  );
}

main();
