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

router.get('/exam-series', (req, res) => {
  try {
    res.json(store.listExamSeries(req.query));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Failed to list exam series' });
  }
});

router.get('/exam-series/:id', (req, res) => {
  try {
    const series = store.getExamSeriesById(req.params.id);
    if (!series) return res.status(404).json({ error: 'Exam series not found' });
    res.json(series);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Failed to load exam series' });
  }
});

router.get('/sources', (_req, res) => {
  try {
    res.json(store.getSourcesView());
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Failed to load sources' });
  }
});

module.exports = router;
