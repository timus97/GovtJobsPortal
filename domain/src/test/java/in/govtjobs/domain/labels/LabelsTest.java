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
    }
}
