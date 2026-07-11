const MONTHS = {
  jan: 0,
  january: 0,
  feb: 1,
  february: 1,
  mar: 2,
  march: 2,
  apr: 3,
  april: 3,
  may: 4,
  jun: 5,
  june: 5,
  jul: 6,
  july: 6,
  aug: 7,
  august: 7,
  sep: 8,
  sept: 8,
  september: 8,
  oct: 9,
  october: 9,
  nov: 10,
  november: 10,
  dec: 11,
  december: 11,
};

function toIso(y, m, d) {
  const dt = new Date(Date.UTC(y, m, d));
  if (Number.isNaN(dt.getTime())) return null;
  return dt.toISOString().slice(0, 10);
}

function parseDateFromText(text) {
  if (!text) return null;
  const s = String(text);

  let m = s.match(/\b(20\d{2})[-\/](\d{1,2})[-\/](\d{1,2})\b/);
  if (m) return toIso(+m[1], +m[2] - 1, +m[3]);

  m = s.match(/\b(\d{1,2})[-\/.](\d{1,2})[-\/.](20\d{2})\b/);
  if (m) return toIso(+m[3], +m[2] - 1, +m[1]);

  m = s.match(/\b(\d{1,2})(?:st|nd|rd|th)?[\s,]+([A-Za-z]{3,9})[\s,]+(20\d{2})\b/);
  if (m) {
    const mon = MONTHS[m[2].toLowerCase()];
    if (mon != null) return toIso(+m[3], mon, +m[1]);
  }

  m = s.match(/\b([A-Za-z]{3,9})\s+(\d{1,2})(?:st|nd|rd|th)?[,\s]+(20\d{2})\b/);
  if (m) {
    const mon = MONTHS[m[1].toLowerCase()];
    if (mon != null) return toIso(+m[3], mon, +m[2]);
  }

  return null;
}

function findLastDateHint(text) {
  if (!text) return null;
  const s = String(text);
  const labeled = s.match(
    /(?:last\s*date|closing\s*date|apply\s*by|last\s*date\s*to\s*apply|walk[\s-]?in\s*date|date\s*of\s*walk)[:\s-]*([^\n|;]{6,50})/i
  );
  if (labeled) {
    const d = parseDateFromText(labeled[1]);
    if (d) return d;
  }
  return parseDateFromText(s);
}

module.exports = { parseDateFromText, findLastDateHint };
