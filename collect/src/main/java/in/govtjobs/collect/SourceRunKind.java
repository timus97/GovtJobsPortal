package in.govtjobs.collect;

import java.util.List;
import java.util.Locale;

/** How one source fetch finished. {@link #ok()} is true only when the run kept notices. */
public enum SourceRunKind {
    COMPLETE,
    PARTIAL,
    EMPTY,
    FAILED,
    NOT_FINISHED;

    public boolean producedNotices() {
        return this == COMPLETE || this == PARTIAL;
    }

    /** Report and desk label. {@code NOT_FINISHED} is {@code not_finished}. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Notices with no errors are complete. Notices with errors are partial. No notices is empty or failed. */
    public static SourceRunKind from(List<?> records, List<?> errors) {
        boolean notices = records != null && !records.isEmpty();
        boolean problems = errors != null && !errors.isEmpty();
        if (notices && problems) {
            return PARTIAL;
        }
        if (notices) {
            return COMPLETE;
        }
        if (problems) {
            return FAILED;
        }
        return EMPTY;
    }
}
