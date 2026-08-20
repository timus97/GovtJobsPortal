const express = require('express');
const studentAuth = require('../services/studentAuth');
const mockAttempts = require('../services/mockAttempts');
const { publicBank } = require('../../../shared/mockScore');

function requireFeature(_req, res, next) {
  if (!studentAuth.featureStudentOn()) {
    return res.status(404).json({ error: 'Student accounts are not enabled' });
  }
  next();
}

const mocksPublicRouter = express.Router();
mocksPublicRouter.use(requireFeature);

mocksPublicRouter.get('/mocks/:seriesId', (req, res) => {
  const bank = mockAttempts.loadBank(req.params.seriesId);
  if (!bank) return res.status(404).json({ error: 'Mock bank not found' });
  res.json({ bank: publicBank(bank) });
});

const meMocksRouter = express.Router();
meMocksRouter.use(requireFeature);

meMocksRouter.post('/mocks/:seriesId/attempts', studentAuth.requireStudent, (req, res) => {
  const result = mockAttempts.startAttempt(req.student.uid, req.params.seriesId, req.body || {});
  if (result.error === 'UNAUTHORIZED') return res.status(401).json({ error: 'Unauthorized' });
  if (result.error === 'NOT_FOUND') return res.status(404).json({ error: 'Mock bank not found' });
  res.status(201).json(result);
});

meMocksRouter.get('/mocks/attempts/:attemptId', studentAuth.requireStudent, (req, res) => {
  const result = mockAttempts.getAttempt(req.student.uid, req.params.attemptId);
  if (result.error === 'NOT_FOUND') return res.status(404).json({ error: 'Attempt not found' });
  res.json(result);
});

meMocksRouter.post('/mocks/attempts/:attemptId/submit', studentAuth.requireStudent, (req, res) => {
  const answers = req.body && typeof req.body === 'object' ? req.body.answers : {};
  const result = mockAttempts.submitAttempt(req.student.uid, req.params.attemptId, answers);
  if (result.error === 'NOT_FOUND') return res.status(404).json({ error: 'Attempt not found' });
  if (result.error === 'ALREADY_SUBMITTED') {
    return res.status(409).json({ error: 'Attempt already submitted' });
  }
  res.json(result);
});

module.exports = { router: mocksPublicRouter, meMocksRouter };
