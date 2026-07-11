const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { fetchBuffer } = require('./http');
const { ensureDir, root } = require('./rawStore');
const { findLastDateHint } = require('./dates');
const { classifySelectionText } = require(path.join(root, 'shared', 'jobSchema'));

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
async function extractPdf(url, counter = { count: 0, max: 25 }) {
  if (!pdfParse) {
    return { ok: false, error: 'pdf-parse not installed', url };
  }
  if (counter.count >= counter.max) {
    return { ok: false, error: 'PDF_MAX_PER_RUN reached', url, skipped: true };
  }

  ensureDir(pdfDir);

  let buffer;
  let hash;
  try {
    // Check existing by URL-derived temp is hard; always download then hash
    const res = await fetchBuffer(url, { maxBytes: 5 * 1024 * 1024 });
    buffer = res.buffer;
    hash = hashBuffer(buffer);
  } catch (err) {
    return { ok: false, error: err.message, url };
  }

  const pdfPath = path.join(pdfDir, `${hash}.pdf`);
  const txtPath = path.join(pdfDir, `${hash}.txt`);

  if (!fs.existsSync(pdfPath)) {
    fs.writeFileSync(pdfPath, buffer);
    counter.count += 1;
  }

  let text = '';
  if (fs.existsSync(txtPath)) {
    text = fs.readFileSync(txtPath, 'utf8');
  } else {
    try {
      const parsed = await pdfParse(buffer);
      text = (parsed.text || '').replace(/\r/g, '');
      fs.writeFileSync(txtPath, text, 'utf8');
      if (!fs.existsSync(pdfPath)) counter.count += 1;
    } catch (err) {
      return { ok: false, error: `PDF parse failed: ${err.message}`, url, hash, pdfPath };
    }
  }

  const classified = classifySelectionText(text);
  const lastDate = findLastDateHint(text);
  const lines = text
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean);
  const titleGuess = lines.find((l) => l.length > 12 && l.length < 180) || null;

  return {
    ok: true,
    url,
    hash,
    pdfPath,
    txtPath,
    text,
    excerpt: text.slice(0, 800).replace(/\s+/g, ' ').trim(),
    titleGuess,
    lastDate,
    classified,
  };
}

function isPdfUrl(url) {
  return /\.pdf(\?|#|$)/i.test(url || '');
}

module.exports = { extractPdf, isPdfUrl, pdfDir };
