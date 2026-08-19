const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const opsAuth = require('./opsAuth');

// Repo data/ops/operators.json (gitignored). Rotate/remove OPERATOR_PASSWORD after first admin.
const defaultPath = path.join(__dirname, '..', '..', '..', 'data', 'ops', 'operators.json');

function storePath() {
  return process.env.OPERATOR_STORE_PATH || defaultPath;
}

function warnIfStoreUnwritable() {
  if (process.env.NODE_ENV !== 'production') return;
  const file = storePath();
  const dir = path.dirname(file);
  try {
    fs.mkdirSync(dir, { recursive: true });
    fs.accessSync(dir, fs.constants.W_OK);
  } catch {
    console.warn(
      `Operator store is not writable (${file}). On ephemeral disks the table is lost on sleep/redeploy and OPERATOR_PASSWORD will bootstrap again — rotate/remove OPERATOR_PASSWORD after the first admin.`
    );
  }
}

function emptyStore() {
  return { operators: [] };
}

function load() {
  try {
    const raw = fs.readFileSync(storePath(), 'utf8');
    const data = JSON.parse(raw);
    if (!data || !Array.isArray(data.operators)) return emptyStore();
    return { operators: data.operators };
  } catch {
    return emptyStore();
  }
}

function save(data) {
  const file = storePath();
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const tmp = `${file}.${process.pid}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify({ operators: data.operators || [] }, null, 2), 'utf8');
  try {
    fs.renameSync(tmp, file);
  } catch (err) {
    try {
      if (fs.existsSync(file)) fs.unlinkSync(file);
      fs.renameSync(tmp, file);
    } catch (renameErr) {
      try {
        fs.unlinkSync(tmp);
      } catch {
        /* ignore */
      }
      throw renameErr || err;
    }
  }
}

function publicOperator(op) {
  if (!op) return null;
  return {
    id: op.id,
    username: op.username,
    role: op.role,
    createdAt: op.createdAt,
  };
}

function normalizeUsername(username) {
  return String(username || '').trim();
}

function findByUsername(username) {
  const key = normalizeUsername(username).toLowerCase();
  if (!key) return null;
  return load().operators.find((op) => String(op.username).toLowerCase() === key) || null;
}

function list() {
  return load().operators.map(publicOperator);
}

function count() {
  return load().operators.length;
}

function validationError(message) {
  const err = new Error(message);
  err.code = 'VALIDATION';
  return err;
}

function create({ username, password, role = 'operator' } = {}) {
  const name = normalizeUsername(username);
  if (!/^[A-Za-z0-9._-]{3,32}$/.test(name)) {
    throw validationError('Username must be 3–32 letters, numbers, dot, underscore, or hyphen');
  }
  if (typeof password !== 'string' || password.length < 8) {
    throw validationError('Password must be at least 8 characters');
  }
  const nextRole = role === 'admin' ? 'admin' : 'operator';
  const data = load();
  if (data.operators.some((op) => String(op.username).toLowerCase() === name.toLowerCase())) {
    const err = new Error('Username already exists');
    err.code = 'DUPLICATE';
    throw err;
  }
  const op = {
    id: crypto.randomUUID(),
    username: name,
    passwordHash: opsAuth.hash(password),
    role: nextRole,
    createdAt: new Date().toISOString(),
  };
  data.operators.push(op);
  save(data);
  return publicOperator(op);
}

function verifyPassword(username, password) {
  const op = findByUsername(username);
  if (!op || !op.passwordHash) {
    opsAuth.verify(typeof password === 'string' ? password : '', 'scrypt$16384$8$1$00$00');
    return null;
  }
  if (!opsAuth.verify(password, op.passwordHash)) return null;
  return publicOperator(op);
}

function bootstrapIfEmpty(password = process.env.OPERATOR_PASSWORD) {
  warnIfStoreUnwritable();
  if (count() > 0) {
    return { created: false, reason: 'not-empty' };
  }
  if (!password) {
    return { created: false, reason: 'no-bootstrap-password' };
  }
  const operator = create({
    username: 'admin',
    password,
    role: 'admin',
  });
  return { created: true, operator };
}

module.exports = {
  list,
  count,
  create,
  verifyPassword,
  bootstrapIfEmpty,
  findByUsername,
  storePath,
  warnIfStoreUnwritable,
};
