package in.govtjobs.domain.match;

import java.util.List;

/** Thrown when a match profile is incomplete (HTTP 400 equivalent). */
public final class MatchException extends RuntimeException {

    private final int statusCode;
    private final List<String> errors;

    public MatchException(List<String> errors) {
        super(errors == null || errors.isEmpty() ? "invalid profile" : String.join("; ", errors));
        this.statusCode = 400;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public MatchException(String message, List<String> errors) {
        super(message == null || message.isBlank()
                ? (errors == null || errors.isEmpty() ? "invalid profile" : String.join("; ", errors))
                : message);
        this.statusCode = 400;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public List<String> getErrors() {
        return errors;
    }
}
