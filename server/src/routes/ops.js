const express = require('express');
const operatorStore = require('../services/operatorStore');
const opsAuth = require('../services/opsAuth');
const store = require('../services/jobStore');
const collectQueue = require('../services/collectQueue');
const sourceRegistry = require('../services/sourceRegistry');
const dailyCollect = require('../services/dailyCollect');
const logger = require('../services/logger');

const router = express.Router();

const LOGIN_WINDOW_MS = 15 * 60 * 1000;
const LOGIN_MAX = 5;
const loginAttempts = new Map();

function clientIp(req) {
  return req.ip || (req.socket && req.socket.remoteAddress) || 'unknown';
}

function pruneLoginAttempts(now) {
  if (loginAttempts.size < 500) return;
  for (const [ip, rec] of loginAttempts) {
    if (!rec || rec.resetAt <= now) loginAttempts.delete(ip);
  }
}

function rateLimitLogin(req, res, next) {
  const now = Date.now();
  pruneLoginAttempts(now);
  const ip = clientIp(req);
  const rec = loginAttempts.get(ip);
  if (!rec || rec.resetAt <= now) {
    loginAttempts.set(ip, { count: 1, resetAt: now + LOGIN_WINDOW_MS });
    return next();
  }
  rec.count += 1;
  if (rec.count > LOGIN_MAX) {
    const retrySec = Math.max(1, Math.ceil((rec.resetAt - now) / 1000));
    res.setHeader('Retry-After', String(retrySec));
    return res.status(429).json({ error: 'Too many login attempts. Try again in 15 minutes.' });
  }
  next();
}

router.post('/login', rateLimitLogin, (req, res) => {
  try {
    const username = typeof req.body?.username === 'string' ? req.body.username.trim() : '';
    const password = typeof req.body?.password === 'string' ? req.body.password : '';
    if (!username || !password) {
      return res.status(400).json({ error: 'Username and password are required' });
    }

    if (operatorStore.count() === 0) {
      const boot = process.env.OPERATOR_PASSWORD;
      if (username.toLowerCase() === 'admin' && boot && password === boot) {
        try {
          operatorStore.bootstrapIfEmpty(boot);
        } catch (bootErr) {
          return res.status(500).json({ error: bootErr.message || 'Bootstrap failed' });
        }
      }
    }

    const user = operatorStore.verifyPassword(username, password);
    if (!user) {
      logger.warn('auth.ops', 'Operator login failed', logger.fromReq(req, {
        role: 'anon',
        actor: username,
        action: 'ops.login_failed',
        status: 401,
      }));
      return res.status(401).json({ error: 'Invalid username or password' });
    }

    opsAuth.setSessionCookie(res, user);
    logger.info('auth.ops', 'Operator signed in', logger.fromReq(req, {
      role: user.role || 'operator',
      actor: user.username,
      action: 'ops.login',
      status: 200,
    }));
    res.json({ ok: true, username: user.username, role: user.role });
  } catch (err) {
    logger.error('auth.ops', err.message || 'Login failed', logger.fromReq(req, { action: 'ops.login_error' }));
    res.status(500).json({ error: 'Login failed' });
  }
});

router.post('/logout', (req, res) => {
  logger.info('auth.ops', 'Operator signed out', logger.fromReq(req, { action: 'ops.logout' }));
  opsAuth.clearSessionCookie(res);
  res.json({ ok: true });
});

router.get('/me', opsAuth.requireOps, (req, res) => {
  res.json({
    username: req.ops.sub,
    role: req.ops.role || 'operator',
  });
});

router.post('/operators', opsAuth.requireOps, (req, res) => {
  try {
    if ((req.ops.role || 'operator') !== 'admin') {
      return res.status(403).json({ error: 'Admin role required' });
    }
    const username = typeof req.body?.username === 'string' ? req.body.username : '';
    const password = typeof req.body?.password === 'string' ? req.body.password : '';
    const operator = operatorStore.create({
      username,
      password,
      role: 'operator',
    });
    logger.info('auth.ops', `Created operator ${operator.username}`, logger.fromReq(req, {
      action: 'ops.operator_create',
      status: 201,
      meta: { created: operator.username },
    }));
    res.status(201).json({ ok: true, operator });
  } catch (err) {
    if (err.code === 'DUPLICATE') {
      return res.status(409).json({ error: err.message });
    }
    if (err.code === 'VALIDATION') {
      return res.status(400).json({ error: err.message });
    }
    logger.error('auth.ops', err.message || 'Failed to create operator', logger.fromReq(req, { action: 'ops.operator_create_error' }));
    res.status(500).json({ error: 'Failed to create operator' });
  }
});

router.get('/collect-progress', opsAuth.requireOps, (_req, res) => {
  try {
    res.json(dailyCollect.status());
  } catch (err) {
    logger.error('collect', err.message || 'Failed to read collect progress', logger.fromReq(_req, { action: 'collect.progress_error' }));
    res.status(500).json({ error: 'Failed to read collect progress' });
  }
});

router.post('/collect/daily', opsAuth.requireOps, (req, res) => {
  try {
    const limitRaw = req.body?.limit;
    const limit = limitRaw != null && limitRaw !== '' ? Number(limitRaw) : null;
    if (limit != null && (!Number.isFinite(limit) || limit < 1)) {
      return res.status(400).json({ error: 'limit must be a positive number' });
    }
    const job = dailyCollect.start({
      source: typeof req.body?.source === 'string' ? req.body.source.trim() : '',
      limit,
      psuOnly: Boolean(req.body?.psuOnly),
    });
    logger.info('collect', 'Started daily collect', logger.fromReq(req, {
      action: 'collect.daily',
      status: 202,
      meta: { pid: job.pid, source: job.source, limit: job.limit },
    }));
    res.status(202).json({ ...job, progress: dailyCollect.status() });
  } catch (err) {
    if (err.code === 'BUSY') return res.status(409).json({ error: err.message, progress: dailyCollect.status() });
    logger.error('collect', err.message || 'Failed to start daily collect', logger.fromReq(req, { action: 'collect.daily_error' }));
    res.status(500).json({ error: 'Failed to start daily collect' });
  }
});

router.post('/collect', opsAuth.requireOps, (req, res) => {
  try {
    const job = collectQueue.submit({
      url: req.body?.url,
      sourceLabel: req.body?.sourceLabel || req.body?.label,
      sourceId: req.body?.sourceId,
    });
    logger.info('collect', `Queued paste collect ${job.id}`, logger.fromReq(req, {
      action: 'collect.queue',
      status: 202,
      meta: { jobId: job.id, host: job.host, sourceId: job.sourceId || null },
    }));
    res.status(202).json({ jobId: job.id, job });
  } catch (err) {
    if (err.code === 'HOST') {
      return res.status(400).json({ error: 'host_not_allowed', reason: 'host_not_allowed' });
    }
    if (err.code === 'VALIDATION') {
      return res.status(400).json({ error: err.message });
    }
    logger.error('collect', err.message || 'Failed to queue collect job', logger.fromReq(req, { action: 'collect.queue_error' }));
    res.status(500).json({ error: 'Failed to queue collect job' });
  }
});

router.get('/jobs', opsAuth.requireOps, (req, res) => {
  try {
    const items = collectQueue.listJobs({ state: req.query.state });
    res.json({ items, total: items.length });
  } catch (err) {
    logger.error('ops', err.message || 'Ops route failed', logger.fromReq(req, { action: 'ops.error' }));
    res.status(500).json({ error: 'Failed to list collect jobs' });
  }
});

router.get('/jobs/:id', opsAuth.requireOps, (req, res) => {
  const job = collectQueue.findJob(req.params.id);
  if (!job) return res.status(404).json({ error: 'Collect job not found' });
  res.json(job);
});

router.post('/jobs/:id/cancel', opsAuth.requireOps, (req, res) => {
  try {
    const job = collectQueue.cancel(req.params.id);
    if (!job) return res.status(404).json({ error: 'Collect job not found' });
    res.json(job);
  } catch (err) {
    if (err.code === 'STATE') return res.status(409).json({ error: err.message });
    logger.error('ops', err.message || 'Ops route failed', logger.fromReq(req, { action: 'ops.error' }));
    res.status(500).json({ error: 'Failed to cancel job' });
  }
});

router.get('/review', opsAuth.requireOps, (_req, res) => {
  try {
    const items = collectQueue.reviewQueue();
    res.json({
      items,
      total: items.length,
      counts: {
        needs_review: items.filter((j) => j.state === 'needs_review').length,
        valid: items.filter((j) => j.state === 'valid').length,
      },
    });
  } catch (err) {
    logger.error('ops', err.message || 'Ops route failed', logger.fromReq(req, { action: 'ops.error' }));
    res.status(500).json({ error: 'Failed to load review queue' });
  }
});

router.patch('/review/:id', opsAuth.requireOps, (req, res) => {
  try {
    const facts = req.body && typeof req.body === 'object' ? req.body : {};
    const job = collectQueue.patchReview(req.params.id, facts);
    if (!job) return res.status(404).json({ error: 'Collect job not found' });
    res.json(job);
  } catch (err) {
    if (err.code === 'STATE') return res.status(409).json({ error: err.message });
    if (err.code === 'VALIDATION') return res.status(400).json({ error: err.message });
    logger.error('ops', err.message || 'Ops route failed', logger.fromReq(req, { action: 'ops.error' }));
    res.status(500).json({ error: 'Failed to patch review' });
  }
});

router.post('/review/:id/publish', opsAuth.requireOps, async (req, res) => {
  try {
    const job = await collectQueue.publish(req.params.id);
    if (!job) return res.status(404).json({ error: 'Collect job not found' });
    logger.info('ops.review', `Published collect job ${job.id}`, logger.fromReq(req, {
      action: 'ops.publish',
      meta: { jobId: job.id, opportunityId: job.opportunityId || null },
    }));
    res.json(job);
  } catch (err) {
    if (err.code === 'STATE') return res.status(409).json({ error: err.message });
    if (err.code === 'VALIDATION') return res.status(400).json({ error: err.message });
    logger.error('ops', err.message || 'Ops route failed', logger.fromReq(req, { action: 'ops.error' }));
    res.status(500).json({ error: 'Failed to publish' });
  }
});

router.post('/review/:id/unpublish', opsAuth.requireOps, async (req, res) => {
  try {
    if (!collectQueue.featureUnpublishOn()) {
      return res.status(404).json({ error: 'Unpublish is not enabled' });
    }
    const job = await collectQueue.unpublish(req.params.id);
    if (!job) return res.status(404).json({ error: 'Collect job not found' });
    logger.info('ops.review', `Unpublished collect job ${job.id}`, logger.fromReq(req, {
      action: 'ops.unpublish',
      meta: { jobId: job.id },
    }));
    res.json(job);
  } catch (err) {
    if (err.code === 'FEATURE') return res.status(404).json({ error: err.message });
    if (err.code === 'STATE') return res.status(409).json({ error: err.message });
    logger.error('ops', err.message || 'Ops route failed', logger.fromReq(req, { action: 'ops.error' }));
    res.status(500).json({ error: 'Failed to unpublish' });
  }
});

router.post('/review/:id/reject', opsAuth.requireOps, (req, res) => {
  try {
    const job = collectQueue.reject(req.params.id, req.body?.reason);
    if (!job) return res.status(404).json({ error: 'Collect job not found' });
    logger.info('ops.review', `Rejected collect job ${job.id}`, logger.fromReq(req, {
      action: 'ops.reject',
      meta: { jobId: job.id },
    }));
    res.json(job);
  } catch (err) {
    if (err.code === 'STATE') return res.status(409).json({ error: err.message });
    if (err.code === 'VALIDATION') return res.status(400).json({ error: err.message });
    logger.error('ops', err.message || 'Ops route failed', logger.fromReq(req, { action: 'ops.error' }));
    res.status(500).json({ error: 'Failed to reject' });
  }
});

router.get('/sources', opsAuth.requireOps, (_req, res) => {
  try {
    res.json(store.getSourcesView());
  } catch (err) {
    logger.error('sources', err.message || 'Failed to load sources', logger.fromReq(_req, { action: 'sources.list_error' }));
    res.status(500).json({ error: 'Failed to load sources' });
  }
});

router.get('/sources/:sourceId', opsAuth.requireOps, (req, res) => {
  try {
    const source = sourceRegistry.getSource(req.params.sourceId);
    if (!source) return res.status(404).json({ error: 'Source not found' });
    const view = store.getSourcesView();
    const health = (view.sources || []).find((s) => s.sourceId === source.sourceId) || null;
    res.json({ source, health, collectUrl: sourceRegistry.collectUrlOf(source) });
  } catch (err) {
    logger.error('sources', err.message || 'Failed to load source', logger.fromReq(req, { action: 'sources.get_error' }));
    res.status(500).json({ error: 'Failed to load source' });
  }
});

router.post('/sources', opsAuth.requireOps, (req, res) => {
  try {
    const source = sourceRegistry.createSource(req.body || {});
    logger.info('sources', `Created source ${source.sourceId}`, logger.fromReq(req, {
      action: 'sources.create',
      status: 201,
      meta: { sourceId: source.sourceId },
    }));
    res.status(201).json({ source });
  } catch (err) {
    if (err.code === 'DUPLICATE') return res.status(409).json({ error: err.message });
    if (err.code === 'VALIDATION') return res.status(400).json({ error: err.message });
    logger.error('sources', err.message || 'Failed to create source', logger.fromReq(req, { action: 'sources.create_error' }));
    res.status(500).json({ error: 'Failed to create source' });
  }
});

router.patch('/sources/:sourceId', opsAuth.requireOps, (req, res) => {
  try {
    const source = sourceRegistry.updateSource(req.params.sourceId, req.body || {});
    if (!source) return res.status(404).json({ error: 'Source not found' });
    logger.info('sources', `Updated source ${source.sourceId}`, logger.fromReq(req, {
      action: 'sources.update',
      meta: { sourceId: source.sourceId, enabled: source.enabled, keys: Object.keys(req.body || {}) },
    }));
    res.json({ source });
  } catch (err) {
    if (err.code === 'VALIDATION') return res.status(400).json({ error: err.message });
    logger.error('sources', err.message || 'Failed to update source', logger.fromReq(req, { action: 'sources.update_error' }));
    res.status(500).json({ error: 'Failed to update source' });
  }
});

router.post('/sources/:sourceId/collect', opsAuth.requireOps, (req, res) => {
  try {
    const source = sourceRegistry.getSource(req.params.sourceId);
    if (!source) return res.status(404).json({ error: 'Source not found' });
    if (source.method === 'manual') {
      return res.status(400).json({ error: 'Manual sources are not collected automatically' });
    }
    if (!source.enabled) {
      return res.status(400).json({ error: 'Enable the source before collecting' });
    }
    const url = sourceRegistry.collectUrlOf(source);
    if (!url) return res.status(400).json({ error: 'Source has no https URL to collect' });
    const job = collectQueue.submit({
      url,
      sourceLabel: source.name,
      sourceId: source.sourceId,
    });
    logger.info('collect', `Queued source collect ${source.sourceId}`, logger.fromReq(req, {
      action: 'collect.source',
      status: 202,
      meta: { sourceId: source.sourceId, jobId: job.id, host: job.host },
    }));
    res.status(202).json({ jobId: job.id, job, sourceId: source.sourceId });
  } catch (err) {
    if (err.code === 'HOST') {
      return res.status(400).json({ error: 'host_not_allowed', reason: 'host_not_allowed' });
    }
    if (err.code === 'VALIDATION') return res.status(400).json({ error: err.message });
    logger.error('collect', err.message || 'Failed to collect source', logger.fromReq(req, { action: 'collect.source_error' }));
    res.status(500).json({ error: 'Failed to queue source collect' });
  }
});

router.get('/logs', opsAuth.requireOps, (req, res) => {
  try {
    const payload = logger.list({
      unit: req.query.unit,
      role: req.query.role,
      level: req.query.level,
      q: req.query.q,
      limit: req.query.limit,
    });
    res.json(payload);
  } catch (err) {
    logger.error('logs', err.message || 'Failed to list logs', logger.fromReq(req, { action: 'logs.list_error' }));
    res.status(500).json({ error: 'Failed to list logs' });
  }
});

router.post('/client-log', opsAuth.requireOps, (req, res) => {
  const action = typeof req.body?.action === 'string' ? req.body.action.slice(0, 80) : 'client.view';
  const page = typeof req.body?.path === 'string' ? req.body.path.slice(0, 200) : '';
  logger.info('client.ops', action, logger.fromReq(req, {
    action,
    meta: { path: page },
  }));
  res.json({ ok: true });
});

module.exports = router;
