const crypto = require('crypto');

const COOKIE_NAME = 'ops_session';
const IDLE_TTL_MS = 12 * 60 * 60 * 1000;
const SCRYPT_N = 16384;
const SCRYPT_R = 8;
const SCRYPT_P = 1;
const SCRYPT_KEYLEN = 64;
const SCRYPT_MAXMEM = 64 * 1024 * 1024;

let ephemeralSecret = null;

function getSecret() {
  if (process.env.SESSION_SECRET) return process.env.SESSION_SECRET;
  if (process.env.NODE_ENV === 'production') {
    throw new Error('SESSION_SECRET is required in production');
  }
  if (!ephemeralSecret) {
    ephemeralSecret = crypto.randomBytes(32).toString('hex');
    console.warn(
      'SESSION_SECRET not set; using ephemeral secret (sessions reset on restart)'
    );
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

function safeEqual(a, b) {
  const left = Buffer.isBuffer(a) ? a : Buffer.from(String(a));
  const right = Buffer.isBuffer(b) ? b : Buffer.from(String(b));
  if (left.length !== right.length) {
    crypto.timingSafeEqual(left, Buffer.alloc(left.length));
    return false;
  }
  return crypto.timingSafeEqual(left, right);
}

function hash(password) {
  if (typeof password !== 'string' || password.length === 0) {
    throw new Error('Password is required');
  }
  const salt = crypto.randomBytes(16);
  const key = crypto.scryptSync(password, salt, SCRYPT_KEYLEN, {
    N: SCRYPT_N,
    r: SCRYPT_R,
    p: SCRYPT_P,
    maxmem: SCRYPT_MAXMEM,
  });
  return `scrypt$${SCRYPT_N}$${SCRYPT_R}$${SCRYPT_P}$${salt.toString('hex')}$${key.toString('hex')}`;
}

function verify(password, stored) {
  if (typeof password !== 'string' || typeof stored !== 'string') return false;
  const parts = stored.split('$');
  if (parts[0] === 'scrypt' && parts.length === 6) {
    const [, nStr, rStr, pStr, saltHex, keyHex] = parts;
    const N = Number(nStr);
    const r = Number(rStr);
    const p = Number(pStr);
    if (!N || !r || !p || !saltHex || !keyHex) return false;
    let derived;
    try {
      derived = crypto.scryptSync(password, Buffer.from(saltHex, 'hex'), keyHex.length / 2, {
        N,
        r,
        p,
        maxmem: SCRYPT_MAXMEM,
      });
    } catch {
      return false;
    }
    return safeEqual(derived, Buffer.from(keyHex, 'hex'));
  }
  if (parts[0] === 'pbkdf2' && parts.length === 5) {
    const [, iterStr, digest, saltHex, keyHex] = parts;
    const iterations = Number(iterStr);
    if (!iterations || !saltHex || !keyHex) return false;
    let derived;
    try {
      derived = crypto.pbkdf2Sync(
        password,
        Buffer.from(saltHex, 'hex'),
        iterations,
        keyHex.length / 2,
        digest || 'sha256'
      );
    } catch {
      return false;
    }
    return safeEqual(derived, Buffer.from(keyHex, 'hex'));
  }
  return false;
}

function signCookie(user, opts = {}) {
  const now = opts.now instanceof Date ? opts.now.getTime() : Number(opts.now) || Date.now();
  const ttlMs = Number(opts.ttlMs) > 0 ? Number(opts.ttlMs) : IDLE_TTL_MS;
  const payload = {
    v: 1,
    uid: user.id,
    sub: user.username,
    role: user.role || 'operator',
    iat: Math.floor(now / 1000),
    exp: Math.floor((now + ttlMs) / 1000),
  };
  const body = b64url(JSON.stringify(payload));
  const sig = b64url(crypto.createHmac('sha256', getSecret()).update(body).digest());
  return `${body}.${sig}`;
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
    if (!payload || payload.v !== 1 || !payload.sub || !payload.exp) return null;
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

function requireOps(req, res, next) {
  const session = readSession(req);
  if (!session) {
    return res.status(401).json({ error: 'Unauthorized' });
  }
  req.ops = session;
  setSessionCookie(res, {
    id: session.uid,
    username: session.sub,
    role: session.role || 'operator',
  });
  next();
}

module.exports = {
  COOKIE_NAME,
  IDLE_TTL_MS,
  hash,
  verify,
  signCookie,
  readSession,
  requireOps,
  setSessionCookie,
  clearSessionCookie,
  cookieOptions,
  parseCookies,
};
