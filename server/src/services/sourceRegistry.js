/**
 * Read/write data/sources/registry.json for the admin sources desk.
 */
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..', '..', '..');

const METHODS = new Set(['html_scrape', 'browser_scrape', 'pdf_watch', 'manual']);
const PRIORITIES = new Set(['P0', 'P1', 'P2', 'P3']);
const CATEGORIES = new Set([
  'aggregator',
  'manual',
  'staffing',
  'psu_careers',
  'apprenticeship',
  'board',
  'psc',
  'calendar',
  'other',
]);
const ORG_TYPES = new Set(['central', 'psu', 'govt_company', 'autonomous', 'state', 'other']);

const PATCH_FIELDS = [
  'name',
  'category',
  'baseUrl',
  'listUrls',
  'orgTypeDefault',
  'priority',
  'method',
  'cadence',
  'enabled',
  'collector',
  'render',
  'robotsNotes',
  'owner',
  'autoPublish',
  'rateLimitMs',
];

function registryPath() {
  return process.env.REGISTRY_PATH || path.join(root, 'data', 'sources', 'registry.json');
}

function nowIso() {
  return new Date().toISOString().slice(0, 10);
}

function readRegistry() {
  const file = registryPath();
  const raw = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (!raw || !Array.isArray(raw.sources)) {
    const err = new Error('Registry is missing sources[]');
    err.code = 'VALIDATION';
    throw err;
  }
  return raw;
}

function writeRegistry(data) {
  const file = registryPath();
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const tmp = `${file}.${process.pid}.tmp`;
  const next = { ...data, updatedAt: nowIso() };
  fs.writeFileSync(tmp, `${JSON.stringify(next, null, 2)}\n`, 'utf8');
  fs.renameSync(tmp, file);
  return next;
}

function listSources() {
  return readRegistry();
}

function getSource(sourceId) {
  const registry = readRegistry();
  const source = (registry.sources || []).find((s) => s.sourceId === sourceId);
  return source || null;
}

function slugOk(id) {
  return /^[a-z][a-z0-9_]{1,63}$/.test(String(id || ''));
}

function validation(message) {
  const err = new Error(message);
  err.code = 'VALIDATION';
  return err;
}

function normalizeListUrls(value) {
  if (value == null) return undefined;
  if (typeof value === 'string') {
    return value
      .split(/\r?\n|,/)
      .map((s) => s.trim())
      .filter(Boolean);
  }
  if (!Array.isArray(value)) throw validation('listUrls must be an array of https URLs');
  return value.map((u) => String(u || '').trim()).filter(Boolean);
}

function applyPatch(current, patch) {
  const next = { ...current };
  for (const key of PATCH_FIELDS) {
    if (!Object.prototype.hasOwnProperty.call(patch, key)) continue;
    let value = patch[key];
    if (key === 'listUrls') value = normalizeListUrls(value);
    if (key === 'enabled' || key === 'autoPublish') value = Boolean(value);
    if (key === 'rateLimitMs') {
      const n = Number(value);
      if (!Number.isFinite(n) || n < 0) throw validation('rateLimitMs must be a non-negative number');
      value = Math.round(n);
    }
    if (key === 'name') {
      value = String(value || '').trim();
      if (!value) throw validation('name is required');
    }
    if (key === 'method' && value && !METHODS.has(value)) {
      throw validation(`method must be one of ${[...METHODS].join(', ')}`);
    }
    if (key === 'priority' && value && !PRIORITIES.has(value)) {
      throw validation('priority must be P0, P1, P2, or P3');
    }
    if (key === 'category' && value && !CATEGORIES.has(value)) {
      throw validation(`category must be one of ${[...CATEGORIES].join(', ')}`);
    }
    if (key === 'orgTypeDefault' && value && !ORG_TYPES.has(value)) {
      throw validation(`orgTypeDefault must be one of ${[...ORG_TYPES].join(', ')}`);
    }
    if (key === 'baseUrl' || (key === 'listUrls' && Array.isArray(value))) {
      const urls = key === 'baseUrl' ? (value ? [value] : []) : value;
      for (const url of urls) {
        if (!url) continue;
        try {
          const parsed = new URL(url);
          if (parsed.protocol !== 'https:') throw new Error('https only');
        } catch {
          throw validation(`${key} must be https URL(s)`);
        }
      }
    }
    if (value === undefined) continue;
    if (value === '' && (key === 'collector' || key === 'render' || key === 'cadence' || key === 'owner')) {
      delete next[key];
      continue;
    }
    next[key] = value;
  }
  return next;
}

function updateSource(sourceId, patch) {
  const registry = readRegistry();
  const idx = (registry.sources || []).findIndex((s) => s.sourceId === sourceId);
  if (idx < 0) return null;
  const body = patch && typeof patch === 'object' ? patch : {};
  registry.sources[idx] = applyPatch(registry.sources[idx], body);
  writeRegistry(registry);
  return registry.sources[idx];
}

function createSource(input) {
  const body = input && typeof input === 'object' ? input : {};
  const sourceId = String(body.sourceId || '').trim();
  if (!slugOk(sourceId)) throw validation('sourceId must be a slug like ncs_gov or upsc_calendar');
  const registry = readRegistry();
  if ((registry.sources || []).some((s) => s.sourceId === sourceId)) {
    const err = new Error(`Source ${sourceId} already exists`);
    err.code = 'DUPLICATE';
    throw err;
  }
  const seed = {
    sourceId,
    name: String(body.name || '').trim(),
    category: body.category || 'other',
    baseUrl: body.baseUrl || '',
    listUrls: normalizeListUrls(body.listUrls) || [],
    orgTypeDefault: body.orgTypeDefault || 'central',
    priority: body.priority || 'P2',
    method: body.method || 'html_scrape',
    cadence: body.cadence || 'daily',
    enabled: body.enabled !== false,
    robotsNotes: body.robotsNotes || '',
    owner: body.owner || 'curator',
  };
  if (!seed.name) throw validation('name is required');
  const source = applyPatch(seed, body);
  registry.sources.push(source);
  writeRegistry(registry);
  return source;
}

function collectUrlOf(source) {
  if (!source) return '';
  const urls = [
    ...(Array.isArray(source.listUrls) ? source.listUrls : []),
    source.baseUrl,
  ].filter(Boolean);
  return urls[0] || '';
}

module.exports = {
  METHODS,
  PRIORITIES,
  CATEGORIES,
  PATCH_FIELDS,
  registryPath,
  listSources,
  getSource,
  updateSource,
  createSource,
  collectUrlOf,
};
