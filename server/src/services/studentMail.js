/**
 * Student mail: Resend (free API) first, SMTP second.
 * Loads repo-root .env the same way the alert script does.
 */
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..', '..', '..');
const RESEND_URL = 'https://api.resend.com/emails';
const DEFAULT_FROM = 'Sarkari Desk <onboarding@resend.dev>';

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

function allowDevLink() {
  return process.env.NODE_ENV !== 'production';
}

function fromAddress() {
  return process.env.MAIL_FROM || process.env.SMTP_FROM || DEFAULT_FROM;
}

function status() {
  loadEnvFile();
  if (process.env.RESEND_API_KEY) return { configured: true, provider: 'resend' };
  if (process.env.SMTP_HOST) return { configured: true, provider: 'smtp' };
  return { configured: false, provider: null };
}

function smtpConfigured() {
  return status().provider === 'smtp';
}

function resetBodies({ url, expiresAt }) {
  const when = expiresAt ? ` It expires at ${expiresAt}.` : ' It expires in one hour.';
  const text = [
    'Reset your Sarkari Desk password using this one-time link:',
    url,
    `This is not a board or PSU account.${when}`,
    'If you did not ask for a reset, ignore this email.',
  ].join('\n\n');
  const safeUrl = String(url).replace(/&/g, '&amp;').replace(/"/g, '&quot;');
  const html = [
    '<p>Reset your Sarkari Desk password using this one-time link:</p>',
    `<p><a href="${safeUrl}">${safeUrl}</a></p>`,
    `<p>This is not a board or PSU account.${when}</p>`,
    '<p>If you did not ask for a reset, ignore this email.</p>',
  ].join('');
  return { subject: 'Reset your Sarkari Desk password', text, html };
}

async function sendViaResend({ to, subject, text, html }) {
  const key = process.env.RESEND_API_KEY;
  const fetchFn = typeof fetch === 'function' ? fetch : null;
  if (!fetchFn) {
    const err = new Error('fetch is not available');
    err.code = 'MAIL';
    throw err;
  }
  const res = await fetchFn(RESEND_URL, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${key}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      from: fromAddress(),
      to: [to],
      subject,
      text,
      html,
    }),
  });
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    const err = new Error(`Resend ${res.status}: ${String(body).slice(0, 240)}`);
    err.code = 'MAIL';
    throw err;
  }
  return { sent: true, provider: 'resend' };
}

async function sendViaSmtp({ to, subject, text, html }) {
  let nodemailer;
  try {
    nodemailer = require('nodemailer');
  } catch {
    return { sent: false, reason: 'no_mailer' };
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
    from: fromAddress(),
    to,
    subject,
    text,
    html,
  });
  return { sent: true, provider: 'smtp' };
}

async function sendPasswordReset({ to, url, expiresAt }) {
  loadEnvFile();
  const bodies = resetBodies({ url, expiresAt });
  const payload = { to, ...bodies };
  if (process.env.RESEND_API_KEY) {
    return sendViaResend(payload);
  }
  if (process.env.SMTP_HOST) {
    return sendViaSmtp(payload);
  }
  return { sent: false, reason: 'no_mailer' };
}

module.exports = {
  loadEnvFile,
  smtpConfigured,
  allowDevLink,
  sendPasswordReset,
  status,
  fromAddress,
  resetBodies,
  RESEND_URL,
};
