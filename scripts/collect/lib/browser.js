const { DEFAULT_UA } = require('./http');

let playwright;
try {
  playwright = require('playwright');
} catch {
  playwright = null;
}

/**
 * Run fn(context) with a shared Chromium browser.
 * @param {(ctx: {browser, context, page}) => Promise<any>} fn
 */
async function withBrowser(fn, options = {}) {
  if (!playwright) {
    throw new Error('playwright is not installed. Run: npm install playwright && npx playwright install chromium');
  }
  const headless = options.headless ?? process.env.COLLECT_HEADLESS !== 'false';
  const browser = await playwright.chromium.launch({ headless });
  const context = await browser.newContext({
    userAgent: DEFAULT_UA,
    viewport: { width: 1365, height: 900 },
    ignoreHTTPSErrors: true,
  });

  // Speed: block heavy assets
  await context.route('**/*', (route) => {
    const type = route.request().resourceType();
    if (['image', 'media', 'font'].includes(type)) return route.abort();
    return route.continue();
  });

  try {
    const page = await context.newPage();
    page.setDefaultTimeout(options.timeoutMs || 45000);
    page.setDefaultNavigationTimeout(options.navTimeoutMs || 45000);
    return await fn({ browser, context, page });
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }
}

/**
 * Extract job-like anchors after JS render.
 */
async function extractJobLinksFromPage(page, listUrl) {
  await page.goto(listUrl, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2500);
  try {
    await page.waitForLoadState('networkidle', { timeout: 12000 });
  } catch {
    /* ignore */
  }

  const links = await page.evaluate(() => {
    const JOB =
      /career|recruit|vacanc|notification|opening|walk|apprentice|advertisement|advt|employment|job|apply|circular|consultant|contract|engagement/i;
    const out = [];
    const seen = new Set();
    for (const a of document.querySelectorAll('a[href]')) {
      const href = a.href;
      if (!href || href.startsWith('javascript') || href === '#' || seen.has(href)) continue;
      const text = (a.innerText || a.textContent || a.getAttribute('title') || '').replace(/\s+/g, ' ').trim();
      const blob = `${text} ${href}`;
      if (!JOB.test(blob)) continue;
      seen.add(href);
      out.push({
        title: (text || href).slice(0, 240),
        href,
        text: (text || '').slice(0, 500),
        isPdf: /\.pdf(\?|#|$)/i.test(href),
      });
    }
    return out.slice(0, 80);
  });

  const html = await page.content();
  return { links, html, finalUrl: page.url() };
}

module.exports = { withBrowser, extractJobLinksFromPage };
