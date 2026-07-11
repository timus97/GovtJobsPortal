# QA & Performance Report

Generated: 2026-07-11T14:58:47.544Z

Public site: https://timus97.github.io/GovtJobsPortal
API base: http://localhost:4000

## Summary: 12/12 passed (0 failed)

| Check | Status | Time (ms) | Detail |
|-------|--------|-----------|--------|
| api_health | PASS | 22 | status=200 |
| api_stats | PASS | 1 | total=451, open=382 |
| api_jobs_list | PASS | 6 | count=5, total=386 |
| api_sources | PASS | 7 | sources=264 |
| api_job_detail | PASS | 5 | id=f38c310d0e82d6c2 |
| api_filter_psu | PASS | 5 | total=346 |
| public_home | PASS | 38 | status=200, bytes=1674 |
| public_stats_json | PASS | 11 | total=451, open=382, bytes=1670 |
| public_jobs_json | PASS | 340 | count=451, bytes=590190 |
| public_sources_json | PASS | 249 | sources=264, bytes=273284 |
| perf_thresholds | PASS | 0 | homeMs=38, statsMs=11, jobsMs=340, publicStatsP50=10, publicStatsP95=11, publicStatsSamples=[9,10,10,10,11], thresholds= |
| local_schema_hasExam | PASS | 0 | total=451, bad=0 |

## Performance samples

- `http://localhost:4000/api/health` — **22 ms**, 74 bytes
- `http://localhost:4000/api/stats` — **1 ms**, 1196 bytes
- `http://localhost:4000/api/jobs?limit=5` — **6 ms**, 6450 bytes
- `http://localhost:4000/api/sources` — **7 ms**, 413631 bytes
- `http://localhost:4000/api/jobs/:id` — **5 ms**, 1225 bytes
- `http://localhost:4000/api/jobs?orgType=psu` — **5 ms**, 23043 bytes
- `https://timus97.github.io/GovtJobsPortal/` — **38 ms**, 1674 bytes
- `https://timus97.github.io/GovtJobsPortal/data/stats.json` — **11 ms**, 1670 bytes
- `https://timus97.github.io/GovtJobsPortal/data/jobs.json` — **340 ms**, 590190 bytes
- `https://timus97.github.io/GovtJobsPortal/data/sources.json` — **249 ms**, 273284 bytes

## Thresholds

- Home HTML ≤ 3000 ms
- stats.json ≤ 2000 ms
- jobs.json ≤ 5000 ms
- Observed stats P50/P95: 10/11 ms
