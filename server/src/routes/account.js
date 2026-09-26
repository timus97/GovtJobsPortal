const express = require('express');
const studentAuth = require('../services/studentAuth');
const studentStore = require('../services/studentStore');
const multipart = require('../services/multipart');
const logger = require('../services/logger');
const passwordReset = require('../services/passwordReset');
const studentMail = require('../services/studentMail');

const router = express.Router();

const LOGIN_WINDOW_MS = 15 * 60 * 1000;
const LOGIN_MAX = 5;
const loginAttempts = new Map();

function clientIp(req) {
  return req.ip || (req.socket && req.socket.remoteAddress) || 'unknown';
}

function pruneAttempts(now) {
  if (loginAttempts.size < 500) return;
  for (const [ip, rec] of loginAttempts) {
    if (!rec || rec.resetAt <= now) loginAttempts.delete(ip);
  }
}

function rateLimitAuth(req, res, next) {
  const now = Date.now();
  pruneAttempts(now);
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
    return res.status(429).json({ error: 'Too many attempts. Try again in 15 minutes.' });
  }
  next();
}

function requireFeature(_req, res, next) {
  if (!studentAuth.featureStudentOn()) {
    return res.status(404).json({ error: 'Student accounts are not enabled' });
  }
  next();
}

router.use(requireFeature);

router.post('/register', rateLimitAuth, async (req, res) => {
  try {
    const student = await studentStore.register({
      email: req.body?.email,
      password: req.body?.password,
    });
    studentAuth.setSessionCookie(res, student);
    logger.info('auth.student', 'Student registered', logger.fromReq(req, {
      role: 'student',
      actor: student.email,
      action: 'student.register',
      status: 201,
    }));
    res.status(201).json({ ok: true, student });
  } catch (err) {
    if (err.code === 'DUPLICATE') {
      logger.warn('auth.student', 'Registration duplicate email', logger.fromReq(req, {
        role: 'anon',
        action: 'student.register_duplicate',
        status: 409,
      }));
      return res.status(409).json({ error: err.message });
    }
    if (err.code === 'VALIDATION') return res.status(400).json({ error: err.message });
    logger.error('auth.student', err.message || 'Registration failed', logger.fromReq(req, { action: 'student.register_error' }));
    res.status(500).json({ error: 'Registration failed' });
  }
});

router.post('/login', rateLimitAuth, async (req, res) => {
  try {
    const student = await studentStore.verifyPassword(req.body?.email, req.body?.password);
    if (!student) {
      logger.warn('auth.student', 'Student login failed', logger.fromReq(req, {
        role: 'anon',
        action: 'student.login_failed',
        status: 401,
      }));
      return res.status(401).json({ error: 'Invalid email or password' });
    }
    studentAuth.setSessionCookie(res, student);
    logger.info('auth.student', 'Student signed in', logger.fromReq(req, {
      role: 'student',
      actor: student.email,
      action: 'student.login',
      status: 200,
    }));
    res.json({ ok: true, student });
  } catch (err) {
    logger.error('auth.student', err.message || 'Login failed', logger.fromReq(req, { action: 'student.login_error' }));
    res.status(500).json({ error: 'Login failed' });
  }
});

router.post('/logout', (req, res) => {
  logger.info('auth.student', 'Student signed out', logger.fromReq(req, { action: 'student.logout' }));
  studentAuth.clearSessionCookie(res);
  res.json({ ok: true });
});

router.post('/forgot-password', rateLimitAuth, async (req, res) => {
  const generic = { ok: true, message: 'If that email is registered, we sent a reset link.' };
  try {
    const email = typeof req.body?.email === 'string' ? req.body.email : '';
    const issued = await passwordReset.requestReset(email);
    if (!issued.created) {
      logger.info('auth.student', 'Password reset requested for unknown email', logger.fromReq(req, {
        role: 'anon',
        action: 'student.reset_unknown',
        status: 200,
      }));
      return res.json(generic);
    }
    let mailed = { sent: false, reason: 'skipped' };
    try {
      mailed = await studentMail.sendPasswordReset({
        to: issued.student.email,
        url: issued.url,
        expiresAt: issued.expiresAt,
      });
    } catch (mailErr) {
      logger.warn('auth.student', `Reset email failed: ${mailErr.message}`, logger.fromReq(req, {
        role: 'anon',
        actor: issued.student.email,
        action: 'student.reset_mail_failed',
      }));
      mailed = { sent: false, reason: 'send_failed' };
    }
    logger.info('auth.student', 'Password reset issued', logger.fromReq(req, {
      role: 'anon',
      actor: issued.student.email,
      action: 'student.reset_requested',
      status: 200,
      meta: { mailed: mailed.sent, reason: mailed.reason || null },
    }));
    const body = { ...generic };
    if (!mailed.sent && studentMail.allowDevLink()) {
      body.devResetUrl = issued.url;
      body.devHint =
        'No mail provider is configured (set RESEND_API_KEY or SMTP_HOST). Use the one-time link below.';
    }
    return res.json(body);
  } catch (err) {
    if (err.code === 'VALIDATION') return res.status(400).json({ error: err.message });
    logger.error('auth.student', err.message || 'Reset request failed', logger.fromReq(req, {
      action: 'student.reset_request_error',
    }));
    res.status(500).json({ error: 'Could not start a password reset' });
  }
});

router.get('/reset-password', (req, res) => {
  const peeked = passwordReset.peekReset(req.query?.token);
  if (!peeked.ok) {
    return res.json({ ok: false });
  }
  res.json({ ok: true, expiresAt: peeked.expiresAt });
});

router.post('/reset-password', rateLimitAuth, async (req, res) => {
  try {
    const student = await passwordReset.consumeReset(req.body?.token, req.body?.password);
    studentAuth.setSessionCookie(res, student);
    logger.info('auth.student', 'Password reset completed', logger.fromReq(req, {
      role: 'student',
      actor: student.email,
      action: 'student.reset_completed',
      status: 200,
    }));
    res.json({ ok: true, student });
  } catch (err) {
    if (err.code === 'VALIDATION' || err.code === 'EXPIRED') {
      return res.status(400).json({ error: err.message });
    }
    logger.error('auth.student', err.message || 'Reset failed', logger.fromReq(req, {
      action: 'student.reset_error',
    }));
    res.status(500).json({ error: 'Could not reset password' });
  }
});

router.get('/me', studentAuth.requireStudent, async (req, res) => {
  const row = await studentStore.findById(req.student.uid);
  if (!row) return res.status(401).json({ error: 'Unauthorized' });
  res.json({ student: studentStore.publicStudent(row) });
});

router.get('/profile', studentAuth.requireStudent, async (req, res) => {
  res.json({ profile: await studentStore.getProfile(req.student.uid) });
});

router.put('/profile', studentAuth.requireStudent, async (req, res) => {
  const body = req.body && typeof req.body === 'object' ? req.body : {};
  const profile = await studentStore.saveProfile(req.student.uid, body);
  if (!profile) return res.status(401).json({ error: 'Unauthorized' });
  logger.info('student.profile', 'Saved student profile', logger.fromReq(req, {
    action: 'student.profile_save',
    meta: { hasDob: Boolean(profile.dob), hasCategory: Boolean(profile.reservationCategory) },
  }));
  res.json({ profile });
});

router.post('/profile/import', studentAuth.requireStudent, async (req, res) => {
  const existing = await studentStore.getProfile(req.student.uid);
  if (existing && !studentStore.profileIsEmpty(existing)) {
    return res.status(409).json({ error: 'Server profile already has facts', profile: existing });
  }
  const incoming = req.body?.profile && typeof req.body.profile === 'object' ? req.body.profile : {};
  const profile = await studentStore.saveProfile(req.student.uid, incoming);
  res.json({ profile, imported: true });
});

const meRouter = express.Router();
meRouter.use(requireFeature);
meRouter.get('/profile', studentAuth.requireStudent, async (req, res) => {
  res.json({ profile: await studentStore.getProfile(req.student.uid) });
});
meRouter.put('/profile', studentAuth.requireStudent, async (req, res) => {
  const body = req.body && typeof req.body === 'object' ? req.body : {};
  const profile = await studentStore.saveProfile(req.student.uid, body);
  if (!profile) return res.status(401).json({ error: 'Unauthorized' });
  logger.info('student.profile', 'Saved student profile', logger.fromReq(req, {
    action: 'student.profile_save',
    meta: { hasDob: Boolean(profile.dob), hasCategory: Boolean(profile.reservationCategory) },
  }));
  res.json({ profile });
});
meRouter.post('/profile/import', studentAuth.requireStudent, async (req, res) => {
  const existing = await studentStore.getProfile(req.student.uid);
  if (existing && !studentStore.profileIsEmpty(existing)) {
    return res.status(409).json({ error: 'Server profile already has facts', profile: existing });
  }
  const incoming = req.body?.profile && typeof req.body.profile === 'object' ? req.body.profile : {};
  const profile = await studentStore.saveProfile(req.student.uid, incoming);
  res.json({ profile, imported: true });
});

meRouter.get('/items', studentAuth.requireStudent, async (req, res) => {
  try {
    res.json(await studentStore.listItems(req.student.uid, { status: req.query.status }));
  } catch (err) {
    logger.error('student.desk', err.message || 'Desk error', logger.fromReq(req, { action: 'student.desk_error' }));
    res.status(500).json({ error: 'Failed to list desk items' });
  }
});

meRouter.post('/items', studentAuth.requireStudent, async (req, res) => {
  try {
    const item = await studentStore.createItem(req.student.uid, req.body || {});
    if (!item) return res.status(401).json({ error: 'Unauthorized' });
    res.status(201).json({ item });
  } catch (err) {
    if (err.code === 'VALIDATION') return res.status(400).json({ error: err.message });
    logger.error('student.desk', err.message || 'Desk error', logger.fromReq(req, { action: 'student.desk_error' }));
    res.status(500).json({ error: 'Failed to track item' });
  }
});

meRouter.get('/items/:id', studentAuth.requireStudent, async (req, res) => {
  const item = await studentStore.getItem(req.student.uid, req.params.id);
  if (!item) return res.status(404).json({ error: 'Item not found' });
  res.json({ item });
});

meRouter.patch('/items/:id', studentAuth.requireStudent, async (req, res) => {
  try {
    const item = await studentStore.updateItem(req.student.uid, req.params.id, req.body || {});
    if (!item) return res.status(404).json({ error: 'Item not found' });
    res.json({ item });
  } catch (err) {
    if (err.code === 'VALIDATION') return res.status(400).json({ error: err.message });
    logger.error('student.desk', err.message || 'Desk error', logger.fromReq(req, { action: 'student.desk_error' }));
    res.status(500).json({ error: 'Failed to update item' });
  }
});

meRouter.delete('/items/:id', studentAuth.requireStudent, async (req, res) => {
  const ok = await studentStore.deleteItem(req.student.uid, req.params.id);
  if (!ok) return res.status(404).json({ error: 'Item not found' });
  res.json({ ok: true });
});

function sendStoreError(res, err, fallback) {
  if (err.code === 'VALIDATION') return res.status(400).json({ error: err.message });
  if (err.code === 'TOO_LARGE') return res.status(413).json({ error: err.message });
  logger.error('student.files', err.message || fallback, { role: 'student', action: 'student.file_error' });
  return res.status(500).json({ error: fallback });
}

function fileKindParam(req, res) {
  const kind = String(req.params.kind || '');
  if (!studentStore.FILE_KINDS.includes(kind)) {
    res.status(400).json({ error: 'kind must be admit or result' });
    return null;
  }
  return kind;
}

meRouter.post('/items/:id/files/:kind', studentAuth.requireStudent, async (req, res) => {
  const kind = fileKindParam(req, res);
  if (!kind) return;
  try {
    const part = await multipart.readMultipartFile(req, {
      fieldName: 'file',
      maxBytes: studentStore.MAX_FILE_BYTES + 65536,
    });
    const result = await studentStore.saveFile(req.student.uid, req.params.id, kind, {
      buffer: part.buffer,
      originalName: part.originalName,
    });
    if (!result) return res.status(404).json({ error: 'Item not found' });
    res.status(201).json(result);
  } catch (err) {
    sendStoreError(res, err, 'Upload failed');
  }
});

meRouter.get('/items/:id/files/:kind', studentAuth.requireStudent, async (req, res) => {
  const kind = fileKindParam(req, res);
  if (!kind) return;
  try {
    const file = await studentStore.readFileForDownload(req.student.uid, req.params.id, kind);
    if (!file) return res.status(404).json({ error: 'File not found' });
    res.setHeader('Content-Type', file.mime);
    res.setHeader('Content-Length', String(file.buffer.length));
    res.setHeader('Cache-Control', 'private, no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    const name = String(file.originalName || kind).replace(/"/g, '');
    res.setHeader('Content-Disposition', `attachment; filename="${name}"`);
    res.send(file.buffer);
  } catch (err) {
    sendStoreError(res, err, 'Download failed');
  }
});

meRouter.post('/events', studentAuth.requireStudent, (req, res) => {
  const action = typeof req.body?.action === 'string' ? req.body.action.slice(0, 80) : 'client.view';
  const page = typeof req.body?.path === 'string' ? req.body.path.slice(0, 200) : '';
  logger.info('client.student', action, logger.fromReq(req, {
    action,
    meta: { path: page },
  }));
  res.json({ ok: true });
});

meRouter.delete('/items/:id/files/:kind', studentAuth.requireStudent, async (req, res) => {
  const kind = fileKindParam(req, res);
  if (!kind) return;
  try {
    const result = await studentStore.deleteFile(req.student.uid, req.params.id, kind);
    if (!result) return res.status(404).json({ error: 'Item not found' });
    if (!result.removed) return res.status(404).json({ error: 'File not found' });
    res.json({ ok: true, item: result.item });
  } catch (err) {
    sendStoreError(res, err, 'Delete failed');
  }
});

module.exports = { router, meRouter };
