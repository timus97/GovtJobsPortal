# Collector verified URLs — 30 Sep 2026

Fetch method: HTTPS only, browser User-Agent `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36`, default certificate checks left on. No TLS bypass. Radware `validate.perfdrive.com` was not followed. Playwright was used only where a same-site JavaScript cookie wall hid a page that a browser then loaded.

Registry edits are `listUrls` and `baseUrl` only. `enabled` and `method` were not changed. Sources not listed as verified-update were left unchanged.

## psu_iocl

- status: blocked
- old baseUrl: https://iocl.com/
- old listUrls:
  - https://iocl.com/latest-job-opening
  - https://iocl.com/careers
  - https://iocl.com/career
  - https://iocl.com/recruitment
  - https://iocl.com/jobs
  - https://iocl.com/en/careers
  - https://iocl.com/en/career
  - https://iocl.com/careers/current-openings
- new listUrls: unchanged
- evidence: Plain HTTPS GET of https://iocl.com/latest-job-opening returned HTTP 307 from Sucuri/Cloudproxy with no Location header and body title "You are being redirected..." (JavaScript cookie wall). careers.iocl.com did not resolve. A browser load of the same HTTPS URL finished on https://iocl.com/latest-job-opening with title "Latest Job Openings : IndianOil | Oil and Gas Job Vacancies", heading "Latest Job Openings", and link "Click Here to Read the Detailed Advertisement - [14/08/2026]" -> https://iocl.com/admin/img/UploadedFiles/LatestJobOpening/Files/DetailedAd14082026.pdf. The existing first list URL is already that page. Not replaced. Collector non-JS clients still hit the wall.

## psu_ntpc

- status: verified
- old baseUrl: https://www.ntpc.co.in/
- old listUrls:
  - https://www.ntpc.co.in/en/careers
  - https://careers.ntpc.co.in
  - https://www.ntpc.co.in/careers
  - https://www.ntpc.co.in/career
  - https://www.ntpc.co.in/recruitment
  - https://www.ntpc.co.in/jobs
  - https://www.ntpc.co.in/en/career
  - https://www.ntpc.co.in/careers/current-openings
- new baseUrl: https://careers.ntpc.co.in/
- new listUrls:
  - https://careers.ntpc.co.in/recruitment/
- evidence: HTTPS 200 final https://careers.ntpc.co.in/recruitment/ title "NTPC Limited - Recruitment Portal" heading "Notice Board". Notice: Advt.09/26_Eng -> https://careers.ntpc.co.in/recruitment/pdf_viewer.php?token=NmpyQW9xSHkrUVF0VzJSZnpEbWRSc3pjdTM3KzRqSlhNV3dlVmhzUWY0M3I3SlVKa2luTE5uYy9BMjJvRVVFbG9xbTJxZGlua0loY2duQXlmRjNsYkFndGl2MUZYWlMzNXhuZThYWGZGSU1OZ1hJcXVJSGpEOGJG (on careers.ntpc.co.in). https://www.ntpc.co.in/ and /en/careers 302 to validate.perfdrive.com (Radware). That host was not followed. https://careers.ntpc.co.in/ 302 to http://careers.ntpc.co.in/recruitment/ which then refused the connection, so the HTTPS /recruitment/ path is the list.

## psu_bpcl

- status: verified
- old baseUrl: https://www.bharatpetroleum.in/
- old listUrls:
  - https://www.bharatpetroleum.in/careers/current-openings.aspx
  - https://www.bpclcareers.in
  - https://www.bharatpetroleum.in/careers
  - https://www.bharatpetroleum.in/career
  - https://www.bharatpetroleum.in/recruitment
  - https://www.bharatpetroleum.in/jobs
  - https://www.bharatpetroleum.in/en/careers
  - https://www.bharatpetroleum.in/en/career
- new baseUrl: https://www.bharatpetroleum.in/
- new listUrls:
  - https://www.bharatpetroleum.in/careers/job-openings
- evidence: HTTPS 200 final https://www.bharatpetroleum.in/careers/job-openings title "Job Openings | Official Website of BPCL, India" heading "Job-Openings". Notice: "Click here to view Detailed Advertisement" -> https://www.bharatpetroleum.in/images/files/BPCL-KR-and-MR-NON-MGMT-RECT-NOTIFICATION-july-26.pdf. The old current-openings.aspx URL 301/302s four times to this same final URL (collector allows only three hops). https://www.bpclcareers.in/ did not resolve.

## psu_ongc

- status: verified
- old baseUrl: https://www.ongcindia.com/
- old listUrls:
  - https://www.ongcindia.com/wps/wcm/connect/en/career
  - https://www.ongcindia.com/careers
  - https://www.ongcindia.com/career
  - https://www.ongcindia.com/recruitment
  - https://www.ongcindia.com/jobs
  - https://www.ongcindia.com/en/careers
  - https://www.ongcindia.com/en/career
  - https://www.ongcindia.com/careers/current-openings
- new baseUrl: https://www.ongcindia.com/
- new listUrls:
  - https://ongcindia.com/web/eng/career/recruitment-notice
- evidence: HTTPS 200 final https://ongcindia.com/web/eng/career/recruitment-notice title "ONGC - Recruitment Notices - Oil and Gas jobs - en - ongcindia.com". Notice: "Recruitment of Geologists and Engineers at E1 Level - Advt. No. 2/2026 (R&P)" -> https://ongcindia.com/web/eng/detail?assetEntry=84777603&assetClassPK=84777498. Old WCM career path was not the list. https://ongcindia.com/web/eng/career/recruitment was HTTP 404.

## psu_bhel

- status: verified
- old baseUrl: https://www.bhel.com/
- old listUrls:
  - https://www.bhel.com/careers
  - https://careers.bhel.in
  - https://www.bhel.com/career
  - https://www.bhel.com/recruitment
  - https://www.bhel.com/jobs
  - https://www.bhel.com/en/careers
  - https://www.bhel.com/en/career
  - https://www.bhel.com/careers/current-openings
- new baseUrl: https://careers.bhel.in/
- new listUrls:
  - https://careers.bhel.in/index.jsp
- evidence: HTTPS 200 final https://careers.bhel.in/index.jsp heading "Current Openings ( वर्तमान रिक्तियां)". Notice: "Detailed Advertisement" -> https://careers.bhel.in/ar_2025/Artisan_Detailed AD_110825.pdf and heading "Artisan Recruitment – 2025". https://www.bhel.com/careers was HTTP 404 title "Page not found". BHEL's own public notice names https://careers.bhel.in/ as the recruitment site. careers.bhel.in is a sibling host (bhel.in, not bhel.com).

## psu_gail

- status: verified
- old baseUrl: https://gailonline.com/
- old listUrls:
  - https://gailonline.com/ZBCareergail.html
  - https://gailonline.com
  - https://gailonline.com/careers
  - https://gailonline.com/career
  - https://gailonline.com/recruitment
  - https://gailonline.com/jobs
  - https://gailonline.com/en/careers
  - https://gailonline.com/en/career
- new baseUrl: https://www.gailonline.com/
- new listUrls:
  - https://www.gailonline.com/vacancies.html
- evidence: HTTPS 200 final https://www.gailonline.com/vacancies.html title "GAIL India Limited". Notice: "Detailed Advertisement in English" -> https://www.gailonline.com/careers/currentOpnning/Detailed-Adv-09-09-2026.pdf. Also GATE-2027 advertisement PDF on the same page. Old https://gailonline.com/ZBCareergail.html redirected to www and was HTTP 404. https://www.gailonline.com/careers/ was HTTP 403. https://careers.gail.co.in/ timed out.

## psu_pgcil

- status: verified
- old baseUrl: https://www.powergrid.in/
- old listUrls:
  - https://www.powergrid.in/en/career
  - https://www.powergrid.in/careers
  - https://www.powergrid.in/career
  - https://www.powergrid.in/recruitment
  - https://www.powergrid.in/jobs
  - https://www.powergrid.in/en/careers
  - https://www.powergrid.in/careers/current-openings
  - https://www.powergrid.in/human-resources/careers
- new baseUrl: https://www.powergrid.in/
- new listUrls:
  - https://www.powergrid.in/en/job-opportunities1
- evidence: HTTPS 200 final https://www.powergrid.in/en/job-opportunities1 title "Employment opportunities | POWERGRID" heading "Employment opportunities". Notice: "Recruitment of Engineer Trainees-2027 through GATE 2027 Advt No. CC/03/2026 dated 24.09.2026" -> https://www.powergrid.in/en/job-opportunities/recruitment-engineer-trainees-2027-through-gate-2027-advt-no-cc032026-dated. https://www.powergrid.in/en/career and /careers were HTTP 404. Homepage redirected to http://www.powergrid.in/hi and then timed out.

## psu_pfc

- status: blocked
- old baseUrl: https://www.pfcindia.com/
- old listUrls:
  - https://www.pfcindia.com/Home/VS/77
  - https://www.pfcindia.com
  - https://www.pfcindia.com/careers
  - https://www.pfcindia.com/career
  - https://www.pfcindia.com/recruitment
  - https://www.pfcindia.com/jobs
  - https://www.pfcindia.com/en/careers
  - https://www.pfcindia.com/en/career
- new listUrls: unchanged
- evidence: HTTPS certificate failure on https://www.pfcindia.com/Home/VS/77, https://pfcindia.com/, and https://pfcindia.com/Home/VS/77: hostname mismatch, certificate is not valid for that host. https://pfcindia.co.in/ failed with an expired certificate. No replacement. listUrls unchanged.

## psu_bel

- status: blocked
- old baseUrl: https://bel-india.in/
- old listUrls:
  - https://bel-india.in/careers
  - https://bel-india.in
  - https://bel-india.in/career
  - https://bel-india.in/recruitment
  - https://bel-india.in/jobs
  - https://bel-india.in/en/careers
  - https://bel-india.in/en/career
  - https://bel-india.in/careers/current-openings
- new listUrls: unchanged
- evidence: HTTPS certificate failure on https://bel-india.in/, https://bel-india.in/careers, and https://www.bel-india.in/: unable to get local issuer certificate. No replacement. listUrls unchanged.

## psu_concor

- status: blocked
- old baseUrl: https://www.concorindia.co.in/
- old listUrls:
  - https://www.concorindia.co.in/career.aspx
  - https://www.concorindia.co.in
  - https://www.concorindia.co.in/careers
  - https://www.concorindia.co.in/career
  - https://www.concorindia.co.in/recruitment
  - https://www.concorindia.co.in/jobs
  - https://www.concorindia.co.in/en/careers
  - https://www.concorindia.co.in/en/career
- new listUrls: unchanged
- evidence: HTTPS 200 final https://www.concorindia.co.in/career.aspx title "CONCOR" and https://www.concorindia.co.in/recruitment.aspx title "CONCOR". Neither page exposed a recruitment, vacancy, or advertisement link (empty anchor set). Not treated as a list. listUrls unchanged.

## psu_mtnl

- status: blocked
- old baseUrl: https://mtnl.in/
- old listUrls:
  - https://mtnl.in/career.html
  - https://mtnl.in
  - https://mtnl.in/careers
  - https://mtnl.in/career
  - https://mtnl.in/recruitment
  - https://mtnl.in/jobs
  - https://mtnl.in/en/careers
  - https://mtnl.in/en/career
- new listUrls: unchanged
- evidence: https://mtnl.in/career.html was HTTP 404. https://mtnl.in/ and https://www.mtnl.in/ were HTTPS 200 title "MTNL Corporate Office Home Main Homepage". The only recruitment-like link was an advocate-panel notice on mtnldelhi.in plus "Beware of Fake Advertisement / Appointment Letter regarding Recruitment in MTNL" -> https://mtnl.in/notice_fraud_adv.pdf. That is not a vacancy list. listUrls unchanged.

## psu_nalco

- status: blocked
- old baseUrl: https://nalcoindia.com/
- old listUrls:
  - https://nalcoindia.com/careers
  - https://nalcoindia.com
  - https://nalcoindia.com/career
  - https://nalcoindia.com/recruitment
  - https://nalcoindia.com/jobs
  - https://nalcoindia.com/en/careers
  - https://nalcoindia.com/en/career
  - https://nalcoindia.com/careers/current-openings
- new listUrls: unchanged
- evidence: HTTPS certificate failure on https://nalcoindia.com/, https://nalcoindia.com/careers, and https://www.nalcoindia.com/: unable to get local issuer certificate. https://nalcoindia.co.in/ returned HTTP 200 with an empty title and no recruitment link. No replacement. listUrls unchanged.

## psu_nbcc

- status: verified
- old baseUrl: https://www.nbccindia.in/
- old listUrls:
  - https://www.nbccindia.in/webEnglish/career
  - https://www.nbccindia.in
  - https://www.nbccindia.in/careers
  - https://www.nbccindia.in/career
  - https://www.nbccindia.in/recruitment
  - https://www.nbccindia.in/jobs
  - https://www.nbccindia.in/en/careers
  - https://www.nbccindia.in/en/career
- new baseUrl: https://www.nbccindia.in/
- new listUrls:
  - https://www.nbccindia.in/webEnglish/jobs
- evidence: https://www.nbccindia.in/webEnglish/career was HTTP 404. HTTPS 200 final https://www.nbccindia.in/webEnglish/jobs title "CAREER | NBCC (India) Ltd.". Notice: "Reopening Notice against Advertisement No-01/2026 (Pub. Date : 12-04-2026)" -> https://www.nbccindia.in/pdfData/jobs/REOPENING NOTICE_12042026.pdf. Also "No-01/2026 Window Advertisement" PDF on the same page.

## psu_nlc

- status: blocked
- old baseUrl: https://www.nlcindia.in/
- old listUrls:
  - https://www.nlcindia.in/new_website/career.htm
  - https://www.nlcindia.in
  - https://www.nlcindia.in/careers
  - https://www.nlcindia.in/career
  - https://www.nlcindia.in/recruitment
  - https://www.nlcindia.in/jobs
  - https://www.nlcindia.in/en/careers
  - https://www.nlcindia.in/en/career
- new listUrls: unchanged
- evidence: https://www.nlcindia.in/new_website/career.htm was HTTP 404. https://www.nlcindia.in/careers/ was HTTPS 200 title "Neyveli Lignite Corporation Limited" with no vacancy or advertisement link. Homepage had no recruitment signal. listUrls unchanged.

## psu_rinl

- status: blocked
- old baseUrl: https://www.vizagsteel.com/
- old listUrls:
  - https://www.vizagsteel.com/index.asp?pgid=35
  - https://www.vizagsteel.com
  - https://www.vizagsteel.com/careers
  - https://www.vizagsteel.com/career
  - https://www.vizagsteel.com/recruitment
  - https://www.vizagsteel.com/jobs
  - https://www.vizagsteel.com/en/careers
  - https://www.vizagsteel.com/en/career
- new listUrls: unchanged
- evidence: https://www.vizagsteel.com/ read timed out. https://vizagsteel.com/ and https://www.vizagsteel.in/ did not resolve. No replacement. listUrls unchanged.

## psu_sci

- status: blocked
- old baseUrl: https://www.shipindia.com/
- old listUrls:
  - https://www.shipindia.com/career
  - https://www.shipindia.com
  - https://www.shipindia.com/careers
  - https://www.shipindia.com/recruitment
  - https://www.shipindia.com/jobs
  - https://www.shipindia.com/en/careers
  - https://www.shipindia.com/en/career
  - https://www.shipindia.com/careers/current-openings
- new listUrls: unchanged
- evidence: https://www.shipindia.com/career was HTTP 404. https://www.shipindia.com/page/career was HTTPS 200 title "Shipping Corporation of India | Shipping Company" but the only matched link was an empty "0" notification endpoint https://www.shipindia.com/frontcontroller/notification (also 200, no notice text). Not a proven vacancy list. listUrls unchanged.

## psu_ovl

- status: blocked
- old baseUrl: https://www.ongcindia.com/
- old listUrls:
  - https://www.ongcindia.com/wps/wcm/connect/en/career
  - https://www.ongcindia.com/careers
  - https://www.ongcindia.com/career
  - https://www.ongcindia.com/recruitment
  - https://www.ongcindia.com/jobs
  - https://www.ongcindia.com/en/careers
  - https://www.ongcindia.com/en/career
  - https://www.ongcindia.com/careers/current-openings
- new listUrls: unchanged
- evidence: Registry base is ONGC, not ONGC Videsh. https://ongcvidesh.com/ and https://www.ongcvidesh.com/ failed TLS: unable to get local issuer certificate. No OVL list page was fetched. listUrls unchanged.

## psu_ircon

- status: blocked
- old baseUrl: https://www.ircon.org/
- old listUrls:
  - https://www.ircon.org/index.php?option=com_content&view=article&id=70
  - https://www.ircon.org
  - https://www.ircon.org/careers
  - https://www.ircon.org/career
  - https://www.ircon.org/recruitment
  - https://www.ircon.org/jobs
  - https://www.ircon.org/en/careers
  - https://www.ircon.org/en/career
- new listUrls: unchanged
- evidence: https://ircon.org/ and https://www.ircon.org/ were HTTPS 200 title "Welcome To Ircon International Limited" but matched links were investor/brochure PDFs, not a vacancy list. https://ircon.org/careers, https://www.ircon.org/careers, and https://ircon.org/human-resource were HTTP 404. listUrls unchanged.

## psu_rites

- status: blocked
- old baseUrl: https://www.rites.com/
- old listUrls:
  - https://www.rites.com/Career
  - https://www.rites.com
  - https://www.rites.com/careers
  - https://www.rites.com/career
  - https://www.rites.com/recruitment
  - https://www.rites.com/jobs
  - https://www.rites.com/en/careers
  - https://www.rites.com/en/career
- new listUrls: unchanged
- evidence: HTTPS certificate failure on https://www.rites.com/, https://www.rites.com/Career, and https://rites.com/: unable to get local issuer certificate. https://www.ritesltd.com/ did not resolve. No replacement. listUrls unchanged.

## psu_nfl

- status: blocked
- old baseUrl: https://www.nationalfertilizers.com/
- old listUrls:
  - https://www.nationalfertilizers.com/career
  - https://www.nationalfertilizers.com
  - https://www.nationalfertilizers.com/careers
  - https://www.nationalfertilizers.com/recruitment
  - https://www.nationalfertilizers.com/jobs
  - https://www.nationalfertilizers.com/en/careers
  - https://www.nationalfertilizers.com/en/career
  - https://www.nationalfertilizers.com/careers/current-openings
- new listUrls: unchanged
- evidence: HTTPS 200 final https://www.nationalfertilizers.com/en/career/ title "Careers". Visible links were board-committee and integrity PDFs, not a vacancy or advertisement. Hindi https://www.nationalfertilizers.com/career/ was the same. No captcha text in this browser-UA fetch; the 30 Sep run reported a captcha to the bot UA. listUrls unchanged.

## psu_hudco

- status: verified
- old baseUrl: https://www.hudco.org.in/
- old listUrls:
  - https://www.hudco.org.in/Site/FormTemplete/frmTemp1PLargeTC1C_2.aspx?MnId=46&ParentID=22
  - https://www.hudco.org.in
  - https://www.hudco.org.in/careers
  - https://www.hudco.org.in/career
  - https://www.hudco.org.in/recruitment
  - https://www.hudco.org.in/jobs
  - https://www.hudco.org.in/en/careers
  - https://www.hudco.org.in/en/career
- new baseUrl: https://hudco.org.in/
- new listUrls:
  - https://hudco.org.in/careers
- evidence: Old HUDCO form URL was the daily-run timeout. HTTPS 200 https://hudco.org.in/careers (title empty in the raw fetch). Notice: "Detailed Advertisement" -> https://hudco.org.in/writereaddata/Detailed-Advertisement.pdf. Also "Engagement of Consultants (for UiWIN Vertical) on Full-Time Basis" -> https://hudco.org.in/writereaddata/UiWIN_Advt_04.pdf. https://www.hudco.org.in/ linked "Career" to /careers.

## psu_ireda

- status: verified
- old baseUrl: https://www.ireda.in/
- old listUrls:
  - https://www.ireda.in/career
  - https://www.ireda.in
  - https://www.ireda.in/careers
  - https://www.ireda.in/recruitment
  - https://www.ireda.in/jobs
  - https://www.ireda.in/en/careers
  - https://www.ireda.in/en/career
  - https://www.ireda.in/careers/current-openings
- new baseUrl: https://ireda.in/
- new listUrls:
  - https://ireda.in/IredaWebPortal/careers/recruitment-notifications
- evidence: https://www.ireda.in/career was HTTP 404. https://ireda.in/IredaWebPortal/careers redirected to the notifications path. HTTPS 200 https://ireda.in/IredaWebPortal/careers/recruitment-notifications title "Recruitment Notifications" with text "Current openings and past recruitment drives at IREDA". Notice: Download -> https://ireda.in/IredaWebPortal/documents/careers/recruitment/ireda-hr-01-2026-detail.pdf.

## psu_cwc

- status: blocked
- old baseUrl: https://cewacor.nic.in/
- old listUrls:
  - https://cewacor.nic.in/Index/career
  - https://cewacor.nic.in
  - https://cewacor.nic.in/careers
  - https://cewacor.nic.in/career
  - https://cewacor.nic.in/recruitment
  - https://cewacor.nic.in/jobs
  - https://cewacor.nic.in/en/careers
  - https://cewacor.nic.in/en/career
- new listUrls: unchanged
- evidence: HTTPS certificate failure on https://cewacor.nic.in/, https://cewacor.nic.in/Index/career, and https://www.cewacor.nic.in/: unable to get local issuer certificate. No replacement. listUrls unchanged.

## psu_sjvn

- status: blocked
- old baseUrl: https://sjvn.nic.in/
- old listUrls:
  - https://sjvn.nic.in/career.htm
  - https://sjvn.nic.in
  - https://sjvn.nic.in/careers
  - https://sjvn.nic.in/career
  - https://sjvn.nic.in/recruitment
  - https://sjvn.nic.in/jobs
  - https://sjvn.nic.in/en/careers
  - https://sjvn.nic.in/en/career
- new listUrls: unchanged
- evidence: https://sjvn.nic.in/ and https://sjvn.nic.in/career.htm read timed out. https://www.sjvnindia.com/ TLS EOF. https://sjvnindia.com/ and https://sjvnindia.com/career were HTTPS 200 with an empty title and no recruitment link. https://www.sjvn.co.in/ timed out. No replacement. listUrls unchanged.

## psu_nhpc

- status: verified
- old baseUrl: https://www.nhpcindia.com/
- old listUrls:
  - https://www.nhpcindia.com/Default.aspx?id=122&lg=eng
  - https://www.nhpcindia.com
  - https://www.nhpcindia.com/careers
  - https://www.nhpcindia.com/career
  - https://www.nhpcindia.com/recruitment
  - https://www.nhpcindia.com/jobs
  - https://www.nhpcindia.com/en/careers
  - https://www.nhpcindia.com/en/career
- new baseUrl: https://www.nhpcindia.com/
- new listUrls:
  - https://www.nhpcindia.com/welcome/job
- evidence: https://www.nhpcindia.com/Default.aspx?id=122&lg=eng was HTTP 404. Homepage HTTPS 200 linked "वर्तमान अधिसूचना" to the job list. HTTPS 200 https://www.nhpcindia.com/welcome/job title "नौकरी की सूची - एनएचपीसी इंडिया". Notice: "View Details" -> https://www.nhpcindia.com/assests/pzi_public/job/DV_Notification_12082026.pdf.

## psu_seci

- status: blocked
- old baseUrl: https://www.seci.co.in/
- old listUrls:
  - https://www.seci.co.in/careers
  - https://www.seci.co.in
  - https://www.seci.co.in/career
  - https://www.seci.co.in/recruitment
  - https://www.seci.co.in/jobs
  - https://www.seci.co.in/en/careers
  - https://www.seci.co.in/en/career
  - https://www.seci.co.in/careers/current-openings
- new listUrls: unchanged
- evidence: https://www.seci.co.in/careers and https://www.seci.co.in/career were HTTP 404. https://www.seci.co.in/jobs was HTTPS 200 but the title was the corporate name and the matched file was a jobs user-manual PDF, not a vacancy advertisement. Homepage links found were tender notifications. listUrls unchanged.

## psu_irfc

- status: blocked
- old baseUrl: https://irfc.co.in/
- old listUrls:
  - https://irfc.co.in/career
  - https://irfc.co.in
  - https://irfc.co.in/careers
  - https://irfc.co.in/recruitment
  - https://irfc.co.in/jobs
  - https://irfc.co.in/en/careers
  - https://irfc.co.in/en/career
  - https://irfc.co.in/careers/current-openings
- new listUrls: unchanged
- evidence: https://irfc.co.in/ and https://irfc.co.in/career returned HTTP 302 without a usable recruitment body. https://irfc.co.in/home ended at https://irfc.co.in/hi with an empty title. https://www.irfc.co.in/ and https://irfc.nic.in/ did not resolve. No recruitment list. listUrls unchanged.

## psu_cpcl

- status: verified
- old baseUrl: https://www.cpcl.co.in/
- old listUrls:
  - https://www.cpcl.co.in/careers
  - https://www.cpcl.co.in
  - https://www.cpcl.co.in/career
  - https://www.cpcl.co.in/recruitment
  - https://www.cpcl.co.in/jobs
  - https://www.cpcl.co.in/en/careers
  - https://www.cpcl.co.in/en/career
  - https://www.cpcl.co.in/careers/current-openings
- new baseUrl: https://www.cpcl.co.in/
- new listUrls:
  - https://www.cpcl.co.in/company/people/careers/
- evidence: https://www.cpcl.co.in/careers was HTTP 404 and linked onward to the people/careers path. HTTPS 200 https://www.cpcl.co.in/company/people/careers/ title "Careers – CPCL". Notice: "Engagement of Apprentices 2026-27" -> https://www.cpcl.co.in/wp-content/uploads/2026/09/Notification_20for_20Engagement_20of_20Apprentices_20at_20CPCL_20for_202026-27.cleaned.pdf.

## psu_antrix

- status: blocked
- old baseUrl: https://www.antrix.co.in/
- old listUrls:
  - https://www.antrix.co.in/careers
  - https://www.antrix.co.in
  - https://www.antrix.co.in/career
  - https://www.antrix.co.in/recruitment
  - https://www.antrix.co.in/jobs
  - https://www.antrix.co.in/en/careers
  - https://www.antrix.co.in/en/career
  - https://www.antrix.co.in/careers/current-openings
- new listUrls: unchanged
- evidence: HTTPS 200 https://www.antrix.co.in/career and https://antrix.co.in/career title "Antrix". The only matched link was the Hindi career URL. No vacancy or advertisement link. listUrls unchanged.

## psu_csl

- status: verified
- old baseUrl: https://cochinshipyard.in/
- old listUrls:
  - https://cochinshipyard.in/career
  - https://cochinshipyard.in
  - https://cochinshipyard.in/careers
  - https://cochinshipyard.in/recruitment
  - https://cochinshipyard.in/jobs
  - https://cochinshipyard.in/en/careers
  - https://cochinshipyard.in/en/career
  - https://cochinshipyard.in/careers/current-openings
- new baseUrl: https://cochinshipyard.in/
- new listUrls:
  - https://cochinshipyard.in/Careers
- evidence: https://cochinshipyard.in/career redirected attention to https://cochinshipyard.in/Careers. That URL was HTTPS 200 title "Careers | Cochin Shipyard" and the page text included "Please read Vacancy notification for the posts/User manual before applying online." https://cochinshipyard.in/current-openings was HTTP 404.

## psu_gsl

- status: verified
- old baseUrl: https://goashipyard.in/
- old listUrls:
  - https://goashipyard.in/career
  - https://goashipyard.in
  - https://goashipyard.in/careers
  - https://goashipyard.in/recruitment
  - https://goashipyard.in/jobs
  - https://goashipyard.in/en/careers
  - https://goashipyard.in/en/career
  - https://goashipyard.in/careers/current-openings
- new baseUrl: https://goashipyard.in/
- new listUrls:
  - https://goashipyard.in/
- evidence: https://goashipyard.in/career/ and https://goashipyard.in/human-resource/careers both finished on https://goashipyard.in/ HTTPS 200 title "Home Page | Goa Shipyard Official Website". Notice: "CANDIDATES PROVISIONALLY SHORTLISTED FOR DOCUMENT VERIFICATION & PERSONAL INTERVIEW FOR THE POST OF MANAGEMENT TRAINEE (ELECTRICAL) - (ADVT. NO. 06/2025)" -> https://goashipyard.in/storage/clients_logo/1787558006.pdf. A bot-wall phrase also matched somewhere in the HTML, but the shortlist links were in the body.

## psu_hcl

- status: verified
- old baseUrl: https://www.hindustancopper.com/
- old listUrls:
  - https://www.hindustancopper.com/Page/Career
- new baseUrl: https://www.hindustancopper.com/
- new listUrls:
  - https://www.hindustancopper.com/Page/Career_new
- evidence: https://www.hindustancopper.com/Page/Career was HTTP 500. HTTPS 200 https://www.hindustancopper.com/Page/Career_new title "Hindustan Copper Limited". Notice: "Notice regarding Recruitment Notification for appointment on Fixed Tenure Basis" -> https://hindustancopper.com/Content/Admin/AnnouncementFiles/fixed tenure basis.pdf.

## psu_itpo

- status: blocked
- old baseUrl: https://indiatradefair.com/
- old listUrls:
  - https://indiatradefair.com
  - https://www.indiatradefair.com/career
  - https://indiatradefair.com/careers
  - https://indiatradefair.com/career
  - https://indiatradefair.com/recruitment
  - https://indiatradefair.com/jobs
  - https://indiatradefair.com/en/careers
  - https://indiatradefair.com/en/career
- new listUrls: unchanged
- evidence: https://indiatradefair.com/ and https://www.indiatradefair.com/ were HTTPS 200 title "ITPO" with no recruitment link. https://indiatradefair.com/career was HTTP 404. listUrls unchanged.

## psu_kiocl

- status: blocked
- old baseUrl: https://www.kioclltd.in/
- old listUrls:
  - https://www.kioclltd.in/career
  - https://www.kioclltd.in
  - https://www.kioclltd.in/careers
  - https://www.kioclltd.in/recruitment
  - https://www.kioclltd.in/jobs
  - https://www.kioclltd.in/en/careers
  - https://www.kioclltd.in/en/career
  - https://www.kioclltd.in/careers/current-openings
- new listUrls: unchanged
- evidence: https://www.kioclltd.in/ was HTTPS 200 title "KIOCL Ltd." without a vacancy link. https://www.kioclltd.in/career and https://www.kioclltd.in/careers were HTTP 404. listUrls unchanged.

## psu_mcl

- status: blocked
- old baseUrl: https://www.mahanadicoal.in/
- old listUrls:
  - https://www.mahanadicoal.in/career.php
  - https://www.mahanadicoal.in
  - https://www.mahanadicoal.in/careers
  - https://www.mahanadicoal.in/career
  - https://www.mahanadicoal.in/recruitment
  - https://www.mahanadicoal.in/jobs
  - https://www.mahanadicoal.in/en/careers
  - https://www.mahanadicoal.in/en/career
- new listUrls: unchanged
- evidence: https://www.mahanadicoal.in/career.php was HTTP 404. https://www.mahanadicoal.in/career was HTTPS 200 but the links were a trading-window closure and an NIT, not recruitment. listUrls unchanged.

## psu_mrpl

- status: blocked
- old baseUrl: https://www.mrpl.co.in/
- old listUrls:
  - https://www.mrpl.co.in/careers
  - https://www.mrpl.co.in
  - https://www.mrpl.co.in/career
  - https://www.mrpl.co.in/recruitment
  - https://www.mrpl.co.in/jobs
  - https://www.mrpl.co.in/en/careers
  - https://www.mrpl.co.in/en/career
  - https://www.mrpl.co.in/careers/current-openings
- new listUrls: unchanged
- evidence: https://www.mrpl.co.in/, https://www.mrpl.co.in/careers, and https://mrpl.co.in/ returned HTTP 503. No replacement. listUrls unchanged.

## psu_mecl

- status: blocked
- old baseUrl: https://www.mecl.co.in/
- old listUrls:
  - https://www.mecl.co.in/career
  - https://www.mecl.co.in
  - https://www.mecl.co.in/careers
  - https://www.mecl.co.in/recruitment
  - https://www.mecl.co.in/jobs
  - https://www.mecl.co.in/en/careers
  - https://www.mecl.co.in/en/career
  - https://www.mecl.co.in/careers/current-openings
- new listUrls: unchanged
- evidence: https://www.mecl.co.in/ and https://www.mecl.co.in/career did not resolve. https://mecl.gov.in/ timed out. No replacement. listUrls unchanged.

## psu_mmtc

- status: blocked
- old baseUrl: https://www.mmtclimited.com/
- old listUrls:
  - https://www.mmtclimited.com/career
  - https://www.mmtclimited.com
  - https://www.mmtclimited.com/careers
  - https://www.mmtclimited.com/recruitment
  - https://www.mmtclimited.com/jobs
  - https://www.mmtclimited.com/en/careers
  - https://www.mmtclimited.com/en/career
  - https://www.mmtclimited.com/careers/current-openings
- new listUrls: unchanged
- evidence: https://www.mmtclimited.com/ and https://www.mmtclimited.com/career were HTTP 400. https://mmtclimited.com/career redirected to the homepage, whose links were annual-report PDFs, not vacancies. listUrls unchanged.

## psu_pdil

- status: blocked
- old baseUrl: https://www.pdilin.com/
- old listUrls:
  - https://www.pdilin.com/career
  - https://www.pdilin.com
  - https://www.pdilin.com/careers
  - https://www.pdilin.com/recruitment
  - https://www.pdilin.com/jobs
  - https://www.pdilin.com/en/careers
  - https://www.pdilin.com/en/career
  - https://www.pdilin.com/careers/current-openings
- new listUrls: unchanged
- evidence: HTTPS certificate failure on https://www.pdilin.com/, https://www.pdilin.com/career, and https://pdilin.com/: certificate has expired. No replacement. listUrls unchanged.

## psu_secl

- status: verified
- old baseUrl: https://www.secl-cil.in/
- old listUrls:
  - https://www.secl-cil.in
  - https://www.secl-cil.in/career
  - https://www.secl-cil.in/careers
  - https://www.secl-cil.in/recruitment
  - https://www.secl-cil.in/jobs
  - https://www.secl-cil.in/en/careers
  - https://www.secl-cil.in/en/career
  - https://www.secl-cil.in/careers/current-openings
- new baseUrl: https://secl-cil.in/
- new listUrls:
  - https://secl-cil.in/index
- evidence: Browser-UA HTTPS 200. https://www.secl-cil.in/ finished at https://secl-cil.in/index title "South Eastern Coalfields Limited". Notice: "Notification for engagement of Consultant (Medical Specialist) in various specializations and Consultant" -> https://secl-cil.in/writereaddata/753c4fb3608e5bc88f4c11120248c2ce90f6e72648c656ddfb3d269369ab911d.pdf. The 30 Sep run reported HTTP 403 to the bot UA, so a non-browser client may still be refused.

## psu_wcl

- status: blocked
- old baseUrl: https://westerncoal.in/
- old listUrls:
  - https://westerncoal.in/index.php/career
  - https://westerncoal.in
  - https://westerncoal.in/careers
  - https://westerncoal.in/career
  - https://westerncoal.in/recruitment
  - https://westerncoal.in/jobs
  - https://westerncoal.in/en/careers
  - https://westerncoal.in/en/career
- new listUrls: unchanged
- evidence: https://westerncoal.in/ and https://www.westerncoal.in/ were HTTPS 200 with no recruitment signal. https://westerncoal.in/index.php/career and https://westerncoal.in/career were HTTP 404. listUrls unchanged.

## psu_wapcos

- status: verified
- old baseUrl: https://www.wapcos.gov.in/
- old listUrls:
  - https://www.wapcos.gov.in/career
  - https://www.wapcos.gov.in
  - https://www.wapcos.gov.in/careers
  - https://www.wapcos.gov.in/recruitment
  - https://www.wapcos.gov.in/jobs
  - https://www.wapcos.gov.in/en/careers
  - https://www.wapcos.gov.in/en/career
  - https://www.wapcos.gov.in/careers/current-openings
- new baseUrl: https://www.wapcos.co.in/
- new listUrls:
  - https://www.wapcos.co.in/english/career/
- evidence: https://www.wapcos.gov.in/ did not resolve. https://www.wapcos.co.in/career/ was HTTP 404 and pointed at the English career path. HTTPS 200 https://www.wapcos.co.in/english/career/ title "Current Advertisements | WAPCOS Limited" with text "Explore current openings and related documents." Notice: Advertisement PDF -> https://www.wapcos.co.in/media/docs/Career/Advertisement_VGypb0_ezmSEP.pdf.

## psu_epi

- status: verified
- old baseUrl: https://www.engineeringprojects.com/
- old listUrls:
  - https://www.engineeringprojects.com/career
  - https://www.engineeringprojects.com
  - https://www.engineeringprojects.com/careers
  - https://www.engineeringprojects.com/recruitment
  - https://www.engineeringprojects.com/jobs
  - https://www.engineeringprojects.com/en/careers
  - https://www.engineeringprojects.com/en/career
  - https://www.engineeringprojects.com/careers/current-openings
- new baseUrl: https://epi.gov.in/
- new listUrls:
  - https://epi.gov.in/career
- evidence: https://www.engineeringprojects.com/ failed TLS: hostname mismatch. HTTPS 200 https://epi.gov.in/career title starts "Career | EPI" and the page text includes "HRD Current Openings". Same host linked "Current Openings" to https://epi.gov.in/career. https://www.epi.gov.in/ redirected to that career path.

## psu_fsnl

- status: blocked
- old baseUrl: https://mtnl.in/
- old listUrls:
  - https://mtnl.in/career.html
  - https://mtnl.in
  - https://mtnl.in/careers
  - https://mtnl.in/career
  - https://mtnl.in/recruitment
  - https://mtnl.in/jobs
  - https://mtnl.in/en/careers
  - https://mtnl.in/en/career
- new listUrls: unchanged
- evidence: Registry list URLs are MTNL, the wrong organisation (https://mtnl.in/career.html was HTTP 404). https://fsnl.nic.in/ and https://www.fsnl.nic.in/ did not resolve. No FSNL recruitment list was fetched. listUrls unchanged.

## psu_nfdc

- status: blocked
- old baseUrl: https://www.nfdcindia.com/
- old listUrls:
  - https://www.nfdcindia.com
  - https://www.nfdcindia.com/career
  - https://www.nfdcindia.com/careers
  - https://www.nfdcindia.com/recruitment
  - https://www.nfdcindia.com/jobs
  - https://www.nfdcindia.com/en/careers
  - https://www.nfdcindia.com/en/career
  - https://www.nfdcindia.com/careers/current-openings
- new listUrls: unchanged
- evidence: https://www.nfdcindia.com/ and https://nfdcindia.com/ were HTTPS 200 title "NFDC: Cinemas of india" with no recruitment link. https://nfdcindia.com/careers and https://www.nfdcindia.com/careers were the same homepage title and no vacancy link. listUrls unchanged.

## psu_bbnl

- status: blocked
- old baseUrl: https://bbnl.nic.in/
- old listUrls:
  - https://bbnl.nic.in
  - https://bbnl.nic.in/career
  - https://bbnl.nic.in/careers
  - https://bbnl.nic.in/recruitment
  - https://bbnl.nic.in/jobs
  - https://bbnl.nic.in/en/careers
  - https://bbnl.nic.in/en/career
  - https://bbnl.nic.in/careers/current-openings
- new listUrls: unchanged
- evidence: https://bbnl.nic.in/ timed out. No replacement. listUrls unchanged.

## psu_eml

- status: blocked
- old baseUrl: https://www.bhel.com/
- old listUrls:
  - https://www.bhel.com/careers
  - https://careers.bhel.in
  - https://www.bhel.com/career
  - https://www.bhel.com/recruitment
  - https://www.bhel.com/jobs
  - https://www.bhel.com/en/careers
  - https://www.bhel.com/en/career
  - https://www.bhel.com/careers/current-openings
- new listUrls: unchanged
- evidence: Registry list URLs are BHEL corporate, not BHEL Electrical Machines. https://www.bheleml.com/ and https://bheleml.com/ did not resolve. No EML recruitment list was fetched. listUrls unchanged.

## psu_kpl

- status: verified
- old baseUrl: https://www.ennoreport.gov.in/
- old listUrls:
  - https://www.ennoreport.gov.in
  - https://www.ennoreport.gov.in/careers
  - https://www.ennoreport.gov.in/career
  - https://www.ennoreport.gov.in/recruitment
  - https://www.ennoreport.gov.in/jobs
  - https://www.ennoreport.gov.in/en/careers
  - https://www.ennoreport.gov.in/en/career
  - https://www.ennoreport.gov.in/careers/current-openings
- new baseUrl: https://www.kamarajarport.in/
- new listUrls:
  - https://www.kamarajarport.in/career
- evidence: https://www.ennoreport.gov.in/ timed out. HTTPS 200 https://www.kamarajarport.in/career title "Careers - Kamarajar Port Limited". Page text included "Career Opportunities" and the column headings "Advertisement No. Vacancy Title". https://kamarajarport.in/career is the same site.

## psu_brahmos

- status: verified
- old baseUrl: https://www.brahmos.com/
- old listUrls:
  - https://www.brahmos.com/content.php?id=13
  - https://www.brahmos.com
  - https://www.brahmos.com/careers
  - https://www.brahmos.com/career
  - https://www.brahmos.com/recruitment
  - https://www.brahmos.com/jobs
  - https://www.brahmos.com/en/careers
  - https://www.brahmos.com/en/career
- new baseUrl: https://www.brahmos.com/
- new listUrls:
  - https://www.brahmos.com/career-opening
- evidence: https://www.brahmos.com/content.php?id=13 was HTTP 404. Homepage linked careers to https://www.brahmos.com/career-opening which was HTTPS 200 title "Current Opening". Linked file on that page: https://www.brahmos.com/public/uploads/Career_Disclaimer.pdf ("Click here to read more"). No separate live advertisement PDF was linked.

## psu_eesl

- status: blocked
- old baseUrl: https://eeslindia.org/
- old listUrls:
  - https://eeslindia.org/en/careers
  - https://eeslindia.org
  - https://eeslindia.org/careers
  - https://eeslindia.org/career
  - https://eeslindia.org/recruitment
  - https://eeslindia.org/jobs
  - https://eeslindia.org/en/career
  - https://eeslindia.org/careers/current-openings
- new listUrls: unchanged
- evidence: https://eeslindia.org/en/careers and https://eeslindia.org/careers/ and https://eeslindia.org/hi/career were HTTP 404. https://eeslindia.org/ redirected to https://eeslindia.org/hi/ which had no vacancy list (Hindi-pakhwada and an advocate empanelment PDF). listUrls unchanged.

## psu_ciwtc

- status: blocked
- old baseUrl: https://www.pfcindia.com/
- old listUrls:
  - https://www.pfcindia.com/Home/VS/77
  - https://www.pfcindia.com
  - https://www.pfcindia.com/careers
  - https://www.pfcindia.com/career
  - https://www.pfcindia.com/recruitment
  - https://www.pfcindia.com/jobs
  - https://www.pfcindia.com/en/careers
  - https://www.pfcindia.com/en/career
- new listUrls: unchanged
- evidence: Registry list URLs are PFC, the wrong organisation. PFC hosts failed TLS hostname mismatch (see psu_pfc). No CIWTC recruitment list was fetched. listUrls unchanged.

## psu_dfccil

- status: verified
- old baseUrl: https://dfccil.com/
- old listUrls:
  - https://dfccil.com/Home/DynamicPages?MenuId=48
  - https://dfccil.com
  - https://dfccil.com/careers
  - https://dfccil.com/career
  - https://dfccil.com/recruitment
  - https://dfccil.com/jobs
  - https://dfccil.com/en/careers
  - https://dfccil.com/en/career
- new baseUrl: https://dfccil.com/
- new listUrls:
  - https://dfccil.com/Carrer/index?Id=1
- evidence: Browser-UA HTTPS 200 https://dfccil.com/Carrer/index?Id=1 title "DFCCIL". The old menu URL also linked "Open Market Recruitment" to this path. Notice: "Schedule for Physical Efficiency Test (PET) for Multi-Tasking Staff against Advt No.01/DR/2025" -> https://dfccil.com/upload/PET-schedule-notice-with-annexures_ZDMQ.pdf. The 30 Sep run reported HTTP 403 to the bot UA.

## psu_ecgc

- status: verified
- old baseUrl: https://www.ecgc.in/
- old listUrls:
  - https://www.ecgc.in/career
  - https://www.ecgc.in
  - https://www.ecgc.in/careers
  - https://www.ecgc.in/recruitment
  - https://www.ecgc.in/jobs
  - https://www.ecgc.in/en/careers
  - https://www.ecgc.in/en/career
  - https://www.ecgc.in/careers/current-openings
- new baseUrl: https://main.ecgc.in/
- new listUrls:
  - https://main.ecgc.in/career-with-ecgc/
- evidence: https://www.ecgc.in/career and https://www.ecgc.in/ and https://ecgc.in/ redirected to https://main.ecgc.in/ (left ecgc.in). HTTPS 200 https://main.ecgc.in/career-with-ecgc/ title is the ECGC career-with-ECGC page. Notice: "Detailed Hindi Advertisement for Recruitment of Probationary Officers for FY 2025-26" -> https://main.ecgc.in/wp-content/themes/pcwebecgc/images/pcECGPagePDF/careerwithecgc/Detailed Hindi Advertisement for Recruitment of Probationary Officers for FY 2025-26.pdf.

## psu_ecil

- status: verified
- old baseUrl: https://www.ecil.co.in/
- old listUrls:
  - https://www.ecil.co.in/career.html
  - https://www.ecil.co.in
  - https://www.ecil.co.in/careers
  - https://www.ecil.co.in/career
  - https://www.ecil.co.in/recruitment
  - https://www.ecil.co.in/jobs
  - https://www.ecil.co.in/en/careers
  - https://www.ecil.co.in/en/career
- new baseUrl: https://www.ecil.co.in/
- new listUrls:
  - https://www.ecil.co.in/jobopenings
- evidence: https://www.ecil.co.in/career.html was HTTP 404 and linked "Current Job Openings" to the job page. HTTPS 200 https://www.ecil.co.in/jobopenings title "Current Job Openings | ECIL | DAE | India". Notice: "Advertisement" -> https://www.ecil.co.in/jobs/ITI_ADVERTISEMENT_11_2026.pdf. https://ecil.co.in/ without www failed TLS hostname mismatch.

## psu_hal_pharma

- status: blocked
- old baseUrl: https://www.hindantibiotics.in/
- old listUrls:
  - https://www.hindantibiotics.in
  - https://www.hindantibiotics.in/careers
  - https://www.hindantibiotics.in/career
  - https://www.hindantibiotics.in/recruitment
  - https://www.hindantibiotics.in/jobs
  - https://www.hindantibiotics.in/en/careers
  - https://www.hindantibiotics.in/en/career
  - https://www.hindantibiotics.in/careers/current-openings
- new listUrls: unchanged
- evidence: HTTPS certificate failure on https://www.hindantibiotics.in/ and https://hindantibiotics.in/: self-signed certificate. No replacement. listUrls unchanged.

## psu_hfcl

- status: blocked
- old baseUrl: https://www.pfcindia.com/
- old listUrls:
  - https://www.pfcindia.com/Home/VS/77
  - https://www.pfcindia.com
  - https://www.pfcindia.com/careers
  - https://www.pfcindia.com/career
  - https://www.pfcindia.com/recruitment
  - https://www.pfcindia.com/jobs
  - https://www.pfcindia.com/en/careers
  - https://www.pfcindia.com/en/career
- new listUrls: unchanged
- evidence: Registry list URLs are PFC, the wrong organisation. PFC hosts failed TLS hostname mismatch. No Hindustan Fertilizers recruitment list was fetched. listUrls unchanged.

## psu_idrcl

- status: blocked
- old baseUrl: https://gailonline.com/
- old listUrls:
  - https://gailonline.com/ZBCareergail.html
  - https://gailonline.com
  - https://gailonline.com/careers
  - https://gailonline.com/career
  - https://gailonline.com/recruitment
  - https://gailonline.com/jobs
  - https://gailonline.com/en/careers
  - https://gailonline.com/en/career
- new listUrls: unchanged
- evidence: Registry list URLs are GAIL, the wrong organisation. https://idrcl.in/ and https://www.idrcl.in/ did not resolve. https://www.idrcl.co.in/ failed TLS: certificate has expired. No replacement. listUrls unchanged.

## psu_iifcl

- status: verified
- old baseUrl: https://gailonline.com/
- old listUrls:
  - https://gailonline.com/ZBCareergail.html
  - https://gailonline.com
  - https://gailonline.com/careers
  - https://gailonline.com/career
  - https://gailonline.com/recruitment
  - https://gailonline.com/jobs
  - https://gailonline.com/en/careers
  - https://gailonline.com/en/career
- new baseUrl: https://iifcl.in/
- new listUrls:
  - https://iifcl.in/Careers
- evidence: Registry list URLs were GAIL. https://iifcl.in/ and https://www.iifcl.in/ were HTTPS 200 and linked "भर्ती" to https://iifcl.in/Careers. That page was HTTPS 200 title "भर्ती" heading "भर्ती". No individual advertisement PDF was linked in the fetched HTML (the visible list under the heading was charter, GST, and tenders). https://www.iifcl.org/ refused the connection.

## psu_ihmcl

- status: verified
- old baseUrl: https://www.mahanadicoal.in/
- old listUrls:
  - https://www.mahanadicoal.in/career.php
  - https://www.mahanadicoal.in
  - https://www.mahanadicoal.in/careers
  - https://www.mahanadicoal.in/career
  - https://www.mahanadicoal.in/recruitment
  - https://www.mahanadicoal.in/jobs
  - https://www.mahanadicoal.in/en/careers
  - https://www.mahanadicoal.in/en/career
- new baseUrl: https://ihmcl.co.in/
- new listUrls:
  - https://ihmcl.co.in/careers/
- evidence: Registry list URLs were Mahanadi Coalfields. HTTPS 200 https://ihmcl.co.in/ title "IHMCL – Indian Highways Management Company Limited" linked "CAREERS" to https://ihmcl.co.in/careers/. That page was HTTPS 200 title "Careers – IHMCL". Notice: "Advertisement" -> https://ihmcl.co.in/wp-content/uploads/2026/07/Advertisement-CISO-1.pdf.

## psu_isprl

- status: verified
- old baseUrl: https://www.isprlindia.com/
- old listUrls:
  - https://www.isprlindia.com
  - https://www.isprlindia.com/careers
  - https://www.isprlindia.com/career
  - https://www.isprlindia.com/recruitment
  - https://www.isprlindia.com/jobs
  - https://www.isprlindia.com/en/careers
  - https://www.isprlindia.com/en/career
  - https://www.isprlindia.com/careers/current-openings
- new baseUrl: https://www.isprlindia.com/
- new listUrls:
  - https://www.isprlindia.com/career.asp
- evidence: Homepage https://www.isprlindia.com/ was HTTPS 200 and linked Career to https://www.isprlindia.com/career.asp. That page was HTTPS 200 title "ISPRL : Indian Strategic Petroleum Reserves Limited". Notice: "Details & Application Format for Chief Executive Officer & Managing Director" -> https://www.isprlindia.com/downloads/career/vacancy-detail-ceo-md.pdf.

## psu_icsil

- status: verified
- old baseUrl: https://gailonline.com/
- old listUrls:
  - https://gailonline.com/ZBCareergail.html
  - https://gailonline.com
  - https://gailonline.com/careers
  - https://gailonline.com/career
  - https://gailonline.com/recruitment
  - https://gailonline.com/jobs
  - https://gailonline.com/en/careers
  - https://gailonline.com/en/career
- new baseUrl: https://www.icsil.in/
- new listUrls:
  - https://www.icsil.in/requirement-careers
- evidence: Registry list URLs were GAIL. https://www.icsil.in/career was HTTP 404 and linked "Current Jobs" to the requirement page. HTTPS 200 https://www.icsil.in/requirement-careers title "Current Jobs | Intelligent Communication Systems India Ltd, GOI". Notice: Download -> https://icsil.in/app/uploads/advertisement/Advertisement_-_Deputation_Posts25_9_20268.pdf.

## psu_munpl

- status: blocked
- old baseUrl: https://mtnl.in/
- old listUrls:
  - https://mtnl.in/career.html
  - https://mtnl.in
  - https://mtnl.in/careers
  - https://mtnl.in/career
  - https://mtnl.in/recruitment
  - https://mtnl.in/jobs
  - https://mtnl.in/en/careers
  - https://mtnl.in/en/career
- new listUrls: unchanged
- evidence: Registry list URLs are MTNL, the wrong organisation. No Meja Urja Nigam recruitment site was fetched. listUrls unchanged.

## psu_nhidcl

- status: verified
- old baseUrl: https://www.hudco.org.in/
- old listUrls:
  - https://www.hudco.org.in/Site/FormTemplete/frmTemp1PLargeTC1C_2.aspx?MnId=46&ParentID=22
  - https://www.hudco.org.in
  - https://www.hudco.org.in/careers
  - https://www.hudco.org.in/career
  - https://www.hudco.org.in/recruitment
  - https://www.hudco.org.in/jobs
  - https://www.hudco.org.in/en/careers
  - https://www.hudco.org.in/en/career
- new baseUrl: https://nhidcl.com/
- new listUrls:
  - https://nhidcl.com/current-jobs
- evidence: Registry list URLs were HUDCO. HTTPS 200 https://nhidcl.com/ title "Home Page | National Highways & Infrastructure Development Corporation Ltd." linked "Current Jobs" to https://nhidcl.com/current-jobs. That page was HTTPS 200 title "Current Vacancies | National Highways & Infrastructure Development Corporation Ltd.". Notice: hiring-notice PDF -> https://nhidcl.com/sites/default/files/2025-12/window_advt_hiring_notice_no._05_of_2025_addendum_23.12.2025.pdf.

## psu_npci

- status: blocked
- old baseUrl: https://www.nfdcindia.com/
- old listUrls:
  - https://www.nfdcindia.com
  - https://www.nfdcindia.com/career
  - https://www.nfdcindia.com/careers
  - https://www.nfdcindia.com/recruitment
  - https://www.nfdcindia.com/jobs
  - https://www.nfdcindia.com/en/careers
  - https://www.nfdcindia.com/en/career
  - https://www.nfdcindia.com/careers/current-openings
- new listUrls: unchanged
- evidence: Registry list URLs are NFDC, the wrong organisation. https://www.npci.org.in/, https://npci.org.in/, and https://www.npci.org.in/what-we-do/careers/job-openings were HTTPS 403 title "Access Denied". No replacement. listUrls unchanged.

## psu_remcl

- status: blocked
- old baseUrl: https://www.mahanadicoal.in/
- old listUrls:
  - https://www.mahanadicoal.in/career.php
  - https://www.mahanadicoal.in
  - https://www.mahanadicoal.in/careers
  - https://www.mahanadicoal.in/career
  - https://www.mahanadicoal.in/recruitment
  - https://www.mahanadicoal.in/jobs
  - https://www.mahanadicoal.in/en/careers
  - https://www.mahanadicoal.in/en/career
- new listUrls: unchanged
- evidence: Registry list URLs were Mahanadi Coalfields. https://www.remcltd.com/ was HTTPS 200 title "REMC Limited | Welcome To Official Website REMCL". Its "Careers" link went to https://www.remcltd.com/People-at-REMCL, and the fetched homepage links were GHG worksheets, not vacancies. listUrls unchanged.

## psu_rhpc

- status: blocked
- old baseUrl: https://www.powergrid.in/
- old listUrls:
  - https://www.powergrid.in/en/career
  - https://www.powergrid.in/careers
  - https://www.powergrid.in/career
  - https://www.powergrid.in/recruitment
  - https://www.powergrid.in/jobs
  - https://www.powergrid.in/en/careers
  - https://www.powergrid.in/careers/current-openings
  - https://www.powergrid.in/human-resources/careers
- new listUrls: unchanged
- evidence: Registry list URLs are POWERGRID, the wrong organisation. No Ratle Hydro recruitment site was fetched. listUrls unchanged.

## psu_utiitsl

- status: blocked
- old baseUrl: https://eeslindia.org/
- old listUrls:
  - https://eeslindia.org/en/careers
  - https://eeslindia.org
  - https://eeslindia.org/careers
  - https://eeslindia.org/career
  - https://eeslindia.org/recruitment
  - https://eeslindia.org/jobs
  - https://eeslindia.org/en/career
  - https://eeslindia.org/careers/current-openings
- new listUrls: unchanged
- evidence: Registry list URLs are EESL, the wrong organisation. https://www.utiitsl.com/careers and https://utiitsl.com/careers were HTTPS 200 but the files were PFRDA/PAN downloads, not a vacancy list. listUrls unchanged.

## upsc_calendar

- status: verified
- old baseUrl: https://upsc.gov.in/
- old listUrls:
  - https://upsc.gov.in/examinations/exam-calendar
- new baseUrl: https://www.upsc.gov.in/
- new listUrls:
  - https://www.upsc.gov.in/examinations/exam-calendar
- evidence: https://upsc.gov.in/examinations/exam-calendar returned HTTP 307 to https://www.upsc.gov.in/ (homepage), so the non-www path does not stay on the calendar. HTTPS 200 https://www.upsc.gov.in/examinations/exam-calendar title "Calendar | UPSC" heading "Calendar". Notice: "Annual Calendar 2027" -> https://www.upsc.gov.in/sites/default/files/Calendar-Year-2027-Engl-200526.pdf (uploaded 20/05/2026). A noscript line asks for JavaScript; the calendar rows were still in the fetched HTML. The 30 Sep run reported HTTP 403 to the bot UA.

## upsc_active

- status: verified
- old baseUrl: https://upsc.gov.in/
- old listUrls:
  - https://upsc.gov.in/examinations/active-exams
- new baseUrl: https://www.upsc.gov.in/
- new listUrls:
  - https://www.upsc.gov.in/examinations/active-exams
- evidence: https://upsc.gov.in/examinations/active-exams returned HTTP 307 to the UPSC homepage. HTTPS 200 https://www.upsc.gov.in/examinations/active-exams title "Active Examinations | UPSC" heading "Active Examinations". Notice heading on the page: "Civil Services (Main) Examination, 2026". Also listed: "Engineering Services (Preliminary) Examination, 2027". The 30 Sep run reported HTTP 403 to the bot UA.

## ibps_home

- status: verified-same
- old baseUrl: https://www.ibps.in/
- old listUrls:
  - https://www.ibps.in/
- new listUrls: unchanged
- evidence: HTTPS 200 final https://www.ibps.in/ title "ibps" heading "Welcome to IBPS". Notice: "Updated Vacancies as on 29.09.2026 for Common Recruitment Process for CRP-CSA-XVI" -> https://www.ibps.in/wp-content/uploads/Annexure_Corrigendum_29.09.2026.pdf. https://ibps.in/ was the same page. Certificate checks stayed on and this fetch succeeded. listUrls unchanged.

## ibps_calendar

- status: verified-same
- old baseUrl: https://www.ibps.in/
- old listUrls:
  - https://www.ibps.in/index.php/crp-updates/
- new listUrls: unchanged
- evidence: HTTPS 200 final https://www.ibps.in/index.php/crp-updates/ title "CRP Updates – ibps" heading "Recent Updates". Notice: "29 Sep 26 Updated Vacancies as on 29.09.2026 for Common Recruitment Process for CRP-CSA-XVI" -> https://www.ibps.in/wp-content/uploads/Annexure_Corrigendum_29.09.2026.pdf. Also "01 Jul 26 Notification for Common Recruitment Process for CRP- PO/MTs-XVI". The 30 Sep PKIX error was not reproduced with a default trust store. listUrls unchanged.

## sbi_openings

- status: verified
- old baseUrl: https://sbi.co.in/web/careers/current-openings
- old listUrls:
  - https://sbi.co.in/web/careers/current-openings
- new baseUrl: https://sbi.bank.in/
- new listUrls:
  - https://sbi.bank.in/web/careers/current-openings
- evidence: https://sbi.co.in/web/careers/current-openings, https://bank.sbi/web/careers/current-openings, and https://www.sbi.co.in/web/careers/current-openings all 301 to https://sbi.bank.in/web/careers/current-openings (different registrable site, so the collector stops). That final URL was HTTPS 200. Rendered page heading "Current Openings". Notice text: "ENGAGEMENT OF SPECIALIST CADRE OFFICERS ON CONTRACT BASIS" advertisement CRPD/SCO/2026-27/25, last date 21-10-2026. Apply link seen for CRPD/SCO/2026-27/20 -> https://recruitment.sbi.bank.in/crpd-sco-2026-27-20/apply. The raw HTML shell had an empty title, so a non-JS client may still see no anchors.

## rrb_chennai

- status: verified
- old baseUrl: https://www.rrbchennai.gov.in/
- old listUrls:
  - https://www.rrbchennai.gov.in/
- new baseUrl: https://rrb.indianrailways.gov.in/
- new listUrls:
  - https://rrb.indianrailways.gov.in/chennai
- evidence: https://www.rrbchennai.gov.in/ failed TLS: hostname mismatch. https://rrbchennai.gov.in/ was HTTPS 302 to https://rrb.indianrailways.gov.in/chennai, which was HTTPS 200 title "RRB" heading "Government of India, Ministry of Railways Railway Recruitment Board, Chennai". Notice: "Notification" -> https://rrb.indianrailways.gov.in/getdata?cennum=03/2026&loc=chennai&category=Notification. The redirect leaves rrbchennai.gov.in, so the list URL is the railways host.

## psc_bpsc

- status: blocked
- old baseUrl: https://www.bpsc.bih.nic.in/
- old listUrls:
  - https://www.bpsc.bih.nic.in/
- new listUrls: unchanged
- evidence: https://www.bpsc.bih.nic.in/ timed out. https://bpsc.bihar.gov.in/ failed TLS: unable to get local issuer certificate. https://www.bpsc.bihar.gov.in/ did not resolve. A page fetch that did not prove a valid certificate is not used as a replacement. listUrls unchanged.

## psc_wbpsc

- status: blocked
- old baseUrl: https://psc.wb.gov.in/
- old listUrls:
  - https://psc.wb.gov.in/
- new listUrls: unchanged
- evidence: https://psc.wb.gov.in/ failed TLS: unsafe legacy renegotiation disabled. https://wbpsc.gov.in/ timed out. No replacement. listUrls unchanged.

## epfo_recruitment

- status: verified
- old baseUrl: https://www.epfindia.gov.in/
- old listUrls:
  - https://www.epfindia.gov.in/site_en/Recruitments.php
- new baseUrl: https://www.epfo.gov.in/
- new listUrls:
  - https://www.epfo.gov.in/recruitments/
- evidence: https://www.epfindia.gov.in/site_en/Recruitments.php redirected to https://www.epfo.gov.in/site_en/Recruitments.php which was HTTP 404. https://www.epfindia.gov.in/ redirected to https://www.epfo.gov.in/ HTTPS 200 and linked "Recruitments" -> https://www.epfo.gov.in/recruitments/. That URL was HTTPS 200 title "Employees' Provident Fund Organisation". The HTML anchors on the recruitments page itself were the section link and Archives (https://www.epfo.gov.in/archive-recruitments/), not a single advertisement PDF. A second fetch of the recruitments page did not return body text.

## drdo_careers

- status: verified
- old baseUrl: https://www.drdo.gov.in/
- old listUrls:
  - https://www.drdo.gov.in/drdo/careers
- new baseUrl: https://rac.gov.in/
- new listUrls:
  - https://rac.gov.in/index.php?lang=en&id=0
  - https://www.drdo.gov.in/drdo/offerings/vacancies
- evidence: https://www.drdo.gov.in/drdo/careers and https://www.drdo.gov.in/careers were HTTP 404. https://www.drdo.gov.in/drdo/offerings/vacancies was HTTPS 200 title "Vacancies | Defence Research and Development Organisation - DRDO". The notice board with files is RAC: https://rac.gov.in/ redirected to https://rac.gov.in/index.php?lang=en&id=0 HTTPS 200 title "RAC, DRDO" heading "Ongoing Recruitment Opportunities in DRDO". Notice: "Advertisement" -> https://rac.gov.in/download/advt_157.pdf. Second fetched page kept: the DRDO vacancies URL.

## nta_ugc_net_calendar

- status: verified
- old baseUrl: https://ugcnet.nta.nic.in/
- old listUrls:
  - https://ugcnet.nta.nic.in/
- new baseUrl: https://ugcnet.nta.ac.in/
- new listUrls:
  - https://ugcnet.nta.ac.in/
- evidence: https://ugcnet.nta.nic.in/ was HTTPS 200 title "University Grants Commission (UGC)-NET | India" with no notice link (the 30 Sep run had reported HTTP 403). https://ugcnet.nta.ac.in/ was HTTPS 200 same title. Notice: "Opening of the online portal for submission of Online Application Form for UGC-NET June 2025 - reg." -> https://ugcnet.nta.ac.in/images/public-notice-opening-of-the-online-portal-for-submission-of-online-application-form-for-ugc-net-june-2025.pdf. https://exams.nta.ac.in/UGC-NET/ was HTTP 404. https://nta.ac.in/ had no exam notice.

## ncs_gov

- status: verified
- old baseUrl: https://www.ncs.gov.in/
- old listUrls:
  - https://betacloud.ncs.gov.in/job-listing?isGovernmentJob=true
- new baseUrl: https://ncs.gov.in/
- new listUrls:
  - https://ncs.gov.in/job-listing?isGovernmentJob=true
- evidence: https://betacloud.ncs.gov.in/job-listing?isGovernmentJob=true and https://www.ncs.gov.in/ both 301 to https://ncs.gov.in/ (homepage title "NcsNewWebsite", not the government-job list). Homepage link "Jobs in Government sector" -> https://ncs.gov.in/job-listing?isGovernmentJob=true. That URL was HTTPS 200 title "NcsNewWebsite" and the rendered text included "0 Job Posts and 0 Vacancies" under the government-job filter. It is the public government-job list, and it was empty at fetch time.

## Counts

- verified registry updates: 35
- verified, listUrls already correct: 2
- blocked, listUrls unchanged: 42
- sources in this ledger: 79
