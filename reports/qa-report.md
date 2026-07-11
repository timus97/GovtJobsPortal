# QA & Performance Report

Generated: 2026-07-11T15:00:15.242Z

Public site: https://timus97.github.io/GovtJobsPortal
API base: http://localhost:4000

## Summary: 12/12 passed (0 failed)

| Check | Status | Time (ms) | Detail |
|-------|--------|-----------|--------|
| api_health | PASS | 21 | status=200 |
| api_stats | PASS | 1 | total=627, open=562 |
| api_jobs_list | PASS | 7 | count=5, total=566 |
| api_sources | PASS | 8 | sources=264 |
| api_job_detail | PASS | 6 | id=f38c310d0e82d6c2 |
| api_filter_psu | PASS | 6 | total=526 |
| public_home | PASS | 253 | status=200, bytes=1674 |
| public_stats_json | PASS | 246 | total=451, open=382, bytes=1670 |
| public_jobs_json | PASS | 444 | count=451, bytes=590190 |
| public_sources_json | PASS | 256 | sources=264, bytes=273284 |
| perf_thresholds | PASS | 0 | homeMs=253, statsMs=246, jobsMs=444, publicStatsP50=10, publicStatsP95=11, publicStatsSamples=[9,10,10,10,11], threshold |
| local_schema_hasExam | PASS | 0 | total=627, bad=0 |

## Performance samples

- `http://localhost:4000/api/health` — **21 ms**, 74 bytes
- `http://localhost:4000/api/stats` — **1 ms**, 1343 bytes
- `http://localhost:4000/api/jobs?limit=5` — **7 ms**, 6451 bytes
- `http://localhost:4000/api/sources` — **8 ms**, 413645 bytes
- `http://localhost:4000/api/jobs/:id` — **6 ms**, 1225 bytes
- `http://localhost:4000/api/jobs?orgType=psu` — **6 ms**, 23043 bytes
- `https://timus97.github.io/GovtJobsPortal/` — **253 ms**, 1674 bytes
- `https://timus97.github.io/GovtJobsPortal/data/stats.json` — **246 ms**, 1670 bytes
- `https://timus97.github.io/GovtJobsPortal/data/jobs.json` — **444 ms**, 590190 bytes
- `https://timus97.github.io/GovtJobsPortal/data/sources.json` — **256 ms**, 273284 bytes

## Thresholds

- Home HTML ≤ 3000 ms
- stats.json ≤ 2000 ms
- jobs.json ≤ 5000 ms
- Observed stats P50/P95: 10/11 ms
