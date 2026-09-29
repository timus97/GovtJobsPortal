package in.govtjobs.domain.labels;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LabelsTest {

    @Test
    void labelMapsCoverCoreCodes() {
        assertThat(Labels.ORG_TYPE_LABELS.get("psu")).isEqualTo("PSU");
        assertThat(Labels.SELECTION_LABELS.get("cbt")).isEqualTo("Computer-based test");
        assertThat(Labels.QUAL_LABELS.get("pg")).isEqualTo("Postgraduate");
        assertThat(Labels.STATUS_LABELS.get("closing_soon")).isEqualTo("Closing Soon");
        assertThat(Labels.formatDate(null)).isEqualTo("—");
        assertThat(Labels.formatDate("")).isEqualTo("—");
        assertThat(Labels.formatDate("2026-09-26")).contains("2026");
        assertThat(Labels.formatDate("not-a-date")).isEqualTo("not-a-date");
        assertThat(Labels.formatDateTime(null)).isEqualTo("—");
        assertThat(Labels.formatDateTime("2026-09-26T10:15:00Z")).contains("2026");
        assertThat(Labels.formatDateTime("nope")).isEqualTo("nope");
    }
}
