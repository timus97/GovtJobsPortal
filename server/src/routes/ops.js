const express = require('express');
const operatorStore = require('../services/operatorStore');
const opsAuth = require('../services/opsAuth');
const store = require('../services/jobStore');
const collectQueue = require('../services/collectQueue');

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
      return res.status(401).json({ error: 'Invalid username or password' });
    }

    opsAuth.setSessionCookie(res, user);
    res.json({ ok: true, username: user.username, role: user.role });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Login failed' });
  }
});

router.post('/logout', (_req, res) => {
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
    res.status(201).json({ ok: true, operator });
  } catch (err) {
    if (err.code === 'DUPLICATE') {
      return res.status(409).json({ error: err.message });
    }
    if (err.code === 'VALIDATION') {
      return res.status(400).json({ error: err.message });
    }
    console.error(err);
    res.status(500).json({ error: 'Failed to create operator' });
  }
});

router.post('/collect', opsAuth.requireOps, (req, res) => {
  try {
    const job = collectQueue.submit({
      url: req.body?.url,
      sourceLabel: req.body?.sourceLabel || req.body?.label,
    });
    res.status(202).json({ jobId: job.id, job });
  } catch (err) {
    if (err.code === 'HOST') {
      return res.status(400).json({ error: 'host_not_allowed', reason: 'host_not_allowed' });
    }
    if (err.code === 'VALIDATION') {
      return res.status(400).json({ error: err.message });
    }
    console.error(err);
    res.status(500).json({ error: 'Failed to queue collect job' });
  }
});

router.get('/jobs', opsAuth.requireOps, (req, res) => {
  try {
    const items = collectQueue.listJobs({ state: req.query.state });
    res.json({ items, total: items.length });
  } catch (err) {
    console.error(err);
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
    console.error(err);
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
    console.error(err);
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
    console.error(err);
    res.status(500).json({ error: 'Failed to patch review' });
  }
});

router.post('/review/:id/publish', opsAuth.requireOps, async (req, res) => {
  try {
    const job = await collectQueue.publish(req.params.id);
    if (!job) return res.status(404).json({ error: 'Collect job not found' });
    res.json(job);
  } catch (err) {
    if (err.code === 'STATE') return res.status(409).json({ error: err.message });
    if (err.code === 'VALIDATION') return res.status(400).json({ error: err.message });
    console.error(err);
    res.status(500).json({ error: 'Failed to publish' });
  }
});

router.post('/review/:id/reject', opsAuth.requireOps, (req, res) => {
  try {
    const job = collectQueue.reject(req.params.id, req.body?.reason);
    if (!job) return res.status(404).json({ error: 'Collect job not found' });
    res.json(job);
  } catch (err) {
    if (err.code === 'STATE') return res.status(409).json({ error: err.message });
    if (err.code === 'VALIDATION') return res.status(400).json({ error: err.message });
    console.error(err);
    res.status(500).json({ error: 'Failed to reject' });
  }
});

router.get('/sources', opsAuth.requireOps, (_req, res) => {
  try {
    res.json(store.getSourcesView());
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Failed to load sources' });
  }
});

module.exports = router;
