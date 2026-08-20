# Public hosting

The **v1 product** is always-on Express + SPA (match and `/ops` need the API). GitHub Pages is a **JSON snapshot fallback**, not the match or ops host.

`render.yaml` is still the free Render plan (sleeps ~15 min). Upgrade that service when you want the product host to stay awake.

## 1. GitHub Pages (static snapshot)

**Public URL pattern:** `https://timus97.github.io/GovtJobsPortal/`

### What gets deployed

- Built React SPA (`client/dist`)
- Snapshot of job data under `client/public/data/` (jobs, stats, sources, pipeline reports)
- Client uses live `/api` when available; otherwise falls back to static JSON

### One-time setup

1. Push this repo to GitHub as **public** `timus97/GovtJobsPortal` (or your fork).
2. Repo → **Settings → Pages → Source: GitHub Actions**.
3. Push to `main` (or run workflow **Deploy GitHub Pages**).
4. Wait for the green check; open the Pages URL.

### Local build that matches Pages

```bash
npm.cmd install --ignore-scripts
npm.cmd --prefix client install
node scripts/prepareStaticData.js
set VITE_BASE=/GovtJobsPortal/
npm.cmd --prefix client run build
```

### Refreshing public data

```bash
npm.cmd run pipeline:daily
node scripts/prepareStaticData.js
git add data client/public/data
git commit -m "chore(data): refresh jobs"
git push
```

The Pages workflow rebuilds the site on every push to `main`.

---

## 2. Render.com free (full Express API + SPA)

**Use when:** you want a real Node API (same as local `npm run server`).

1. Create a free account at [render.com](https://render.com) (Gmail works).
2. **New → Blueprint** → connect this GitHub repo → apply `render.yaml`.
3. Or **New Web Service** → connect repo:
   - Build: `npm install && npm --prefix client install && node scripts/prepareStaticData.js && npm --prefix client run build && npm --prefix server install`
   - Start: `node server/src/index.js`
   - Plan: **Free**
4. After deploy: `https://<service-name>.onrender.com`

**Note:** Free web services sleep after ~15 minutes idle; first request may take 30–60s.

---

## 3. Optional: Netlify / Cloudflare Pages

Same as GitHub Pages static mode:

- Build command: `npm --prefix client install && node scripts/prepareStaticData.js && npm --prefix client run build`
- Publish directory: `client/dist`
- Set env `VITE_BASE=/` (root domain) or your path prefix

---

## Secrets (only for email alerts + optional daily scrape Action)

Not required for public browsing. For alerts / scrape CI:

| Secret | Purpose |
|--------|---------|
| `SMTP_*` / `ALERT_*` | Email digests |
| `PUBLIC_SITE_URL` | Links inside alert emails |

---

## Gmail verification

If a host asks for email verification (Render, Netlify, etc.), any Gmail works.  
**Do not commit** passwords or app passwords into the repo. Use host secret stores.

---

## Health checks after go-live

- Home page loads
- `/jobs` lists openings
- `/sources` lists PSU/gov URLs
- Job detail “Official site” opens external career page

## Live URLs (this project)

| What | URL |
|------|-----|
| **Public website** | https://timus97.github.io/GovtJobsPortal/ |
| **Jobs** | https://timus97.github.io/GovtJobsPortal/jobs |
| **Sources** | https://timus97.github.io/GovtJobsPortal/sources |
| **GitHub repo** | https://github.com/timus97/GovtJobsPortal |
| **Static jobs data** | https://timus97.github.io/GovtJobsPortal/data/jobs.json |

## QA reports

After deploy, run:

```bash
npm.cmd run qa:e2e
```

Reports are written to `reports/qa-report.md` and `reports/qa-report.json`.
