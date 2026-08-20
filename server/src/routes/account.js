const express = require('express');
const studentAuth = require('../services/studentAuth');
const studentStore = require('../services/studentStore');
const multipart = require('../services/multipart');

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

router.post('/register', rateLimitAuth, (req, res) => {
  try {
    const student = studentStore.register({
      email: req.body?.email,
      password: req.body?.password,
    });
    studentAuth.setSessionCookie(res, student);
    res.status(201).json({ ok: true, student });
  } catch (err) {
    if (err.code === 'DUPLICATE') return res.status(409).json({ error: err.message });
    if (err.code === 'VALIDATION') return res.status(400).json({ error: err.message });
    console.error(err);
    res.status(500).json({ error: 'Registration failed' });
  }
});

router.post('/login', rateLimitAuth, (req, res) => {
  try {
    const student = studentStore.verifyPassword(req.body?.email, req.body?.password);
    if (!student) return res.status(401).json({ error: 'Invalid email or password' });
    studentAuth.setSessionCookie(res, student);
    res.json({ ok: true, student });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Login failed' });
  }
});

router.post('/logout', (_req, res) => {
  studentAuth.clearSessionCookie(res);
  res.json({ ok: true });
});

router.get('/me', studentAuth.requireStudent, (req, res) => {
  const row = studentStore.findById(req.student.uid);
  if (!row) return res.status(401).json({ error: 'Unauthorized' });
  res.json({ student: studentStore.publicStudent(row) });
});

router.get('/profile', studentAuth.requireStudent, (req, res) => {
  res.json({ profile: studentStore.getProfile(req.student.uid) });
});

router.put('/profile', studentAuth.requireStudent, (req, res) => {
  const body = req.body && typeof req.body === 'object' ? req.body : {};
  const profile = studentStore.saveProfile(req.student.uid, body);
  if (!profile) return res.status(401).json({ error: 'Unauthorized' });
  res.json({ profile });
});

router.post('/profile/import', studentAuth.requireStudent, (req, res) => {
  const existing = studentStore.getProfile(req.student.uid);
  if (existing && !studentStore.profileIsEmpty(existing)) {
    return res.status(409).json({ error: 'Server profile already has facts', profile: existing });
  }
  const incoming = req.body?.profile && typeof req.body.profile === 'object' ? req.body.profile : {};
  const profile = studentStore.saveProfile(req.student.uid, incoming);
  res.json({ profile, imported: true });
});

const meRouter = express.Router();
meRouter.use(requireFeature);
meRouter.get('/profile', studentAuth.requireStudent, (req, res) => {
  res.json({ profile: studentStore.getProfile(req.student.uid) });
});
meRouter.put('/profile', studentAuth.requireStudent, (req, res) => {
  const body = req.body && typeof req.body === 'object' ? req.body : {};
  const profile = studentStore.saveProfile(req.student.uid, body);
  if (!profile) return res.status(401).json({ error: 'Unauthorized' });
  res.json({ profile });
});
meRouter.post('/profile/import', studentAuth.requireStudent, (req, res) => {
  const existing = studentStore.getProfile(req.student.uid);
  if (existing && !studentStore.profileIsEmpty(existing)) {
    return res.status(409).json({ error: 'Server profile already has facts', profile: existing });
  }
  const incoming = req.body?.profile && typeof req.body.profile === 'object' ? req.body.profile : {};
  const profile = studentStore.saveProfile(req.student.uid, incoming);
  res.json({ profile, imported: true });
});

meRouter.get('/items', studentAuth.requireStudent, (req, res) => {
  try {
    res.json(studentStore.listItems(req.student.uid, { status: req.query.status }));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Failed to list desk items' });
  }
});

meRouter.post('/items', studentAuth.requireStudent, (req, res) => {
  try {
    const item = studentStore.createItem(req.student.uid, req.body || {});
    if (!item) return res.status(401).json({ error: 'Unauthorized' });
    res.status(201).json({ item });
  } catch (err) {
    if (err.code === 'VALIDATION') return res.status(400).json({ error: err.message });
    console.error(err);
    res.status(500).json({ error: 'Failed to track item' });
  }
});

meRouter.get('/items/:id', studentAuth.requireStudent, (req, res) => {
  const item = studentStore.getItem(req.student.uid, req.params.id);
  if (!item) return res.status(404).json({ error: 'Item not found' });
  res.json({ item });
});

meRouter.patch('/items/:id', studentAuth.requireStudent, (req, res) => {
  try {
    const item = studentStore.updateItem(req.student.uid, req.params.id, req.body || {});
    if (!item) return res.status(404).json({ error: 'Item not found' });
    res.json({ item });
  } catch (err) {
    if (err.code === 'VALIDATION') return res.status(400).json({ error: err.message });
    console.error(err);
    res.status(500).json({ error: 'Failed to update item' });
  }
});

meRouter.delete('/items/:id', studentAuth.requireStudent, (req, res) => {
  const ok = studentStore.deleteItem(req.student.uid, req.params.id);
  if (!ok) return res.status(404).json({ error: 'Item not found' });
  res.json({ ok: true });
});

function sendStoreError(res, err, fallback) {
  if (err.code === 'VALIDATION') return res.status(400).json({ error: err.message });
  if (err.code === 'TOO_LARGE') return res.status(413).json({ error: err.message });
  console.error(err);
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
    const result = studentStore.saveFile(req.student.uid, req.params.id, kind, {
      buffer: part.buffer,
      originalName: part.originalName,
    });
    if (!result) return res.status(404).json({ error: 'Item not found' });
    res.status(201).json(result);
  } catch (err) {
    sendStoreError(res, err, 'Upload failed');
  }
});

meRouter.get('/items/:id/files/:kind', studentAuth.requireStudent, (req, res) => {
  const kind = fileKindParam(req, res);
  if (!kind) return;
  try {
    const file = studentStore.readFileForDownload(req.student.uid, req.params.id, kind);
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

meRouter.delete('/items/:id/files/:kind', studentAuth.requireStudent, (req, res) => {
  const kind = fileKindParam(req, res);
  if (!kind) return;
  try {
    const result = studentStore.deleteFile(req.student.uid, req.params.id, kind);
    if (!result) return res.status(404).json({ error: 'Item not found' });
    if (!result.removed) return res.status(404).json({ error: 'File not found' });
    res.json({ ok: true, item: result.item });
  } catch (err) {
    sendStoreError(res, err, 'Delete failed');
  }
});

module.exports = { router, meRouter };
