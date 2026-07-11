/**
 * Functional + performance checks against local API and/or public site.
 * Writes reports/qa-report.json and reports/qa-report.md
 *
 * Usage:
 *   node scripts/qa/e2eAndPerf.js
 *   node scripts/qa/e2eAndPerf.js --public https://timus97.github.io/GovtJobsPortal
 *   node scripts/qa/e2eAndPerf.js --api http://localhost:4000
 */
const fs = require('fs');
const path = require('path');
const http = require('http');
const https = require('https');

const root = path.join(__dirname, '..', '..');
const reportDir = path.join(root, 'reports');

function arg(name, fallback) {
  const i = process.argv.indexOf(name);
  if (i >= 0 && process.argv[i + 1]) return process.argv[i + 1];
  return fallback;
}

const publicBase = (arg('--public', process.env.PUBLIC_SITE_URL || 'https://timus97.github.io/GovtJobsPortal')).replace(
  /\/$/,
  ''
);
const apiBase = (arg('--api', process.env.API_BASE || 'http://localhost:4000')).replace(/\/$/, '');

function fetchUrl(url, timeoutMs = 15000) {
  return new Promise((resolve, reject) => {
    const lib = url.startsWith('https') ? https : http;
    const started = Date.now();
    const req = lib.get(url, { timeout: timeoutMs, headers: { 'User-Agent': 'NoExamSarkari-QA/1.0' } }, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        const body = Buffer.concat(chunks);
        resolve({
          url,
          status: res.statusCode,
          ms: Date.now() - started,
          bytes: body.length,
          body: body.toString('utf8'),
          headers: res.headers,
        });
      });
    });
    req.on('error', reject);
    req.on('timeout', () => {
      req.destroy();
      reject(new Error(`timeout ${url}`));
    });
  });
}

async function check(name, fn) {
  const t0 = Date.now();
  try {
    const detail = await fn();
    return { name, ok: true, ms: Date.now() - t0, ...detail };
  } catch (err) {
    return { name, ok: false, ms: Date.now() - t0, error: err.message };
  }
}

async function main() {
  const results = [];
  const perf = [];

  // --- Local API (optional) ---
  results.push(
    await check('api_health', async () => {
      const r = await fetchUrl(`${apiBase}/api/health`);
      if (r.status !== 200) throw new Error(`status ${r.status}`);
      const j = JSON.parse(r.body);
      if (!j.ok) throw new Error('health not ok');
      perf.push({ endpoint: '/api/health', ms: r.ms, bytes: r.bytes, target: apiBase });
      return { status: r.status, ms: r.ms };
    })
  );

  results.push(
    await check('api_stats', async () => {
      const r = await fetchUrl(`${apiBase}/api/stats`);
      if (r.status !== 200) throw new Error(`status ${r.status}`);
      const j = JSON.parse(r.body);
      if (typeof j.total !== 'number') throw new Error('missing total');
      perf.push({ endpoint: '/api/stats', ms: r.ms, bytes: r.bytes, target: apiBase });
      return { total: j.total, open: j.open, ms: r.ms };
    })
  );

  results.push(
    await check('api_jobs_list', async () => {
      const r = await fetchUrl(`${apiBase}/api/jobs?limit=5`);
      if (r.status !== 200) throw new Error(`status ${r.status}`);
      const j = JSON.parse(r.body);
      if (!Array.isArray(j.items)) throw new Error('no items');
      perf.push({ endpoint: '/api/jobs?limit=5', ms: r.ms, bytes: r.bytes, target: apiBase });
      return { count: j.items.length, total: j.total, ms: r.ms };
    })
  );

  results.push(
    await check('api_sources', async () => {
      const r = await fetchUrl(`${apiBase}/api/sources`);
      if (r.status !== 200) throw new Error(`status ${r.status}`);
      const j = JSON.parse(r.body);
      if (!Array.isArray(j.sources) || j.sources.length < 1) throw new Error('no sources');
      perf.push({ endpoint: '/api/sources', ms: r.ms, bytes: r.bytes, target: apiBase });
      return { sources: j.sources.length, ms: r.ms };
    })
  );

  results.push(
    await check('api_job_detail', async () => {
      const list = await fetchUrl(`${apiBase}/api/jobs?limit=1`);
      const j = JSON.parse(list.body);
      const id = j.items?.[0]?.id;
      if (!id) throw new Error('no job id');
      const r = await fetchUrl(`${apiBase}/api/jobs/${id}`);
      if (r.status !== 200) throw new Error(`status ${r.status}`);
      const job = JSON.parse(r.body);
      if (!job.officialUrl) throw new Error('missing officialUrl');
      if (job.hasExam !== false) throw new Error('hasExam must be false');
      perf.push({ endpoint: `/api/jobs/:id`, ms: r.ms, bytes: r.bytes, target: apiBase });
      return { id, ms: r.ms };
    })
  );

  results.push(
    await check('api_filter_psu', async () => {
      const r = await fetchUrl(`${apiBase}/api/jobs?orgType=psu&limit=20`);
      if (r.status !== 200) throw new Error(`status ${r.status}`);
      const j = JSON.parse(r.body);
      const bad = (j.items || []).filter((x) => x.orgType !== 'psu');
      if (bad.length) throw new Error('filter leaked non-psu');
      perf.push({ endpoint: '/api/jobs?orgType=psu', ms: r.ms, bytes: r.bytes, target: apiBase });
      return { total: j.total, ms: r.ms };
    })
  );

  // --- Public static site ---
  results.push(
    await check('public_home', async () => {
      const r = await fetchUrl(`${publicBase}/`);
      if (r.status !== 200) throw new Error(`status ${r.status}`);
      if (!/NoExam|Sarkari|root/i.test(r.body) && r.bytes < 200) throw new Error('empty home');
      perf.push({ endpoint: '/', ms: r.ms, bytes: r.bytes, target: publicBase });
      return { status: r.status, ms: r.ms, bytes: r.bytes };
    })
  );

  results.push(
    await check('public_stats_json', async () => {
      const r = await fetchUrl(`${publicBase}/data/stats.json`);
      if (r.status !== 200) throw new Error(`status ${r.status}`);
      const j = JSON.parse(r.body);
      if (typeof j.total !== 'number' || j.total < 1) throw new Error('invalid stats');
      perf.push({ endpoint: '/data/stats.json', ms: r.ms, bytes: r.bytes, target: publicBase });
      return { total: j.total, open: j.open, ms: r.ms, bytes: r.bytes };
    })
  );

  results.push(
    await check('public_jobs_json', async () => {
      const r = await fetchUrl(`${publicBase}/data/jobs.json`);
      if (r.status !== 200) throw new Error(`status ${r.status}`);
      const j = JSON.parse(r.body);
      if (!Array.isArray(j) || j.length < 1) throw new Error('no jobs');
      const exam = j.filter((x) => x.hasExam === true);
      if (exam.length) throw new Error(`published exam jobs: ${exam.length}`);
      const noUrl = j.filter((x) => !x.officialUrl);
      if (noUrl.length) throw new Error(`missing officialUrl: ${noUrl.length}`);
      perf.push({ endpoint: '/data/jobs.json', ms: r.ms, bytes: r.bytes, target: publicBase });
      return { count: j.length, ms: r.ms, bytes: r.bytes };
    })
  );

  results.push(
    await check('public_sources_json', async () => {
      const r = await fetchUrl(`${publicBase}/data/sources.json`);
      if (r.status !== 200) throw new Error(`status ${r.status}`);
      const j = JSON.parse(r.body);
      if (!j.sources || j.sources.length < 10) throw new Error('too few sources');
      perf.push({ endpoint: '/data/sources.json', ms: r.ms, bytes: r.bytes, target: publicBase });
      return { sources: j.sources.length, ms: r.ms, bytes: r.bytes };
    })
  );

  // Performance: 5 sequential warm requests to public stats
  const warm = [];
  for (let i = 0; i < 5; i++) {
    const r = await fetchUrl(`${publicBase}/data/stats.json`);
    warm.push(r.ms);
  }
  warm.sort((a, b) => a - b);
  const perfSummary = {
    publicStatsP50: warm[Math.floor(warm.length / 2)],
    publicStatsP95: warm[warm.length - 1],
    publicStatsSamples: warm,
    thresholds: {
      htmlHomeMs: 3000,
      statsJsonMs: 2000,
      jobsJsonMs: 5000,
    },
  };

  const homePerf = perf.find((p) => p.endpoint === '/');
  const statsPerf = perf.find((p) => p.endpoint === '/data/stats.json');
  const jobsPerf = perf.find((p) => p.endpoint === '/data/jobs.json');
  results.push({
    name: 'perf_thresholds',
    ok:
      (homePerf?.ms || 99999) <= perfSummary.thresholds.htmlHomeMs &&
      (statsPerf?.ms || 99999) <= perfSummary.thresholds.statsJsonMs &&
      (jobsPerf?.ms || 99999) <= perfSummary.thresholds.jobsJsonMs,
    ms: 0,
    homeMs: homePerf?.ms,
    statsMs: statsPerf?.ms,
    jobsMs: jobsPerf?.ms,
    ...perfSummary,
  });

  // Local schema if present
  try {
    const jobsPath = path.join(root, 'data', 'processed', 'jobs.json');
    if (fs.existsSync(jobsPath)) {
      const jobs = JSON.parse(fs.readFileSync(jobsPath, 'utf8'));
      const exam = jobs.filter((j) => j.hasExam !== false);
      results.push({
        name: 'local_schema_hasExam',
        ok: exam.length === 0,
        ms: 0,
        total: jobs.length,
        bad: exam.length,
      });
    }
  } catch (e) {
    results.push({ name: 'local_schema_hasExam', ok: false, ms: 0, error: e.message });
  }

  const passed = results.filter((r) => r.ok).length;
  const failed = results.filter((r) => !r.ok).length;
  const report = {
    generatedAt: new Date().toISOString(),
    publicBase,
    apiBase,
    summary: { passed, failed, total: results.length },
    results,
    performance: { samples: perf, summary: perfSummary },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, 'qa-report.json'), JSON.stringify(report, null, 2));

  const lines = [
    '# QA & Performance Report',
    '',
    `Generated: ${report.generatedAt}`,
    '',
    `Public site: ${publicBase}`,
    `API base: ${apiBase}`,
    '',
    `## Summary: ${passed}/${results.length} passed (${failed} failed)`,
    '',
    '| Check | Status | Time (ms) | Detail |',
    '|-------|--------|-----------|--------|',
  ];
  for (const r of results) {
    const detail = r.error
      ? r.error
      : Object.entries(r)
          .filter(([k]) => !['name', 'ok', 'ms', 'error'].includes(k))
          .map(([k, v]) => `${k}=${typeof v === 'object' ? JSON.stringify(v) : v}`)
          .join(', ');
    lines.push(`| ${r.name} | ${r.ok ? 'PASS' : 'FAIL'} | ${r.ms} | ${detail.slice(0, 120)} |`);
  }
  lines.push('', '## Performance samples', '');
  for (const p of perf) {
    lines.push(`- \`${p.target}${p.endpoint}\` — **${p.ms} ms**, ${p.bytes} bytes`);
  }
  lines.push(
    '',
    '## Thresholds',
    '',
    `- Home HTML ≤ ${perfSummary.thresholds.htmlHomeMs} ms`,
    `- stats.json ≤ ${perfSummary.thresholds.statsJsonMs} ms`,
    `- jobs.json ≤ ${perfSummary.thresholds.jobsJsonMs} ms`,
    `- Observed stats P50/P95: ${perfSummary.publicStatsP50}/${perfSummary.publicStatsP95} ms`,
    ''
  );
  fs.writeFileSync(path.join(reportDir, 'qa-report.md'), lines.join('\n'));

  console.log(JSON.stringify(report.summary, null, 2));
  console.log(`Wrote ${path.join(reportDir, 'qa-report.md')}`);
  if (failed) process.exitCode = 1;
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
