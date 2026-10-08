package in.govtjobs.collect;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;

/** Host and page guards shared by the board collectors. */
public final class SourceRules {

    private SourceRules() {}

    static final String YEAR_STAMPED_PDF =
            "year-stamped calendar PDF is not a durable listUrl; use the CRP updates listing page";
    static final String NCS_EMPTY =
            "Playwright returned no keepable NCS notices; HTTP homepage scrape skipped";
    static final String CHANDIGARH =
            "rrbcdg.gov.in is Chandigarh zonal, not the national RRB apply host";
    static final String CAPTCHA = "blocked or CAPTCHA page; not solved";
    static final String NO_KEEPABLE = "no keepable notices";

    private static final Pattern YEAR_STAMPED = Pattern.compile("ibps_calendar_\\d{4}", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOCKED = Pattern.compile(
            "recaptcha|g-recaptcha|h-captcha|hcaptcha|id=[\"']captcha|class=[\"'][^\"']*captcha|access denied|unusual traffic|please verify you are (a )?human|request unsuccessful|cf-browser-verification|pardon our interruption",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RRB_NOTICE = Pattern.compile(
            "cen|apply|recruit|notif|vacanc|group\\s*d|ntpc|alp|technician|junior engineer|\\bje\\b|advert|opening|employment notice",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RRB_SKIP = Pattern.compile(
            "login|scribe|faq|twitter|facebook|youtube|instagram", Pattern.CASE_INSENSITIVE);
    private static final Pattern IBPS_NOTICE = Pattern.compile(
            "crp|po/?mt|clerk|csa|rrb|specialist|calendar|notif|apply|recruit|vacanc|advert|exam|window notification",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IBPS_SKIP = Pattern.compile(
            "tender|rfp|iso\\s*9001|fraudulent|caution|trademark|career at ibps|gallery",
            Pattern.CASE_INSENSITIVE);

    public static boolean isYearStampedCalendarPdf(String url) {
        return url != null && YEAR_STAMPED.matcher(url).find();
    }

    public static boolean looksBlocked(String html) {
        String head = head(html);
        if (!BLOCKED.matcher(head).find()) {
            return false;
        }
        String visible = Jsoup.parse(html == null ? "" : html).text().replaceAll("\\s+", " ").trim();
        String lower = visible.toLowerCase(Locale.ROOT);
        if (visible.length() > 80
                && lower.matches("(?s).*(examination|recruitment|vacancy|advertisement|notification|calendar).*")) {
            return false;
        }
        return true;
    }

    /** A WAF or cookie interstitial, not a CAPTCHA widget. The browser may load the same URL. */
    public static boolean wafInterstitial(String html) {
        String head = head(html);
        if (!BLOCKED.matcher(head).find()) {
            return false;
        }
        return !Pattern.compile(
                        "recaptcha|g-recaptcha|h-captcha|hcaptcha|please verify you are (a )?human",
                        Pattern.CASE_INSENSITIVE)
                .matcher(head)
                .find();
    }

    private static String head(String html) {
        return html == null ? "" : html.substring(0, Math.min(html.length(), 24_000));
    }

    public static boolean isChandigarhZonalHost(String url) {
        String host = host(url);
        if (host != null) {
            return host.equals("rrbcdg.gov.in") || host.endsWith(".rrbcdg.gov.in");
        }
        return url != null && url.toLowerCase(java.util.Locale.ROOT).contains("rrbcdg.gov.in");
    }

    public static boolean isNationalApplyHost(String url) {
        String host = host(url);
        if (host != null) {
            return host.equals("rrbapply.gov.in") || host.endsWith(".rrbapply.gov.in");
        }
        return url != null && url.toLowerCase(java.util.Locale.ROOT).contains("rrbapply.gov.in");
    }

    public static boolean isRrbNoticeLink(String title, String href) {
        String blob = (title == null ? "" : title) + " " + (href == null ? "" : href);
        if (RRB_SKIP.matcher(blob).find()) {
            return false;
        }
        return RRB_NOTICE.matcher(blob).find();
    }

    public static boolean isIbpsNoticeLink(String title, String href) {
        String blob = (title == null ? "" : title) + " " + (href == null ? "" : href);
        if (IBPS_SKIP.matcher(blob).find()) {
            return false;
        }
        return IBPS_NOTICE.matcher(blob).find();
    }

    private static final Pattern BOARD_NOTICE = Pattern.compile(
            "notif|apply|advert|exam|recruit|vacanc|calendar|corrigend|notice|advt|engagement|opening|circular|active|cgl|chsl|mts|cpo|steno",
            Pattern.CASE_INSENSITIVE);

    public static boolean isBoardNoticeLink(String title, String href) {
        String blob = (title == null ? "" : title) + " " + (href == null ? "" : href);
        return BOARD_NOTICE.matcher(blob).find();
    }

    public static String host(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(url).getHost();
            return host == null ? null : host.toLowerCase(java.util.Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
