const express = require('express');
const store = require('../services/jobStore');

const router = express.Router();

router.get('/jobs', (req, res) => {
  try {
    res.json(store.listJobs(req.query));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Failed to list jobs' });
  }
});

router.get('/jobs/:id', (req, res) => {
  try {
    const job = store.getJobById(req.params.id);
    if (!job) return res.status(404).json({ error: 'Job not found' });
    res.json(job);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Failed to load job' });
  }
});

router.get('/meta/filters', (_req, res) => {
  try {
    res.json(store.getFilterMeta());
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Failed to load filters' });
  }
});

router.get('/stats', (_req, res) => {
  try {
    res.json(store.getStats());
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Failed to load stats' });
  }
});

router.get('/pipeline', (_req, res) => {
  try {
    res.json(store.getPipeline());
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Failed to load pipeline' });
  }
});

router.get('/sources', (_req, res) => {
  try {
    const registry = store.getRegistry();
    const pipeline = store.getPipeline();
    const collectById = Object.fromEntries(
      (pipeline.collect?.results || []).map((r) => [r.sourceId, r])
    );
    const stats = store.getStats();
    const jobsBySource = stats.bySource || {};

    const sources = (registry.sources || []).map((s) => {
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

    res.json({
      updatedAt: registry.updatedAt || null,
      lastCollectAt: pipeline.collect?.finishedAt || null,
      sources,
      groups: {
        central_gov: sources.filter((s) =>
          ['aggregator', 'manual', 'apprenticeship'].includes(s.category) || s.orgType === 'central'
        ),
        govt_company: sources.filter((s) => s.orgType === 'govt_company' || s.category === 'staffing'),
        psu: sources.filter((s) => s.category === 'psu_careers' || s.orgType === 'psu'),
      },
    });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Failed to load sources' });
  }
});

module.exports = router;
