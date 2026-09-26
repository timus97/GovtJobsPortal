/**
 * Mail provider selection (Resend / SMTP / off). Run: node tests/pr20-mail.js
 */
const assert = require('assert');

process.env.RESEND_API_KEY = '';
process.env.SMTP_HOST = '';
process.env.MAIL_FROM = '';

const studentMail = require('../server/src/services/studentMail');

async function main() {
  assert.deepStrictEqual(studentMail.status(), { configured: false, provider: null });

  const off = await studentMail.sendPasswordReset({
    to: 'ada@example.com',
    url: 'http://localhost:5173/account/reset?token=abc',
    expiresAt: '2099-01-01T00:00:00.000Z',
  });
  assert.strictEqual(off.sent, false);
  assert.strictEqual(off.reason, 'no_mailer');

  process.env.RESEND_API_KEY = 're_test_key';
  const prevFetch = global.fetch;
  let captured;
  global.fetch = async (url, opts) => {
    captured = { url, opts };
    return {
      ok: true,
      status: 200,
      text: async () => '{"id":"msg_1"}',
    };
  };
  try {
    const sent = await studentMail.sendPasswordReset({
      to: 'ada@example.com',
      url: 'http://localhost:5173/account/reset?token=abc',
      expiresAt: '2099-01-01T00:00:00.000Z',
    });
    assert.strictEqual(sent.sent, true);
    assert.strictEqual(sent.provider, 'resend');
    assert.strictEqual(captured.url, studentMail.RESEND_URL);
    const payload = JSON.parse(captured.opts.body);
    assert.strictEqual(payload.to[0], 'ada@example.com');
    assert.ok(payload.text.includes('token=abc'));
    assert.ok(String(captured.opts.headers.Authorization).startsWith('Bearer re_'));
    assert.deepStrictEqual(studentMail.status(), { configured: true, provider: 'resend' });
  } finally {
    global.fetch = prevFetch;
    delete process.env.RESEND_API_KEY;
  }

  console.log('PASS pr20-mail');
}

main().catch((err) => {
  console.error(`FAIL: ${err.message}`);
  process.exit(1);
});
