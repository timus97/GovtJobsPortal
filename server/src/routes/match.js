const crypto = require('crypto');
const express = require('express');
const store = require('../services/jobStore');
const studentAuth = require('../services/studentAuth');
const studentStore = require('../services/studentStore');
const { matchOpportunities } = require('../../../shared/eligibilityMatch');

const router = express.Router();

const FEATURE_SERVER_MATCH = process.env.FEATURE_SERVER_MATCH !== 'off';

function requestIdOf(req) {
  return req.headers['x-request-id'] || crypto.randomUUID();
}

router.post('/match', async (req, res) => {
  const requestId = requestIdOf(req);
  if (!FEATURE_SERVER_MATCH) {
    return res.status(404).json({ error: 'Match is disabled' });
  }

  const body = req.body && typeof req.body === 'object' ? req.body : {};
  let profile = body.profile;
  const session = studentAuth.readSession(req);
  if (session && (!profile || typeof profile !== 'object')) {
    profile = await studentStore.getProfile(session.uid);
  }
  const limitNum = Math.min(100, Math.max(1, parseInt(body.limit, 10) || 50));

  try {
    const opportunities = store.getOpportunities();
    const { matches, excluded } = matchOpportunities(profile, opportunities);
    const payload = {
      generatedAt: new Date().toISOString(),
      candidateCount: opportunities.length,
      matches: matches.slice(0, limitNum),
      excluded: excluded.slice(0, limitNum),
    };
    // Never log the profile body.
    const logger = require('../services/logger');
    logger.info('match', 'Scored opportunities', logger.fromReq(req, {
      requestId,
      action: 'match.run',
      meta: {
        matchCount: payload.matches.length,
        excludedCount: payload.excluded.length,
        candidateCount: payload.candidateCount,
        usedSessionProfile: Boolean(session && (!body.profile || typeof body.profile !== 'object')),
      },
    }));
    return res.json(payload);
  } catch (err) {
    if (err && err.statusCode === 400) {
      return res.status(400).json({ error: err.message, errors: err.errors || [] });
    }
    const logger = require('../services/logger');
    logger.error('match', err.message || 'Failed to match', logger.fromReq(req, {
      requestId,
      action: 'match.error',
    }));
    return res.status(500).json({ error: 'Failed to match' });
  }
});

module.exports = router;
