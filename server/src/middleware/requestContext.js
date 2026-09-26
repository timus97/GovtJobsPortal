const crypto = require('crypto');
const studentAuth = require('../services/studentAuth');
const opsAuth = require('../services/opsAuth');
const logger = require('../services/logger');

function attachRequestContext(req, _res, next) {
  req.requestId = req.headers['x-request-id'] || crypto.randomUUID();
  const student = studentAuth.readSession(req);
  const ops = opsAuth.readSession(req);
  if (student) req.student = student;
  if (ops) req.ops = ops;
  next();
}

function requestLogger(req, res, next) {
  const started = Date.now();
  res.setHeader('x-request-id', req.requestId || '');
  res.on('finish', () => {
    const api = req.originalUrl && req.originalUrl.startsWith('/api');
    if (!api) return;
    const pathOnly = (req.originalUrl || '').split('?')[0]
    const quietHealth = pathOnly === '/api/health' || pathOnly === '/api/session';
    const extra = logger.fromReq(req, {
      action: 'http.request',
      status: res.statusCode,
      durationMs: Date.now() - started,
      meta: { method: req.method, path: req.originalUrl.split('?')[0] },
    });
    if (quietHealth && res.statusCode < 400) {
      logger.debug('http', `${req.method} ${req.originalUrl}`, extra);
      return;
    }
    const fn = res.statusCode >= 500 ? logger.error : res.statusCode >= 400 ? logger.warn : logger.info;
    fn('http', `${req.method} ${req.originalUrl.split('?')[0]}`, extra);
  });
  next();
}

module.exports = { attachRequestContext, requestLogger };
