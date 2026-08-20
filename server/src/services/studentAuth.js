const crypto = require('crypto');
const { safeEqual } = require('./password');

const COOKIE_NAME = 'student_session';
const IDLE_TTL_MS = 14 * 24 * 60 * 60 * 1000;
const AUD = 'student';

let ephemeralSecret = null;

function getSecret() {
  if (process.env.SESSION_SECRET) return process.env.SESSION_SECRET;
  if (process.env.NODE_ENV === 'production') {
    throw new Error('SESSION_SECRET is required in production');
  }
  if (!ephemeralSecret) {
    ephemeralSecret = crypto.randomBytes(32).toString('hex');
    console.warn('SESSION_SECRET not set; using ephemeral secret (sessions reset on restart)');
  }
  return ephemeralSecret;
}

function b64url(buf) {
  return Buffer.from(buf)
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}

function fromB64url(str) {
  const pad = str.length % 4 === 0 ? '' : '='.repeat(4 - (str.length % 4));
  const b64 = String(str).replace(/-/g, '+').replace(/_/g, '/') + pad;
  return Buffer.from(b64, 'base64');
}

function parseCookies(header) {
  const out = {};
  if (!header) return out;
  for (const part of String(header).split(';')) {
    const idx = part.indexOf('=');
    if (idx === -1) continue;
    const key = part.slice(0, idx).trim();
    const val = part.slice(idx + 1).trim();
    if (!key) continue;
    try {
      out[key] = decodeURIComponent(val);
    } catch {
      out[key] = val;
    }
  }
  return out;
}

function signCookie(user, opts = {}) {
  const now = opts.now instanceof Date ? opts.now.getTime() : Number(opts.now) || Date.now();
  const ttlMs = Number(opts.ttlMs) > 0 ? Number(opts.ttlMs) : IDLE_TTL_MS;
  const payload = {
    v: 1,
    aud: AUD,
    uid: user.id,
    sub: user.email,
    iat: Math.floor(now / 1000),
    exp: Math.floor((now + ttlMs) / 1000),
  };
  const body = b64url(JSON.stringify(payload));
  const sig = b64url(crypto.createHmac('sha256', getSecret()).update(body).digest());
  return `${body}.${sig}`;
}

function readSession(req) {
  try {
    const cookies = parseCookies(req && req.headers && req.headers.cookie);
    const token = cookies[COOKIE_NAME];
    if (!token) return null;
    const dot = token.lastIndexOf('.');
    if (dot <= 0) return null;
    const body = token.slice(0, dot);
    const sig = token.slice(dot + 1);
    const expected = b64url(crypto.createHmac('sha256', getSecret()).update(body).digest());
    if (!safeEqual(fromB64url(sig), fromB64url(expected))) return null;
    const payload = JSON.parse(fromB64url(body).toString('utf8'));
    if (!payload || payload.v !== 1 || payload.aud !== AUD || !payload.uid || !payload.exp) {
      return null;
    }
    const nowSec = Math.floor(Date.now() / 1000);
    if (payload.exp <= nowSec) return null;
    return payload;
  } catch {
    return null;
  }
}

function cookieOptions() {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    path: '/',
    maxAge: IDLE_TTL_MS,
  };
}

function setSessionCookie(res, user, opts) {
  const token = signCookie(user, opts);
  res.cookie(COOKIE_NAME, token, cookieOptions());
  return token;
}

function clearSessionCookie(res) {
  res.clearCookie(COOKIE_NAME, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    path: '/',
  });
}

function requireStudent(req, res, next) {
  const session = readSession(req);
  if (!session) {
    return res.status(401).json({ error: 'Unauthorized' });
  }
  req.student = session;
  setSessionCookie(res, { id: session.uid, email: session.sub });
  next();
}

function featureStudentOn() {
  const flag = String(process.env.FEATURE_STUDENT || 'on').trim().toLowerCase();
  return flag !== 'off' && flag !== '0' && flag !== 'false';
}

module.exports = {
  COOKIE_NAME,
  IDLE_TTL_MS,
  AUD,
  signCookie,
  readSession,
  requireStudent,
  setSessionCookie,
  clearSessionCookie,
  featureStudentOn,
};
