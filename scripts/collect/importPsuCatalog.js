/**
 * Parse Wikipedia PSU page wikitext + domain map → merge into registry.json
 *
 * Usage:
 *   node scripts/collect/importPsuCatalog.js
 *   node scripts/collect/importPsuCatalog.js --fetch   # re-download wiki wikitext
 */
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..', '..');

const wikiPath = path.join(root, 'data', 'raw', 'wiki_psu.wikitext');
const domainPath = path.join(root, 'data', 'sources', 'psu_domains.json');
const registryPath = path.join(root, 'data', 'sources', 'registry.json');
const catalogOut = path.join(root, 'data', 'sources', 'psu_catalog.json');

function slugify(name) {
  return String(name)
    .toLowerCase()
    .replace(/&/g, ' and ')
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_|_$/g, '')
    .slice(0, 48);
}

function cleanWikiName(raw) {
  let s = String(raw || '');
  // [[Link|Label]] or [[Link]]
  s = s.replace(/\[\[([^\]|]+)(?:\|([^\]]+))?\]\]/g, (_, a, b) => (b || a).trim());
  s = s.replace(/\{\{[^}]+\}\}/g, '');
  s = s.replace(/<ref[\s\S]*?(<\/ref>|\/\s*>)/gi, '');
  s = s.replace(/'''?/g, '');
  s = s.replace(/\(formerly[^)]+\)/gi, '');
  s = s.replace(/\s+/g, ' ').trim();
  // Drop trailing parenthetical short names for matching, keep short separately
  return s;
}

function extractShort(raw) {
  const m = String(raw).match(/\(([A-Z][A-Z0-9&-]{1,12})\)\s*$/);
  return m ? m[1] : null;
}

function parseWikiLists(text) {
  const sections = [
    { key: 'maharatna', re: /===List of Maharatna===([\s\S]*?)(?====List of |===List of Miniratna|$)/i },
    { key: 'navratna', re: /===List of Navratna===([\s\S]*?)(?====List of |===List of Miniratna|$)/i },
    { key: 'miniratna_i', re: /;Miniratna Category-I([\s\S]*?)(?=;Miniratna Category-II|===List of Other|$)/i },
    { key: 'miniratna_ii', re: /;Miniratna Category-II[^\n]*\n([\s\S]*?)(?====List of Other|===|$)/i },
    { key: 'other_cpsu', re: /===List of Other CPSUs===([\s\S]*?)(?====|\n==[^=]|$)/i },
  ];

  const items = [];
  for (const sec of sections) {
    const m = text.match(sec.re);
    if (!m) continue;
    const body = m[1];
    const lines = body.split('\n');
    for (const line of lines) {
      if (!/^\s*#\s+/.test(line)) continue;
      const raw = line.replace(/^\s*#\s+/, '').trim();
      if (!raw || raw.length < 3) continue;
      const name = cleanWikiName(raw);
      if (!name || name.length < 3) continue;
      if (/^Other CPSEs/i.test(name)) continue;
      items.push({
        name,
        short: extractShort(raw),
        ratna: sec.key,
        wikiRaw: raw.slice(0, 200),
      });
    }
  }
  return items;
}

function findDomain(name, short, domains) {
  const keys = Object.keys(domains);
  const n = name.toLowerCase();
  // exact
  if (domains[name]) return { key: name, ...domains[name] };
  // short match
  if (short) {
    for (const k of keys) {
      const d = domains[k];
      if (d.short && d.short.toLowerCase() === short.toLowerCase()) return { key: k, ...d };
    }
  }
  // includes
  for (const k of keys) {
    const kl = k.toLowerCase();
    if (n.includes(kl) || kl.includes(n) || n.includes((domains[k].short || '').toLowerCase())) {
      return { key: k, ...domains[k] };
    }
  }
  // token overlap
  const tokens = n.split(/[^a-z0-9]+/).filter((t) => t.length > 3);
  let best = null;
  let bestScore = 0;
  for (const k of keys) {
    const kt = k.toLowerCase().split(/[^a-z0-9]+/).filter((t) => t.length > 3);
    const score = tokens.filter((t) => kt.includes(t)).length;
    if (score > bestScore && score >= 2) {
      bestScore = score;
      best = { key: k, ...domains[k] };
    }
  }
  return best;
}

function careerProbes(baseUrl) {
  if (!baseUrl) return [];
  try {
    const u = new URL(baseUrl);
    const origin = u.origin;
    return [
      `${origin}/careers`,
      `${origin}/career`,
      `${origin}/recruitment`,
      `${origin}/jobs`,
      `${origin}/en/careers`,
      `${origin}/en/career`,
      `${origin}/careers/current-openings`,
      `${origin}/human-resources/careers`,
      `${origin}/Career`,
      `${origin}/Careers`,
    ];
  } catch {
    return [];
  }
}

function toSourceEntry(item, domain, priority) {
  const short = item.short || domain?.short || '';
  const sourceId = `psu_${slugify(short || item.name)}`;
  const baseUrl = domain?.baseUrl || '';
  const listUrls = [
    ...(domain?.listUrls || []),
    ...careerProbes(baseUrl),
  ].filter(Boolean);
  // unique
  const uniq = [...new Set(listUrls.map((x) => x.replace(/\/$/, '')))].map((x) =>
    x.includes('://') ? (x.endsWith('/') ? x : x) : x
  );

  return {
    sourceId,
    name: `${item.name}${short ? ` (${short})` : ''} Careers`,
    category: 'psu_careers',
    baseUrl: baseUrl || `https://en.wikipedia.org/wiki/${encodeURIComponent(item.name.replace(/ /g, '_'))}`,
    listUrls: uniq.slice(0, 8),
    orgTypeDefault: 'psu',
    sector: domain?.sector || 'PSU',
    ratna: item.ratna,
    priority,
    method: baseUrl ? 'html_scrape' : 'manual',
    cadence: item.ratna === 'maharatna' || item.ratna === 'navratna' ? 'daily' : 'weekly',
    enabled: Boolean(baseUrl),
    owner: 'automation',
    wikiName: item.name,
  };
}

async function fetchWiki() {
  const url =
    'https://en.wikipedia.org/w/api.php?action=parse&page=Public_Sector_Undertakings_in_India&prop=wikitext&format=json';
  const res = await fetch(url, {
    headers: { 'User-Agent': 'NoExamSarkariBot/1.0 (PSU catalog import)' },
  });
  if (!res.ok) throw new Error(`Wiki fetch failed: ${res.status}`);
  const data = await res.json();
  const text = data.parse.wikitext['*'];
  fs.mkdirSync(path.dirname(wikiPath), { recursive: true });
  fs.writeFileSync(wikiPath, text, 'utf8');
  console.log(`Fetched wiki wikitext (${text.length} chars)`);
  return text;
}

function priorityFor(ratna) {
  if (ratna === 'maharatna') return 'P1';
  if (ratna === 'navratna') return 'P1';
  if (ratna === 'miniratna_i') return 'P2';
  if (ratna === 'miniratna_ii') return 'P2';
  return 'P3';
}

async function main() {
  const doFetch = process.argv.includes('--fetch');
  let wikiText;
  if (doFetch || !fs.existsSync(wikiPath)) {
    wikiText = await fetchWiki();
  } else {
    wikiText = fs.readFileSync(wikiPath, 'utf8');
    console.log(`Using cached wiki wikitext (${wikiText.length} chars)`);
  }

  const domains = JSON.parse(fs.readFileSync(domainPath, 'utf8')).domains || {};
  const parsed = parseWikiLists(wikiText);
  console.log(`Parsed ${parsed.length} PSU names from Wikipedia`);

  const catalog = [];
  const seenNames = new Set();
  for (const item of parsed) {
    const key = item.name.toLowerCase();
    if (seenNames.has(key)) continue;
    seenNames.add(key);
    const domain = findDomain(item.name, item.short, domains);
    catalog.push({
      ...item,
      hasDomain: Boolean(domain?.baseUrl),
      domainKey: domain?.key || null,
      baseUrl: domain?.baseUrl || null,
      sector: domain?.sector || null,
    });
  }

  fs.writeFileSync(catalogOut, JSON.stringify({ updatedAt: new Date().toISOString(), count: catalog.length, items: catalog }, null, 2));
  console.log(`Wrote catalog → ${catalogOut}`);

  const registry = JSON.parse(fs.readFileSync(registryPath, 'utf8'));
  const keep = (registry.sources || []).filter(
    (s) => !s.sourceId.startsWith('psu_') && s.category !== 'psu_careers'
  );
  // Keep non-PSU sources; rebuild all psu_careers from catalog
  // Also keep seed, ncs, employment, becil etc.

  const existingNonPsu = (registry.sources || []).filter(
    (s) => s.category !== 'psu_careers' || s.sourceId === 'becil'
  );

  // Preserve becil as staffing
  const psuSources = [];
  const usedIds = new Set(existingNonPsu.map((s) => s.sourceId));

  for (const item of catalog) {
    const domain = findDomain(item.name, item.short, domains);
    // Skip if we already have a non-psu_ duplicate by short name mapping in existing listUrls
    const entry = toSourceEntry(item, domain, priorityFor(item.ratna));
    let id = entry.sourceId;
    let n = 2;
    while (usedIds.has(id)) {
      id = `${entry.sourceId}_${n++}`;
    }
    entry.sourceId = id;
    usedIds.add(id);
    psuSources.push(entry);
  }

  // Remove old dedicated PSU rows that are superseded (ntpc_careers etc.) — all come from catalog now
  const coreSources = (registry.sources || []).filter(
    (s) =>
      s.category !== 'psu_careers' &&
      ![
        'ntpc_careers',
        'iocl_careers',
        'ongc_careers',
        'bhel_careers',
        'sail_careers',
        'powergrid_careers',
        'hal_careers',
        'bel_careers',
      ].includes(s.sourceId)
  );

  registry.sources = [...coreSources, ...psuSources];
  registry.version = (registry.version || 1) + 1;
  registry.updatedAt = new Date().toISOString().slice(0, 10);
  registry.wikiSource = 'https://en.wikipedia.org/wiki/Public_Sector_Undertakings_in_India';
  registry.psuCount = psuSources.length;
  registry.psuEnabled = psuSources.filter((s) => s.enabled).length;

  fs.writeFileSync(registryPath, JSON.stringify(registry, null, 2), 'utf8');
  console.log(
    `Registry updated: ${psuSources.length} PSU sources (${registry.psuEnabled} with known career URLs), total sources ${registry.sources.length}`
  );
  console.log(
    `Without domain (listed but scrape disabled): ${psuSources.filter((s) => !s.enabled).length}`
  );
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
