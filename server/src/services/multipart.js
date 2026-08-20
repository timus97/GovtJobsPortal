const MAX_DEFAULT = 5 * 1024 * 1024 + 65536;

function fail(code, message) {
  const err = new Error(message);
  err.code = code;
  throw err;
}

function parseBoundary(contentType) {
  const m = String(contentType || '').match(/boundary=(?:"([^"]+)"|([^;]+))/i);
  if (!m) return null;
  return (m[1] || m[2] || '').trim();
}

function indexOf(buf, seq, start) {
  const from = start || 0;
  if (from >= buf.length) return -1;
  return buf.indexOf(seq, from);
}

function readBody(req, maxBytes) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    let done = false;
    function finish(err, buf) {
      if (done) return;
      done = true;
      if (err) reject(err);
      else resolve(buf);
    }
    req.on('data', (chunk) => {
      if (done) return;
      size += chunk.length;
      if (size > maxBytes) {
        req.destroy();
        return finish(Object.assign(new Error('File must be 5 MB or smaller'), { code: 'TOO_LARGE' }));
      }
      chunks.push(chunk);
    });
    req.on('end', () => finish(null, Buffer.concat(chunks)));
    req.on('error', (err) => finish(err));
    req.on('aborted', () => finish(Object.assign(new Error('Upload aborted'), { code: 'VALIDATION' })));
  });
}

function extractFilePart(buf, boundary, fieldName) {
  const dashBoundary = Buffer.from(`--${boundary}`);
  let pos = indexOf(buf, dashBoundary, 0);
  if (pos < 0) fail('VALIDATION', 'Malformed multipart body');
  pos += dashBoundary.length;

  while (pos < buf.length) {
    if (buf[pos] === 0x2d && buf[pos + 1] === 0x2d) break;
    if (buf[pos] === 0x0d && buf[pos + 1] === 0x0a) pos += 2;
    const headerEnd = indexOf(buf, Buffer.from('\r\n\r\n'), pos);
    if (headerEnd < 0) fail('VALIDATION', 'Malformed multipart part');
    const headers = buf.slice(pos, headerEnd).toString('utf8');
    const bodyStart = headerEnd + 4;
    const next = indexOf(buf, Buffer.from(`\r\n--${boundary}`), bodyStart);
    if (next < 0) fail('VALIDATION', 'Malformed multipart part');
    const body = buf.slice(bodyStart, next);
    const nameMatch = headers.match(/\bname="([^"]+)"/i);
    const fileMatch = headers.match(/\bfilename="([^"]*)"/i);
    const fileStar = headers.match(/\bfilename\*=(?:UTF-8'')?([^;\r\n]+)/i);
    const name = nameMatch ? nameMatch[1] : '';
    if (name === fieldName || (fieldName && name === fieldName)) {
      let originalName = fileMatch ? fileMatch[1] : '';
      if (fileStar) {
        try {
          originalName = decodeURIComponent(String(fileStar[1]).trim().replace(/^UTF-8''/i, ''));
        } catch {
          /* keep filename= */
        }
      }
      return { fieldName: name || fieldName, originalName, buffer: body };
    }
    pos = next + 2 + dashBoundary.length;
  }
  fail('VALIDATION', 'No file field in upload');
}

async function readMultipartFile(req, opts = {}) {
  const maxBytes = Number(opts.maxBytes) > 0 ? Number(opts.maxBytes) : MAX_DEFAULT;
  const fieldName = opts.fieldName || 'file';
  const boundary = parseBoundary(req.headers && req.headers['content-type']);
  if (!boundary) fail('VALIDATION', 'Expected multipart/form-data');
  const buf = await readBody(req, maxBytes);
  return extractFilePart(buf, boundary, fieldName);
}

module.exports = {
  parseBoundary,
  extractFilePart,
  readMultipartFile,
};
