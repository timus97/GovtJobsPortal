# Source registry

Human-readable map of data sources. Machine config: `data/sources/registry.json`.

## P0 — daily / continuous

| sourceId | Name | Cadence | Method |
|----------|------|---------|--------|
| seed_manual | Manual curator seed | Continuous | `data/seed/jobs.json`, then `process` |
| ncs_gov | National Career Service | Daily | Playwright (`browser_scrape`) |
| employment_news | Employment News | Daily | HTML + PDF extract |
| becil | BECIL Careers | Daily | HTML + PDF extract |

Daily command: collect daily. See `docs/collection-runbook.md`.

## P1 — major PSU careers

NTPC, IOCL, ONGC, BHEL, SAIL, POWERGRID, HAL, BEL — weekly rotation.

## Rules

- Prefer official apply / notification URLs only.
- Do not republish full PDFs.
- Rate-limit automated collectors.
- Disable broken sources via `enabled: false` in the registry.
