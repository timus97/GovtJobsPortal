const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..', '..', '..');

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function rawDir(sourceId, runId) {
  return path.join(root, 'data', 'raw', sourceId, runId);
}

function saveRaw(sourceId, runId, filename, content) {
  const dir = rawDir(sourceId, runId);
  ensureDir(dir);
  const safe = String(filename).replace(/[^\w.\-]+/g, '_').slice(0, 120);
  const full = path.join(dir, safe);
  if (Buffer.isBuffer(content)) fs.writeFileSync(full, content);
  else fs.writeFileSync(full, content, 'utf8');
  return full;
}

function writeStaging(sourceId, runId, records) {
  const dir = path.join(root, 'data', 'staging', sourceId);
  ensureDir(dir);
  const full = path.join(dir, `${runId}.json`);
  fs.writeFileSync(full, JSON.stringify(records, null, 2), 'utf8');
  return full;
}

function writeJson(relOrAbs, data) {
  const full = path.isAbsolute(relOrAbs) ? relOrAbs : path.join(root, relOrAbs);
  ensureDir(path.dirname(full));
  fs.writeFileSync(full, JSON.stringify(data, null, 2), 'utf8');
  return full;
}

function writeCollectReport(report) {
  return writeJson(path.join(root, 'data', 'processed', 'collect-report.json'), report);
}

module.exports = {
  root,
  ensureDir,
  saveRaw,
  writeStaging,
  writeJson,
  writeCollectReport,
  rawDir,
};
