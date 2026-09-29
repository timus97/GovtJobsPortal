# Collector source research

Date checked: 2026-09-27. Baseline: `data/sources/registry.json` (308 sources, 168 enabled, 140 disabled). This note does not change the registry, Java code, or the database.

## How sources were chosen

The Java collector accepts an https URL only when `OfficialUrlPolicy` allows the host. Suffixes are `.gov.in` and `.nic.in`. `application.yml` `govtjobs.ops.extra-hosts` also allows `ibps.in`, `sbi.co.in`, `sbi.bank.in`, `bank.sbi`, `recruitment.sbi.bank.in`, `becil.com`, `nta.ac.in`, `rbi.org.in`, `opportunities.rbi.org.in`, `nabard.org`, `licindia.in`, `gate2027.iitm.ac.in`, `aiimsexams.ac.in`, `aiims.edu`, and `ongcindia.com`.

The research brief named a shorter extra list: `ibps.in`, `sbi.co.in`, `onlineregistrationform.org`, `ongcindia.com`, `airportsindia.org.in`, `digialm.com`, `cbexams.com`, `ucanapply.com`, `nta.ac.in`. A URL is recommended below only if it is on the Java list or a `.gov.in` / `.nic.in` host. Brief-only hosts are not in the ready table, because the running policy would reject them.

Of the 168 enabled sources, 58 already use an allowed host and 110 do not. Ready rows are the ones whose list page was opened or returned in search with a real notice or exam list. A homepage is used only when that page itself carries the current notices. Pages that failed to resolve, returned an empty shell, or showed no vacancy list are left out.

Priority in the table is not the registry's P0–P3:

- P0: national boards and the largest recruiting bodies this product already treats as core (UPSC, SSC, IBPS, SBI, RRB where a list page resolved, NTA, Employment News).
- P1: other enabled registry sources whose official list URL was verified this pass.
- P2: allowlisted gaps that are not rows in the registry.

Aggregators such as Naukri, SarkariResult, FreeJobAlert, and Gradeup were not considered.

## Sources rejected, or not ready

Not recommended for the crawler:

- **Registry URLs on hosts outside the Java allowlist.** 110 enabled sources, mostly PSU career sites on `.com`, `.co.in`, `.in`, or `.org`. See the allowlist section. Their registry URLs were not re-checked, because they cannot be crawled until the host is added.
- **Wrong-host duplicates in the registry.** Several P3 rows point at another company's site (for example HOCL, HTL, and IDRCL list URLs copied from HAL or GAIL). Do not crawl those rows as if they were the named organisation.
- **UPSC apply portal** `https://upsconline.nic.in/`. It resolves, then redirects to a candidate login. It is not a notice list. Use What's New and Active Examinations.
- **UPSC exam-calendar path in the registry** `https://upsc.gov.in/examinations/exam-calendar`. A direct request redirected to the UPSC homepage, so that path was not treated as the list page. Active exams and What's New were the pages that showed notices.
- **Old SBI list URL** `https://sbi.co.in/web/careers/current-openings`. It did not return the openings page. The live list is on `sbi.bank.in`, which is already an extra host.
- **SBI apply root** `https://recruitment.sbi.bank.in/`. The request timed out. Apply links on the current-openings page use this host; the root is not the list.
- **RRB apply** `https://www.rrbapply.gov.in/`. HTTP 200, but the fetched body was an empty script shell, not Centralised Employment Notices. `https://www.rrbcdg.gov.in/employment-notices.php` showed a CEN index in search, then a later direct fetch failed on TLS. Neither is in the ready table. Design already says not to treat RRB CDG as the national board; zonal boards plus a working CEN index are the list. RRB Chennai is enabled but was not re-fetched.
- **ONGC.** `ongcindia.com` is allowlisted and enabled (`psu_ongc`, `psu_ovl`). `https://ongcindia.com/web/eng/career/recruitment-notice` and the Hindi twin, and the registry path `https://www.ongcindia.com/wps/wcm/connect/en/career`, did not resolve in this pass. No ONGC URL is ready.
- **NCS** `https://betacloud.ncs.gov.in/job-listing?isGovernmentJob=true`. Not re-fetched successfully. Left out.
- **DRDO** `https://www.drdo.gov.in/drdo/careers`. The host returned "inaccessible at this time".
- **LIC** `https://licindia.in/web/guest/careers`. The careers URL opened, but the extract had no vacancy rows. Not treated as a confirmed list page.
- **DSSSB** `https://dsssb.delhi.gov.in/`. Fetch failed.
- **NABARD's What's New** is not the career list. The career-notices URL is the one in the ready table. That page is a mix of advertisements and results, which matches how the bank publishes recruitment.
- **SEBI's vacancy action** is a long archive (rows back to 2010, with later 2026 rows). It is the official list, but a crawler must not treat every historical row as open.
- **Kerala PSC homepage** is announcements and bulletins. The notification index is the list page below.
- **Defence career sites** (`joinindianarmy.nic.in`, `joinindiannavy.gov.in`, `careerairforce.nic.in`) are already in the registry and disabled. They are not gaps. They were not re-verified.
- **Vendor hosts in the brief** (`digialm.com`, `cbexams.com`, `ucanapply.com`, `onlineregistrationform.org`, `airportsindia.org.in`). These are application or exam-delivery hosts, not an organisation's own notice board, and they are not in `application.yml`. No stable vacancy index on them was verified, so none are recommended.

Enabled allowlisted sources that were not opened this pass stay out of the ready table. That includes India Post, EPFO, FCI, KVS, NVS, CTET, AIIMS exams, NHM, BARC, ISRO, REC, SJVN, CWC, MOIL, WAPCOS, apprenticeshipindia.gov.in, and the state PSC homepages other than Kerala. Do not assume those registry URLs still list vacancies.

## Hosts that need an allowlist change

Java will reject these until `extra-hosts` is updated. Do not add them to the crawler first.

**In the brief, missing from `application.yml`:** `onlineregistrationform.org`, `airportsindia.org.in`, `digialm.com`, `cbexams.com`, `ucanapply.com`.

**Enabled registry hosts for large recruiters, not on the allowlist.** URLs are the registry's first list URL, not a freshly verified page:

| Organisation | Registry list URL | Host to add |
| --- | --- | --- |
| Indian Oil (IOCL) | `https://iocl.com/latest-job-opening` | `iocl.com` |
| NTPC | `https://www.ntpc.co.in/en/careers` | `ntpc.co.in` |
| SAIL | `https://www.sail.co.in/en/careers` | `sail.co.in` |
| BHEL | `https://www.bhel.com/careers` | `bhel.com` (and `careers.bhel.in` if that host is kept) |
| Coal India | `https://www.coalindia.in/career` | `coalindia.in` |
| GAIL | `https://gailonline.com/ZBCareergail.html` | `gailonline.com` |
| POWERGRID | `https://www.powergrid.in/en/career` | `powergrid.in` |
| BPCL | `https://www.bharatpetroleum.in/careers/current-openings.aspx` | `bharatpetroleum.in` |
| HPCL | `https://www.hindustanpetroleum.com/Careeropportunities` | `hindustanpetroleum.com` |
| HAL | `https://hal-india.co.in/Career/M__204` | `hal-india.co.in` |
| BEL | `https://bel-india.in/careers` | `bel-india.in` |
| AAI | `https://www.aai.aero/en/careers/recruitment` | `aai.aero` |

`airportsindia.org.in` is not a substitute for `aai.aero` until that host is checked. The same block applies to the rest of the 110 enabled non-allowlisted hosts (shipping, coal subsidiaries, metro, insurance `.co.in`, and similar). Adding a host does not by itself fix copied or stale list URLs.

## Ready for configuration

| priority | label | url | organization | why it fits | list page or homepage | verified |
| --- | --- | --- | --- | --- | --- | --- |
| P0 | UPSC What's New | https://www.upsc.gov.in/whats-new | Union Public Service Commission | Official stream of exam notices and direct-recruitment notices. Better crawl target than the calendar path, which redirected away. | list page | yes |
| P0 | UPSC Active Examinations | https://www.upsc.gov.in/examinations/active-exams | Union Public Service Commission | Current UPSC examination cycles, already an enabled registry list URL. | list page | yes |
| P0 | SSC Notice Board | https://ssc.gov.in/home/notice-board | Staff Selection Commission | Current SSC notices (CGL, CHSL, JE, and others). Enabled registry list URL. HTTP 200. | list page | yes |
| P0 | SSC Examination Calendar | https://ssc.gov.in/for-candidates/examination-calendar | Staff Selection Commission | Commission's examination calendar. Enabled registry list URL. HTTP 200. | list page | yes |
| P0 | IBPS CRP updates | https://www.ibps.in/index.php/crp-updates/ | Institute of Banking Personnel Selection | Live CRP notices for public-sector and regional rural banks. `ibps.in` is an extra host. | list page | yes |
| P0 | SBI Current Openings | https://sbi.bank.in/web/careers/current-openings | State Bank of India | Live specialist, PO, and junior-associate advertisements. Replaces the `sbi.co.in` path, which did not serve this list. `sbi.bank.in` is an extra host. | list page | yes |
| P0 | NTA latest updates | https://nta.ac.in/ | National Testing Agency | Homepage notice stream for UGC NET, CSIR-UGC NET, ICAR, CUET, NEET, and NTA's own vacancy notices. Registry has only the UGC NET child site, not this host. `nta.ac.in` is an extra host. | homepage | yes |
| P0 | Employment News all jobs | https://employmentnews.gov.in/newemp/AllJobs.aspx?k=All | Employment News (Publications Division) | Free job-highlights table the product already collects daily. Official `.gov.in` bulletin, not a private aggregator. | list page | yes |
| P1 | BECIL vacancies | https://www.becil.com/Vacancies | Broadcast Engineering Consultants India Limited | Current contract advertisements with upload and submission dates. Prefer this over `/careers`. `becil.com` is an extra host. Enabled source. | list page | yes |
| P1 | RBI vacancies | https://opportunities.rbi.org.in/Scripts/Vacancies.aspx | Reserve Bank of India | Dated vacancy list. The opportunities homepage is marketing, not the list. Enabled source. | list page | yes |
| P1 | NABARD career notices | https://www.nabard.org/careers-notices1.aspx?cid=693&id=26 | NABARD | Career-notices section the bank tells applicants to use. Enabled registry URL. | list page | yes |
| P1 | SEBI vacancies | https://www.sebi.gov.in/sebiweb/about/AboutAction.do?doVacancies=yes | Securities and Exchange Board of India | Official vacancy archive, including later notices. Crawl as a list, not as proof every row is still open. Enabled source. | list page | yes |
| P1 | ESIC recruitments | https://esic.gov.in/recruitments | Employees' State Insurance Corporation | Recruitment table with publish date and last date. Same path as the enabled registry URL. | list page | yes |
| P1 | GATE 2027 | https://gate2027.iitm.ac.in/ | GATE (IIT Madras for the National Coordination Board) | Current GATE cycle, dates, and notifications. Exam page, not a PSU vacancy board. Enabled source. Host is an extra host. | homepage | yes |
| P1 | UGC NET notices | https://ugcnet.nta.nic.in/ | National Testing Agency (UGC NET) | Public notices for the UGC NET cycle. Enabled registry source. Eligibility test, not a multi-organisation job board. | homepage | yes |
| P1 | Kerala PSC notifications | https://www.keralapsc.gov.in/notifications | Kerala Public Service Commission | Gazette notification index with category numbers and last dates. Enabled source; do not crawl only the homepage. | list page | yes |
| P2 | NTA exam portal index | https://exams.nta.nic.in/ | National Testing Agency | Index of current NTA exam portals, including recruitment exams. Not a registry row. Distinct from `ugcnet.nta.nic.in` and from the `nta.ac.in` notice stream. | list page | yes |

## Configuration applied

Loaded 12 verified https rows into `collect.priority_links` (sort_order 1–12, P0 then P1 then P2). Disabled the four seed homepages because those exact URLs are not in the ready table: `https://ssc.gov.in/`, `https://upsc.gov.in/`, `https://www.ibps.in/`, `https://www.rrbcdg.gov.in/`. Skipped 5 ready rows whose hosts are outside `.gov.in`, `.nic.in`, and the brief extra-host list: `sbi.bank.in`, `becil.com`, `opportunities.rbi.org.in`, `nabard.org`, `gate2027.iitm.ac.in`. Keywords were not changed. One crawl ran (`e5040ea5-1313-4174-b768-192b2ddca11c`). Notices are in `collect.notices` only (`waiting` or `rejected`); none were approved into `catalog.opportunities`.
