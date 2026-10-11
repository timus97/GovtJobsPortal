package in.govtjobs.web.security;

/**
 * Local paths a sign-in may return to. Anything else goes to the default page.
 */
public final class ReturnTo {

    private ReturnTo() {}

    public static String sanitize(String next) {
        if (next == null) {
            return null;
        }
        String value = next.trim();
        if (value.isEmpty()
                || !value.startsWith("/")
                || value.startsWith("//")
                || value.contains("\\")
                || value.contains("://")
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0
                || value.contains("@")) {
            return null;
        }
        if ("/prepare".equals(value) || value.startsWith("/prepare?")) {
            return value;
        }
        if (value.startsWith("/jobs/")) {
            String id = value.substring("/jobs/".length());
            if (!id.isEmpty() && id.indexOf('/') < 0 && id.indexOf('?') < 0 && id.indexOf('#') < 0
                    && id.matches("[A-Za-z0-9._-]+")) {
                return value;
            }
        }
        return null;
    }
}
