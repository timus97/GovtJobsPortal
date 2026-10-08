package in.govtjobs.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class JobLinkQualityTest {

    @Test
    void rejectsNavChromeHomepagesIndexesAndLowScoreScrapes() {
        assertThat(JobLinkQuality.isGarbageJob(job("Careers", "https://ssc.gov.in/advt/1", "process-v1")).reasons())
                .containsExactly("nav_title");
        assertThat(JobLinkQuality.isGarbageJob(job("Skip to main content", "https://ssc.gov.in/advt/1", "process-v1"))
                        .reasons())
                .containsExactly("nav_title");
        assertThat(JobLinkQuality.isGarbageJob(job("Switch हिंदी", "https://ssc.gov.in/advt/1", "process-v1")).reasons())
                .containsExactly("nav_title");
        assertThat(JobLinkQuality.isGarbageJob(job("Please read the syllabus", "https://ssc.gov.in/advt/1", "process-v1"))
                        .garbage())
                .isTrue();
        assertThat(JobLinkQuality.isGarbageJob(
                        job("Please read the syllabus for the post", "https://ssc.gov.in/advt/syllabus", "process-v1"))
                        .garbage())
                .isFalse();

        assertThat(JobLinkQuality.isGarbageJob(job("Junior engineer advt no 7", "https://coalindia.in/", "process-v1"))
                        .reasons())
                .containsExactly("homepage");
        assertThat(JobLinkQuality.isGarbageJob(job("Hello there", "https://org.gov.in/careers", "process-v1")).reasons())
                .containsExactly("index_only");
        assertThat(JobLinkQuality.isGarbageJob(job("Annual job fair", "https://ncs.gov.in/advt/fair-2026", "process-v1"))
                        .reasons())
                .containsExactly("employer_action");
        assertThat(JobLinkQuality.isGarbageJob(job("NIT for bridge", "https://org.gov.in/files/bridge-work", "process-v1"))
                        .reasons())
                .containsExactly("tender");
        assertThat(JobLinkQuality.isGarbageJob(
                        job("Civil package advt no 30", "https://org.gov.in/tenders/civil-package", "process-v1"))
                        .reasons())
                .containsExactly("garbage_href");

        JobLinkQuality.Garbage scrape = JobLinkQuality.isGarbageJob(
                job("Weekly briefing note", "https://org.gov.in/files/briefing-note", "pdf-scrape"));
        assertThat(scrape.garbage()).isTrue();
        assertThat(JobLinkQuality.isGarbageJob(
                        job("Weekly briefing note", "https://org.gov.in/files/briefing-note", "process-v1"))
                        .garbage())
                .isFalse();
        assertThat(JobLinkQuality.isGarbageJob(job("Ab", "https://org.gov.in/files/ab", "process-v1")).garbage())
                .isTrue();
        assertThat(JobLinkQuality.isGarbageJob(
                        job("View Advertisement", "https://uppsc.up.nic.in/advt/1", "scrape-v1"))
                        .garbage())
                .isTrue();
        assertThat(JobLinkQuality.isGarbageJob(
                        job("Tentative Vacancy", "https://ssc.gov.in/for-candidates/tentative-vacancy", "calendar-v1"))
                        .garbage())
                .isTrue();
        assertThat(JobLinkQuality.isGarbageJob(
                        job("DECLARATION OF PROVISIONAL RESULT FOR RECRUITMENT", "https://esic.gov.in/a.pdf", "scrape-v1"))
                        .garbage())
                .isTrue();
        assertThat(JobLinkQuality.isGarbageJob(
                        job("RESULT FOR RECRUITMENT OF FACULTY", "https://esic.gov.in/b.pdf", "scrape-v1"))
                        .garbage())
                .isTrue();
        assertThat(JobLinkQuality.isGarbageJob(
                        job("Download Interview Call Letter for Advt No 1", "https://sail.co.in/a.pdf", "scrape-v1"))
                        .garbage())
                .isTrue();
        assertThat(JobLinkQuality.isGarbageJob(
                        job("Appointment Letter Regarding Direct Recruitment", "https://rpsc.rajasthan.gov.in/a", "scrape-v1"))
                        .garbage())
                .isTrue();
        assertThat(JobLinkQuality.isGarbageJob(
                        job("Regarding interview Of Contractual Part Time Specialist", "https://esic.gov.in/c.pdf", "scrape-v1"))
                        .garbage())
                .isTrue();
        assertThat(JobLinkQuality.isGarbageJob(
                        job(
                                "Walk in Interview for recruitment of Specialists",
                                "https://esic.gov.in/walk-in.pdf",
                                "scrape-v1"))
                        .garbage())
                .isFalse();
    }

    @Test
    void keepsARecruitmentPdfAndScoresIndexesThatNameANotice() {
        JobLinkQuality.Garbage kept = JobLinkQuality.isGarbageJob(
                job("Recruitment advertisement for clerks", "https://org.gov.in/uploads/notice.pdf", "process-v1"));
        assertThat(kept.garbage()).isFalse();
        assertThat(kept.reasons()).contains("document", "notice_title");

        JobLinkQuality.Score named = JobLinkQuality.scoreJobLink(
                Map.of("title", "Recruitment of clerks", "href", "https://org.gov.in/careers"));
        assertThat(named.score()).isEqualTo(3);
        assertThat(named.reasons()).containsExactly("notice_title");

        JobLinkQuality.Score index = JobLinkQuality.scoreJobLink(
                Map.of("title", "Hello there", "href", "https://org.gov.in/careers"));
        assertThat(index.score()).isNegative();
        assertThat(index.reasons()).containsExactly("index_only");

        assertThat(JobLinkQuality.scoreJobLink(Map.of("title", "Careers", "href", "notaurl")).reasons())
                .containsExactly("bad_url");
        assertThat(JobLinkQuality.scoreJobLink(Map.of("title", "Open", "href", "javascript:void(0)")).reasons())
                .containsExactly("bad_url");
        assertThat(JobLinkQuality.scoreJobLink(
                        Map.of("title", "Open roles", "href", "https://org.gov.in/login")).reasons())
                .containsExactly("garbage_href");
        assertThat(JobLinkQuality.scoreJobLink(Map.of("title", "Real advt no 3", "href", "https://ssc.gov.in/")).reasons())
                .containsExactly("homepage");
    }

    private static Map<String, Object> job(String title, String url, String version) {
        return Map.of(
                "title", title,
                "officialUrl", url,
                "collectorVersion", version);
    }
}
