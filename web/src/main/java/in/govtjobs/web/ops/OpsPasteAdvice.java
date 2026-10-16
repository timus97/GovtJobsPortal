package in.govtjobs.web.ops;

import java.util.Map;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Short paste-state words for the ops pages. The stored code is not shown. */
@ControllerAdvice(assignableTypes = OpsController.class)
class OpsPasteAdvice {

    @ModelAttribute("pasteStateLabel")
    public Map<String, String> pasteStateLabel() {
        return Map.of(
                "published_local", PastePublishFacts.tableLabel("published_local"),
                "published", PastePublishFacts.tableLabel("published"),
                "unpublished", PastePublishFacts.tableLabel("unpublished"),
                "rejected", PastePublishFacts.tableLabel("rejected"),
                "needs_review", PastePublishFacts.tableLabel("needs_review"));
    }
}
