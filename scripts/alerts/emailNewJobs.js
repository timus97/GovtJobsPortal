/**
 * Diff jobs.json vs jobs.prev.json and email a digest of new open jobs.
 */
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..', '..');
const jobsPath = path.join(root, 'data', 'processed', 'jobs.json');
const prevPath = path.join(root, 'data', 'processed', 'jobs.prev.json');
const reportPath = path.join(root, 'data', 'processed', 'alert-report.json');

function readJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return fallback;
  }
}

function writeReport(report) {
  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), 'utf8');
}

function loadEnvFile() {
  const envPath = path.join(root, '.env');
  if (!fs.existsSync(envPath)) return;
  const lines = fs.readFileSync(envPath, 'utf8').split(/\r?\n/);
  for (const line of lines) {
    if (!line || line.trim().startsWith('#')) continue;
    const i = line.indexOf('=');
    if (i < 0) continue;
    const key = line.slice(0, i).trim();
    let val = line.slice(i + 1).trim();
    if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
      val = val.slice(1, -1);
    }
    if (process.env[key] === undefined) process.env[key] = val;
  }
}

function buildBodies(newJobs) {
  const site = process.env.PUBLIC_SITE_URL || 'http://localhost:5173';
  const lines = newJobs.map((j) => {
    return `• ${j.title}\n  ${j.organization} | ${j.selectionProcess || ''} | Last: ${j.lastDate || '—'}\n  ${j.officialUrl}\n  Portal: ${site}/jobs/${j.id}\n`;
  });
  const text = `New government/PSU jobs (${newJobs.length}):\n\n${lines.join('\n')}\n— Sarkari Jobs bot\n`;

  const htmlItems = newJobs
    .map(
      (j) => `<li style="margin-bottom:12px">
      <strong>${escapeHtml(j.title)}</strong><br/>
      ${escapeHtml(j.organization)} · ${escapeHtml(j.selectionProcess || '')} · Last date: ${escapeHtml(j.lastDate || '—')}<br/>
      <a href="${escapeHtml(j.officialUrl)}">Official notification</a>
      ${j.id ? ` · <a href="${escapeHtml(site)}/jobs/${escapeHtml(j.id)}">View on portal</a>` : ''}
    </li>`
    )
    .join('');

  const html = `<div style="font-family:system-ui,sans-serif;line-height:1.45">
    <h2>New government/PSU jobs (${newJobs.length})</h2>
    <p>Central / PSU / govt company openings (as classified).</p>
    <ul>${htmlItems}</ul>
    <p style="color:#666;font-size:12px">Always verify on the official site before applying. Not affiliated with GoI/PSUs.</p>
  </div>`;

  return { text, html };
}

function escapeHtml(s) {
  return String(s || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

async function sendMail({ subject, text, html }) {
  let nodemailer;
  try {
    nodemailer = require('nodemailer');
  } catch {
    throw new Error('nodemailer not installed');
  }

  const transporter = nodemailer.createTransport({
    host: process.env.SMTP_HOST,
    port: Number(process.env.SMTP_PORT || 587),
    secure: process.env.SMTP_SECURE === 'true',
    auth: process.env.SMTP_USER
      ? { user: process.env.SMTP_USER, pass: process.env.SMTP_PASS }
      : undefined,
  });

  await transporter.sendMail({
    from: process.env.SMTP_FROM || process.env.SMTP_USER,
    to: process.env.ALERT_TO,
    subject,
    text,
    html,
  });
}

async function main() {
  loadEnvFile();
  const enabled = process.env.ALERT_ENABLED === 'true';
  const onFirst = process.env.ALERT_ON_FIRST_RUN === 'true';
  const now = new Date().toISOString();

  const jobs = readJson(jobsPath, []);
  const prev = fs.existsSync(prevPath) ? readJson(prevPath, []) : null;

  if (!enabled) {
    writeReport({ at: now, sent: false, reason: 'ALERT_ENABLED not true', newCount: 0 });
    console.log('Alerts disabled (set ALERT_ENABLED=true to send).');
    return;
  }

  if (!process.env.SMTP_HOST || !process.env.ALERT_TO) {
    writeReport({ at: now, sent: false, reason: 'Missing SMTP_HOST or ALERT_TO', newCount: 0 });
    console.log('Missing SMTP_HOST or ALERT_TO — skip send.');
    return;
  }

  if (prev === null && !onFirst) {
    writeReport({
      at: now,
      sent: false,
      reason: 'No jobs.prev.json (set ALERT_ON_FIRST_RUN=true to alert on first run)',
      newCount: 0,
    });
    console.log('No previous snapshot — skip first-run spam. Run snapshot after this.');
    return;
  }

  const prevIds = new Set((prev || []).map((j) => j.id));
  const newJobs = jobs.filter(
    (j) => !prevIds.has(j.id) && (j.status === 'open' || j.status === 'closing_soon')
  );

  if (newJobs.length === 0) {
    writeReport({ at: now, sent: false, reason: 'no_new_jobs', newCount: 0 });
    console.log('No new open jobs to alert.');
    return;
  }

  const { text, html } = buildBodies(newJobs.slice(0, 40));
  const subject = `[Sarkari Jobs] ${newJobs.length} new government/PSU job(s)`;

  try {
    await sendMail({ subject, text, html });
    writeReport({
      at: now,
      sent: true,
      newCount: newJobs.length,
      ids: newJobs.map((j) => j.id),
      to: process.env.ALERT_TO,
    });
    console.log(`Alert email sent for ${newJobs.length} job(s) → ${process.env.ALERT_TO}`);
  } catch (err) {
    writeReport({ at: now, sent: false, reason: err.message, newCount: newJobs.length });
    console.error('Failed to send alert:', err.message);
    process.exitCode = 1;
  }
}

main();
