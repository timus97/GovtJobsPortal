/**
 * Copy processed + registry data into client/public/data for static hosting (GitHub Pages).
 */
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const outDir = path.join(root, 'client', 'public', 'data');
const processed = path.join(root, 'data', 'processed');
const sources = path.join(root, 'data', 'sources');

fs.mkdirSync(outDir, { recursive: true });

function copy(name, from) {
  if (!fs.existsSync(from)) {
    console.warn('skip missing', from);
    return;
  }
  fs.copyFileSync(from, path.join(outDir, name));
  console.log('copied', name);
}

copy('jobs.json', path.join(processed, 'jobs.json'));
copy('exam_series.json', path.join(processed, 'exam_series.json'));
copy('opportunities.json', path.join(processed, 'opportunities.json'));
copy('stats.json', path.join(processed, 'stats.json'));
copy('run-report.json', path.join(processed, 'run-report.json'));
copy('collect-report.json', path.join(processed, 'collect-report.json'));
copy('alert-report.json', path.join(processed, 'alert-report.json'));
copy('registry.json', path.join(sources, 'registry.json'));

// Build a lightweight sources index for the static Sources page
try {
  const registry = JSON.parse(fs.readFileSync(path.join(sources, 'registry.json'), 'utf8'));
  const stats = fs.existsSync(path.join(processed, 'stats.json'))
    ? JSON.parse(fs.readFileSync(path.join(processed, 'stats.json'), 'utf8'))
    : {};
  const collect = fs.existsSync(path.join(processed, 'collect-report.json'))
    ? JSON.parse(fs.readFileSync(path.join(processed, 'collect-report.json'), 'utf8'))
    : null;
  const collectById = Object.fromEntries((collect?.results || []).map((r) => [r.sourceId, r]));
  const jobsBySource = stats.bySource || {};

  const list = (registry.sources || []).map((s) => {
    const run = collectById[s.sourceId] || null;
    const listUrls = Array.isArray(s.listUrls) ? s.listUrls.filter(Boolean) : [];
    const urls = [...new Set([s.baseUrl, ...listUrls].filter(Boolean))];
    return {
      sourceId: s.sourceId,
      name: s.name,
      category: s.category || 'other',
      orgType: s.orgTypeDefault || null,
      baseUrl: s.baseUrl || '',
      listUrls,
      urls,
      priority: s.priority,
      method: s.method,
      cadence: s.cadence,
      enabled: Boolean(s.enabled),
      render: s.render || null,
      publishedJobs: jobsBySource[s.sourceId] || 0,
      lastScrape: run
        ? {
            ok: run.ok,
            written: run.written ?? 0,
            errors: run.errors || [],
            durationMs: run.durationMs,
            metrics: run.metrics || {},
          }
        : null,
    };
  });

  fs.writeFileSync(
    path.join(outDir, 'sources.json'),
    JSON.stringify(
      {
        updatedAt: registry.updatedAt || null,
        lastCollectAt: collect?.finishedAt || null,
        sources: list,
      },
      null,
      2
    )
  );
  console.log('wrote sources.json', list.length);
} catch (e) {
  console.warn('sources index failed', e.message);
}

console.log('Static data ready in client/public/data/');
