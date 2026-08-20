const crypto = require('crypto');

const SCRYPT_N = 16384;
const SCRYPT_R = 8;
const SCRYPT_P = 1;
const SCRYPT_KEYLEN = 64;
const SCRYPT_MAXMEM = 64 * 1024 * 1024;

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

module.exports = {
  hash,
  verify,
  safeEqual,
  SCRYPT_N,
  SCRYPT_R,
  SCRYPT_P,
  SCRYPT_KEYLEN,
  SCRYPT_MAXMEM,
};
