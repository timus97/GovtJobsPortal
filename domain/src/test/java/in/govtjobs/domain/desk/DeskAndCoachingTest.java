package in.govtjobs.domain.desk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeskAndCoachingTest {

    private static final List<Map<String, Object>> EQUAL_TOPICS = List.of(
            topic("a", "A", 1),
            topic("b", "B", 1),
            topic("c", "C", 1),
            topic("d", "D", 1));

    private static Map<String, Object> topic(String id, String title, double weight) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("id", id);
        t.put("title", title);
        t.put("weight", weight);
        return t;
    }

    @Test
    void studyPlan_evenSplitInclusiveDays() {
        LocalDate today = LocalDate.of(2026, 1, 1);
        String exam = "2026-01-08";
        assertThat(DeskGuidance.daysLeft(exam, today)).isEqualTo(7);

        Map<String, Object> split = StudyPlan.buildPlan(EQUAL_TOPICS, exam, today);
        assertThat(split.get("unofficial")).isEqualTo(true);
        assertThat(split.get("days")).isEqualTo(8);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topics = (List<Map<String, Object>>) split.get("topics");
        assertThat(topics).hasSize(4);
        for (Map<String, Object> topic : topics) {
            assertThat(topic.get("days")).isEqualTo(2);
            assertThat(topic.get("startDate")).isNotNull();
            assertThat(topic.get("endDate")).isNotNull();
        }
        assertThat(topics.get(0).get("startDate")).isEqualTo("2026-01-01");
        assertThat(topics.get(0).get("endDate")).isEqualTo("2026-01-02");
        assertThat(topics.get(1).get("startDate")).isEqualTo("2026-01-03");
        assertThat(topics.get(1).get("endDate")).isEqualTo("2026-01-04");
        assertThat(topics.get(2).get("startDate")).isEqualTo("2026-01-05");
        assertThat(topics.get(2).get("endDate")).isEqualTo("2026-01-06");
        assertThat(topics.get(3).get("startDate")).isEqualTo("2026-01-07");
        assertThat(topics.get(3).get("endDate")).isEqualTo(exam);

        Map<String, Object> noDate = StudyPlan.buildPlan(EQUAL_TOPICS, null, today);
        assertThat(noDate.get("note")).isEqualTo(StudyPlan.ADD_DATE_NOTE);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nullTopics = (List<Map<String, Object>>) noDate.get("topics");
        for (Map<String, Object> topic : nullTopics) {
            assertThat(topic.get("startDate")).isNull();
            assertThat(topic.get("endDate")).isNull();
            assertThat(topic.get("days")).isNull();
        }

        Map<String, Object> todayExam = StudyPlan.buildPlan(EQUAL_TOPICS, "2026-01-01", today);
        assertThat(todayExam.get("note")).isEqualTo(StudyPlan.ADD_DATE_NOTE);
    }

    @Test
    void mockPublicBank_stripsAnswers() {
        Map<String, Object> tiny = new LinkedHashMap<>();
        tiny.put("seriesId", "tiny");
        tiny.put("unofficial", true);
        tiny.put("durationMin", 5);
        tiny.put(
                "questions",
                List.of(
                        Map.of(
                                "id",
                                "q1",
                                "stem",
                                "1+1",
                                "choices",
                                List.of("1", "2", "3", "4"),
                                "answerIndex",
                                1,
                                "explain",
                                "two"),
                        Map.of(
                                "id",
                                "q2",
                                "stem",
                                "2+2",
                                "choices",
                                List.of("1", "2", "3", "4"),
                                "answerIndex",
                                3,
                                "explain",
                                "four")));

        Map<String, Object> pub = MockScore.publicBank(tiny);
        assertThat(pub.get("unofficial")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) pub.get("questions");
        assertThat(questions).isNotEmpty();
        for (Map<String, Object> q : questions) {
            assertThat(q).doesNotContainKeys("answerIndex", "explain");
            assertThat(q).containsKeys("id", "stem", "choices");
        }

        Map<String, Object> perfect = MockScore.scoreAttempt(tiny, Map.of("q1", 1, "q2", 3));
        assertThat(perfect.get("score")).isEqualTo(2);
        assertThat(perfect.get("total")).isEqualTo(2);
        assertThat(perfect.get("correctIds")).isEqualTo(List.of("q1", "q2"));

        Map<String, Object> none = MockScore.scoreAttempt(tiny, Map.of("q1", 0, "q2", 0));
        assertThat(none.get("score")).isEqualTo(0);
        assertThat(MockScore.scoreAttempt(tiny, Map.of()).get("score")).isEqualTo(0);
    }

    @Test
    void deskGuidance_daysLeftAndNextStep() {
        LocalDate today = LocalDate.of(2026, 1, 1);
        assertThat(DeskGuidance.daysLeft("2026-01-08", today)).isEqualTo(7);
        assertThat(DeskGuidance.daysLeftLabel(7))
                .containsEntry("number", "7")
                .containsEntry("caption", "days left");
        assertThat(DeskGuidance.daysLeftLabel(null))
                .containsEntry("number", "—")
                .containsEntry("caption", "Add exam date");

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("kind", "series");
        item.put("status", "watching");
        item.put("applyOpen", true);
        item.put("examDate", "2026-01-08");
        Map<String, Object> decorated = DeskGuidance.decorateItem(item);
        assertThat(decorated.get("nextStep"))
                .isEqualTo("Apply on the official site, then mark Applied.");
        assertThat(decorated.get("daysLeft")).isNotNull();
        assertThat(decorated.get("kindLabel")).isEqualTo("Calendar");
    }
}
