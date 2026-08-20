/**
 * Structured eligibility facts + education ladder.
 * v1: map already-structured fields only. Do not NLP-parse eligibility[].
 */

const RESERVATION_CATEGORIES = ['UR', 'EWS', 'OBC', 'SC', 'ST'];

const DISCIPLINES = ['engineering', 'commerce', 'arts', 'science', 'law', 'medical', 'any'];

const GENDERS = ['male', 'female', 'other'];

const PWBD_CATEGORIES = ['VH', 'HH', 'OH', 'others', 'none'];

/** Shared ordinal ladder. pg and postgraduate are the same rank. experience is not a rung. */
const EDUCATION_ORDINAL = {
  below_10: 0,
  '10th': 1,
  '12th': 2,
  iti: 3,
  diploma: 4,
  graduate: 5,
  pg: 6,
  postgraduate: 6,
  phd: 7,
};

const EDUCATION_CODES = [
  'below_10',
  '10th',
  '12th',
  'iti',
  'diploma',
  'graduate',
  'pg',
  'postgraduate',
  'phd',
  'experience',
];

const PROFILE_STORAGE_KEY = 'sarkari.profile.v1';

const VERIFY_OFFICIAL = 'verify on official site';

function parseIsoDate(value) {
  if (value == null || value === '') return null;
  const s = String(value).trim();
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(s);
  if (!m) {
    const d = new Date(s);
    return Number.isNaN(d.getTime()) ? null : d;
  }
  const y = Number(m[1]);
  const mo = Number(m[2]);
  const day = Number(m[3]);
  if (mo < 1 || mo > 12 || day < 1 || day > 31) return null;
  return new Date(Date.UTC(y, mo - 1, day));
}

function formatIsoDate(value) {
  const d = value instanceof Date ? value : parseIsoDate(value);
  if (!d) return null;
  const y = d.getUTCFullYear();
  const mo = String(d.getUTCMonth() + 1).padStart(2, '0');
  const day = String(d.getUTCDate()).padStart(2, '0');
  return `${y}-${mo}-${day}`;
}

/**
 * Completed years of age on asOn (notification date), not today.
 */
function ageOnDate(dob, asOn) {
  const birth = parseIsoDate(dob);
  const on = parseIsoDate(asOn);
  if (!birth || !on) return null;
  let age = on.getUTCFullYear() - birth.getUTCFullYear();
  const monthDelta = on.getUTCMonth() - birth.getUTCMonth();
  if (monthDelta < 0 || (monthDelta === 0 && on.getUTCDate() < birth.getUTCDate())) {
    age -= 1;
  }
  return age;
}

function normalizeQualification(code) {
  if (code == null || code === '') return null;
  const raw = String(code).trim().toLowerCase().replace(/\s+/g, '_');
  if (raw === 'experience') return 'experience';
  if (raw === 'postgraduate' || raw === 'post_graduate' || raw === 'post-graduate') return 'pg';
  if (Object.prototype.hasOwnProperty.call(EDUCATION_ORDINAL, raw)) return raw;
  return null;
}

function isEducationRung(code) {
  const n = normalizeQualification(code);
  return n != null && n !== 'experience';
}

function educationRank(code) {
  const n = normalizeQualification(code);
  if (!isEducationRung(n)) return null;
  return EDUCATION_ORDINAL[n];
}

function compareEducation(have, need) {
  const needNorm = normalizeQualification(need);
  if (needNorm == null || needNorm === 'experience') return 'unknown';
  const haveNorm = normalizeQualification(have);
  if (haveNorm == null || haveNorm === 'experience') return 'unknown';
  return EDUCATION_ORDINAL[haveNorm] >= EDUCATION_ORDINAL[needNorm] ? 'pass' : 'fail';
}

function numberOrNull(value) {
  if (value == null || value === '') return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function normalizeCategory(value) {
  if (value == null || value === '') return null;
  const u = String(value).trim().toUpperCase();
  return RESERVATION_CATEGORIES.includes(u) ? u : null;
}

function normalizeDiscipline(value) {
  if (value == null || value === '') return null;
  const raw = String(value).trim().toLowerCase();
  return DISCIPLINES.includes(raw) ? raw : null;
}

function normalizeGender(value) {
  if (value == null || value === '') return null;
  const raw = String(value).trim().toLowerCase();
  return GENDERS.includes(raw) ? raw : null;
}

function normalizeAgeRelaxation(map) {
  if (!map || typeof map !== 'object' || Array.isArray(map)) return null;
  const out = {};
  for (const cat of RESERVATION_CATEGORIES) {
    if (map[cat] == null) continue;
    const raw = map[cat];
    const years = typeof raw === 'object' ? Number(raw.years) : Number(raw);
    if (Number.isFinite(years) && years >= 0) out[cat] = years;
  }
  return Object.keys(out).length ? out : null;
}

function boolOrNull(value) {
  if (value === true || value === 1 || value === 'true' || value === '1') return true;
  if (value === false || value === 0 || value === 'false' || value === '0') return false;
  return null;
}

function asStringArray(value) {
  if (!Array.isArray(value)) return [];
  return value.map((s) => String(s).trim()).filter(Boolean);
}

function normalizePwbdCategory(value) {
  if (value == null || value === '') return null;
  const raw = String(value).trim();
  if (raw.toLowerCase() === 'others' || raw.toLowerCase() === 'other') return 'others';
  const u = raw.toUpperCase();
  return ['VH', 'HH', 'OH'].includes(u) ? u : null;
}

function normalizeOpenToCategories(value) {
  if (!Array.isArray(value)) return null;
  const out = [];
  for (const item of value) {
    const cat = normalizeCategory(item);
    if (cat && !out.includes(cat)) out.push(cat);
  }
  return out.length ? out : null;
}

/**
 * Structured post list only. Missing pwbdAllowed stays null — never invent suitability.
 */
function normalizePosts(posts) {
  if (!Array.isArray(posts) || !posts.length) return null;
  const out = [];
  posts.forEach((post, index) => {
    if (!post || typeof post !== 'object') return;
    const title = String(post.title || post.name || post.post || '').trim();
    if (!title) return;
    const cats = Array.isArray(post.pwbdCategories)
      ? post.pwbdCategories.map(normalizePwbdCategory).filter(Boolean)
      : null;
    out.push({
      id: post.id || `post-${index + 1}`,
      title,
      pwbdAllowed: boolOrNull(post.pwbdAllowed),
      pwbdCategories: cats && cats.length ? cats : null,
      reservedOnly: boolOrNull(post.reservedOnly),
      openToCategories: normalizeOpenToCategories(post.openToCategories),
    });
  });
  return out.length ? out : null;
}

/**
 * Copy structured facts from an Opportunity or compat job.
 * Never reads eligibility[] free text.
 */
function extractOpportunityFacts(opp) {
  if (!opp || typeof opp !== 'object') {
    return {
      id: null,
      ageMin: null,
      ageMax: null,
      ageAsOnDate: null,
      ageRelaxation: null,
      minEducation: null,
      disciplineRequired: null,
      genderRequired: null,
      domicileRequired: false,
      domicileStates: [],
      pwbdAllowed: null,
      pwbdCategories: null,
      posts: null,
      reservedOnly: null,
      openToCategories: null,
      applicationOpen: null,
      applicationClose: null,
      officialUrl: null,
      notificationUrl: null,
      eligibilityParse: { complete: false },
    };
  }

  const applicationClose = opp.applicationClose || opp.lastDate || null;
  const applicationOpen = opp.applicationOpen || null;
  const minEducation = normalizeQualification(opp.minEducation ?? opp.qualification);
  const ageMin = numberOrNull(opp.ageMin);
  const ageMax = numberOrNull(opp.ageMax);
  const ageAsOnDate = opp.ageAsOnDate ? formatIsoDate(opp.ageAsOnDate) : null;
  const ageRelaxation = normalizeAgeRelaxation(opp.ageRelaxation);
  const disciplineRaw = opp.disciplineRequired ?? opp.discipline ?? null;
  const disciplineRequired =
    disciplineRaw == null || disciplineRaw === ''
      ? null
      : normalizeDiscipline(disciplineRaw) || String(disciplineRaw);
  const genderRequired = normalizeGender(opp.genderRequired);
  const domicileRequired = boolOrNull(opp.domicileRequired) === true;
  const domicileStates = asStringArray(opp.domicileStates);
  const pwbdAllowed = boolOrNull(opp.pwbdAllowed);
  const rawPwbdCats = Array.isArray(opp.pwbdCategories)
    ? opp.pwbdCategories.map(normalizePwbdCategory).filter(Boolean)
    : null;
  const pwbdCategories = rawPwbdCats && rawPwbdCats.length ? rawPwbdCats : null;
  const posts = normalizePosts(opp.posts);
  const reservedOnly = boolOrNull(opp.reservedOnly);
  const openToCategories = normalizeOpenToCategories(opp.openToCategories);

  const bandPresent = ageMin != null && ageMax != null;
  const complete = Boolean(ageAsOnDate && bandPresent && minEducation && applicationClose);

  return {
    id: opp.id || null,
    title: opp.title || null,
    organization: opp.organization || null,
    officialUrl: opp.officialUrl || opp.sourceUrl || null,
    notificationUrl: opp.notificationUrl || opp.officialUrl || null,
    ageMin,
    ageMax,
    ageAsOnDate,
    ageRelaxation,
    minEducation,
    disciplineRequired,
    genderRequired,
    domicileRequired,
    domicileStates,
    pwbdAllowed,
    pwbdCategories,
    posts,
    reservedOnly,
    openToCategories,
    applicationOpen,
    applicationClose,
    eligibilityParse: {
      complete: opp.eligibilityParse && typeof opp.eligibilityParse.complete === 'boolean'
        ? opp.eligibilityParse.complete
        : complete,
    },
  };
}

function validateMatchProfile(profile) {
  const errors = [];
  if (!profile || typeof profile !== 'object') {
    return { ok: false, errors: ['profile is required'] };
  }
  if (!normalizeCategory(profile.reservationCategory)) {
    errors.push('reservationCategory is required (UR|EWS|OBC|SC|ST)');
  }
  if (!parseIsoDate(profile.dob)) {
    errors.push('dob is required');
  }
  const edu = normalizeQualification(profile.highestEducation);
  if (!edu) {
    errors.push('highestEducation is required');
  }
  return { ok: errors.length === 0, errors };
}

module.exports = {
  RESERVATION_CATEGORIES,
  DISCIPLINES,
  GENDERS,
  PWBD_CATEGORIES,
  EDUCATION_ORDINAL,
  EDUCATION_CODES,
  PROFILE_STORAGE_KEY,
  VERIFY_OFFICIAL,
  parseIsoDate,
  formatIsoDate,
  ageOnDate,
  normalizeQualification,
  isEducationRung,
  educationRank,
  compareEducation,
  numberOrNull,
  normalizeCategory,
  normalizeDiscipline,
  normalizeGender,
  normalizeAgeRelaxation,
  boolOrNull,
  normalizePwbdCategory,
  normalizeOpenToCategories,
  normalizePosts,
  extractOpportunityFacts,
  validateMatchProfile,
};
