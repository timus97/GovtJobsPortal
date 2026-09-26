/**
 * Start / inspect the daily collector (scripts/collect/runDaily.js).
 * Separate from the paste-URL collect queue.
 */
const { spawn } = require('child_process');
const path = require('path');
const { readProgress } = require('../../../scripts/collect/lib/collectProgress');

const root = path.join(__dirname, '..', '..', '..');

let child = null;

function isPidAlive(pid) {
  if (!pid) return false;
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

function status() {
  const progress = readProgress();
  const childLive = Boolean(child && child.exitCode == null);
  const fileLive = Boolean(progress.running && isPidAlive(progress.pid));
  const running = childLive || fileLive;
  return {
    ...progress,
    running,
    pid: (child && child.pid) || progress.pid || null,
  };
}

function start(opts = {}) {
  const current = status();
  if (current.running) {
    const err = new Error('Daily collect is already running');
    err.code = 'BUSY';
    throw err;
  }

  const script = path.join(root, 'scripts', 'collect', 'runDaily.js');
  const args = [script];
  if (opts.source) args.push('--source', String(opts.source));
  if (opts.limit) args.push('--limit', String(opts.limit));
  if (opts.psuOnly) args.push('--psu-only');

  child = spawn(process.execPath, args, {
    cwd: root,
    env: {
      ...process.env,
      COLLECT_HEADLESS: process.env.COLLECT_HEADLESS || 'true',
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  child.stdout.on('data', (buf) => process.stdout.write(buf));
  child.stderr.on('data', (buf) => process.stderr.write(buf));
  child.on('exit', () => {
    child = null;
  });
  child.on('error', () => {
    child = null;
  });

  return {
    ok: true,
    started: true,
    pid: child.pid,
    source: opts.source || null,
    limit: opts.limit || null,
  };
}

module.exports = { status, start };
