const express = require('express');
const store = require('../services/jobStore');
const logger = require('../services/logger');

const router = express.Router();

router.get('/jobs', (req, res) => {
  try {
    res.json(store.listJobs(req.query));
  } catch (err) {
    logger.error('jobs', err.message || 'Jobs route failed', logger.fromReq(req, { action: 'jobs.error' }));
    res.status(500).json({ error: 'Failed to list jobs' });
  }
});

router.get('/jobs/:id', (req, res) => {
  try {
    const job = store.getJobById(req.params.id);
    if (!job) return res.status(404).json({ error: 'Job not found' });
    res.json(job);
  } catch (err) {
    logger.error('jobs', err.message || 'Jobs route failed', logger.fromReq(req, { action: 'jobs.error' }));
    res.status(500).json({ error: 'Failed to load job' });
  }
});

router.get('/meta/filters', (req, res) => {
  try {
    res.json(store.getFilterMeta());
  } catch (err) {
    logger.error('jobs', err.message || 'Jobs route failed', logger.fromReq(req, { action: 'jobs.error' }));
    res.status(500).json({ error: 'Failed to load filters' });
  }
});

router.get('/stats', (req, res) => {
  try {
    res.json(store.getStats());
  } catch (err) {
    logger.error('jobs', err.message || 'Jobs route failed', logger.fromReq(req, { action: 'jobs.error' }));
    res.status(500).json({ error: 'Failed to load stats' });
  }
});

router.get('/pipeline', (req, res) => {
  try {
    res.json(store.getPipeline());
  } catch (err) {
    logger.error('jobs', err.message || 'Jobs route failed', logger.fromReq(req, { action: 'jobs.error' }));
    res.status(500).json({ error: 'Failed to load pipeline' });
  }
});

router.get('/exam-series', (req, res) => {
  try {
    res.json(store.listExamSeries(req.query));
  } catch (err) {
    logger.error('jobs', err.message || 'Jobs route failed', logger.fromReq(req, { action: 'jobs.error' }));
    res.status(500).json({ error: 'Failed to list exam series' });
  }
});

router.get('/exam-series/:id', (req, res) => {
  try {
    const series = store.getExamSeriesById(req.params.id);
    if (!series) return res.status(404).json({ error: 'Exam series not found' });
    res.json(series);
  } catch (err) {
    logger.error('jobs', err.message || 'Jobs route failed', logger.fromReq(req, { action: 'jobs.error' }));
    res.status(500).json({ error: 'Failed to load exam series' });
  }
});

router.get('/sources', (req, res) => {
  try {
    res.json(store.getSourcesView());
  } catch (err) {
    logger.error('jobs', err.message || 'Jobs route failed', logger.fromReq(req, { action: 'jobs.error' }));
    res.status(500).json({ error: 'Failed to load sources' });
  }
});

module.exports = router;
