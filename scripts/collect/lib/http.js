const DEFAULT_UA =
  'NoExamSarkariBot/1.0 (+https://github.com/local/govt-jobs-portal; research aggregator)';

function noteFetch(url, phase) {
  try {
    require('./collectProgress').setCurrent({ url, phase: phase || 'fetch' });
  } catch {
    /* progress is optional */
  }
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

/**
 * Fetch text with retries, timeout, and polite delay.
 */
async function fetchText(url, options = {}) {
  const {
    timeoutMs = 20000,
    retries = 2,
    delayMs = 1200,
    headers = {},
  } = options;

  let lastError;
  for (let attempt = 0; attempt <= retries; attempt++) {
    if (delayMs) {
      await sleep(attempt === 0 ? Math.min(delayMs, 400) : delayMs * attempt);
    }
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      noteFetch(url, 'fetch');
      const res = await fetch(url, {
        signal: controller.signal,
        headers: {
          'User-Agent': DEFAULT_UA,
          Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
          'Accept-Language': 'en-IN,en;q=0.9',
          ...headers,
        },
        redirect: 'follow',
      });
      clearTimeout(timer);
      if ((res.status === 429 || res.status >= 500) && attempt < retries) {
        lastError = new Error(`HTTP ${res.status} for ${url}`);
        continue;
      }
      if (!res.ok) {
        throw new Error(`HTTP ${res.status} for ${url}`);
      }
      const text = await res.text();
      return { url: res.url || url, status: res.status, text };
    } catch (err) {
      clearTimeout(timer);
      lastError = err;
      if (attempt >= retries) break;
    }
  }
  throw lastError || new Error(`Failed to fetch ${url}`);
}

/**
 * Fetch binary buffer (for PDFs).
 */
async function fetchBuffer(url, options = {}) {
  const { timeoutMs = 30000, retries = 1, delayMs = 800, maxBytes = 5 * 1024 * 1024 } = options;
  let lastError;
  for (let attempt = 0; attempt <= retries; attempt++) {
    if (delayMs) await sleep(attempt === 0 ? 300 : delayMs);
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      noteFetch(url, 'pdf');
      const res = await fetch(url, {
        signal: controller.signal,
        headers: { 'User-Agent': DEFAULT_UA, Accept: 'application/pdf,*/*' },
        redirect: 'follow',
      });
      clearTimeout(timer);
      if (!res.ok) throw new Error(`HTTP ${res.status} for ${url}`);
      const len = Number(res.headers.get('content-length') || 0);
      if (len > maxBytes) throw new Error(`PDF too large (${len} bytes): ${url}`);
      const ab = await res.arrayBuffer();
      if (ab.byteLength > maxBytes) throw new Error(`PDF too large (${ab.byteLength} bytes): ${url}`);
      return { url: res.url || url, status: res.status, buffer: Buffer.from(ab) };
    } catch (err) {
      clearTimeout(timer);
      lastError = err;
      if (attempt >= retries) break;
    }
  }
  throw lastError || new Error(`Failed to fetch buffer ${url}`);
}

async function contentTypeOf(url, options = {}) {
  const { timeoutMs = 10000 } = options;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      method: 'HEAD',
      signal: controller.signal,
      headers: { 'User-Agent': DEFAULT_UA, Accept: '*/*' },
      redirect: 'follow',
    });
    clearTimeout(timer);
    return { url: res.url || url, status: res.status, contentType: res.headers.get('content-type') || '' };
  } catch (err) {
    clearTimeout(timer);
    return { url, status: 0, contentType: '', error: err.message };
  }
}

function isPdfContentType(contentType) {
  return /application\/pdf|application\/x-pdf|application\/octet-stream/i.test(contentType || '');
}

module.exports = { fetchText, fetchBuffer, contentTypeOf, isPdfContentType, sleep, DEFAULT_UA };
