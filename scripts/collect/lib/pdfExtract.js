const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { fetchBuffer } = require('./http');
const { ensureDir, root } = require('./rawStore');
const { findLastDateHint } = require('./dates');
const { classifySelectionText } = require(path.join(root, 'shared', 'jobSchema'));
const { isRecruitmentPdfText, NOTICE_TEXT_RE } = require('./jobLinkQuality');

const pdfDir = path.join(root, 'data', 'raw', 'pdfs');

let pdfParse;
try {
  pdfParse = require('pdf-parse');
} catch {
  pdfParse = null;
}

function hashBuffer(buf) {
  return crypto.createHash('sha256').update(buf).digest('hex');
}

/**
 * Download PDF (if needed), extract text, return structured result.
 * Enforces PDF_MAX_PER_RUN via counter object { count, max }.
 */
async function muteStderr(fn) {
  const write = process.stderr.write;
  process.stderr.write = () => true;
  try {
    return await fn();
  } finally {
    process.stderr.write = write;
  }
}

async function extractPdf(url, counter = { count: 0, max: 200, perSourceMax: 6, perSource: {}, skipped: 0 }) {
  if (!pdfParse) {
    return { ok: false, error: 'pdf-parse not installed', url };
  }
  const max = Number(counter.max || process.env.PDF_MAX_PER_RUN || 200);
  const perMax = Number(counter.perSourceMax || process.env.PDF_MAX_PER_SOURCE || 6);
  const sid = counter.sourceId || '_';
  counter.perSource = counter.perSource || {};
  const usedHere = Number(counter.perSource[sid] || 0);
  if (counter.count >= max || usedHere >= perMax) {
    counter.skipped = (counter.skipped || 0) + 1;
    return { ok: false, error: 'PDF cap reached', url, skipped: true };
  }

  ensureDir(pdfDir);

  let buffer;
  let hash;
  try {
    const res = await fetchBuffer(url, { maxBytes: 5 * 1024 * 1024 });
    buffer = res.buffer;
    hash = hashBuffer(buffer);
    counter.count += 1;
    counter.perSource[sid] = usedHere + 1;
  } catch (err) {
    counter.skipped = (counter.skipped || 0) + 1;
    return { ok: false, error: err.message, url };
  }

  const pdfPath = path.join(pdfDir, `${hash}.pdf`);
  const txtPath = path.join(pdfDir, `${hash}.txt`);

  if (!fs.existsSync(pdfPath)) {
    fs.writeFileSync(pdfPath, buffer);
  }

  let text = '';
  if (fs.existsSync(txtPath)) {
    text = fs.readFileSync(txtPath, 'utf8');
  } else {
    try {
      const parsed = await muteStderr(() => pdfParse(buffer));
      text = (parsed.text || '').replace(/\r/g, '');
      fs.writeFileSync(txtPath, text, 'utf8');
    } catch (err) {
      return { ok: false, error: `PDF parse failed: ${err.message}`, url, hash, pdfPath };
    }
  }

  const classified = classifySelectionText(text);
  const lastDate = findLastDateHint(text);
  const fields = parsePdfJobFields(text);

  return {
    ok: true,
    url,
    hash,
    pdfPath,
    txtPath,
    text,
    excerpt: fields.excerpt,
    titleGuess: fields.title,
    lastDate: lastDate || fields.lastDate,
    vacancies: fields.vacancies,
    classified,
    isJobNotice: fields.isJobNotice,
  };
}

function parsePdfJobFields(text) {
  const raw = String(text || '');
  const compact = raw.replace(/\s+/g, ' ').trim();
  const lines = raw
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean);
  const isJobNotice = isRecruitmentPdfText(raw);
  const titled =
    compact.match(
      /(?:engagement|recruitment|walk-?in interview|advertisement|notification)\s+(?:of|for|:)\s+([^.]{12,160})/i
    ) || null;
  const title =
    (titled && titled[1].trim()) ||
    lines.find((l) => NOTICE_TEXT_RE.test(l) && l.length > 12 && l.length < 180) ||
    lines.find((l) => l.length > 12 && l.length < 160) ||
    null;
  const vac = compact.match(
    /(?:no\.?\s*of\s*posts?|number of posts?|vacancies|vacancy)\s*[:\-]?\s*(\d{1,5})/i
  );
  return {
    isJobNotice,
    title: title ? String(title).replace(/\s+/g, ' ').trim() : null,
    vacancies: vac ? Number(vac[1]) : null,
    lastDate: findLastDateHint(raw),
    excerpt: compact.slice(0, 800),
  };
}

function isPdfUrl(url) {
  return /\.pdf(\?|#|$)/i.test(url || '');
}

module.exports = { extractPdf, isPdfUrl, parsePdfJobFields, pdfDir };
