package in.govtjobs.collect;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Date hints from notice text. Same labels as {@code scripts/collect/lib/dates.js}. */
public final class NoticeDates {

    private NoticeDates() {}

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("jan", 1),
            Map.entry("january", 1),
            Map.entry("feb", 2),
            Map.entry("february", 2),
            Map.entry("mar", 3),
            Map.entry("march", 3),
            Map.entry("apr", 4),
            Map.entry("april", 4),
            Map.entry("may", 5),
            Map.entry("jun", 6),
            Map.entry("june", 6),
            Map.entry("jul", 7),
            Map.entry("july", 7),
            Map.entry("aug", 8),
            Map.entry("august", 8),
            Map.entry("sep", 9),
            Map.entry("sept", 9),
            Map.entry("september", 9),
            Map.entry("oct", 10),
            Map.entry("october", 10),
            Map.entry("nov", 11),
            Map.entry("november", 11),
            Map.entry("dec", 12),
            Map.entry("december", 12));

    private static final Pattern ISO = Pattern.compile("\\b(20\\d{2})[-/](\\d{1,2})[-/](\\d{1,2})\\b");
    private static final Pattern DMY = Pattern.compile("\\b(\\d{1,2})[-/.](\\d{1,2})[-/.](20\\d{2})\\b");
    private static final Pattern DAY_MONTH = Pattern.compile(
            "\\b(\\d{1,2})(?:st|nd|rd|th)?[\\s,\\-]+([A-Za-z]{3,9})[\\s,\\-]+(20\\d{2})\\b");
    private static final Pattern MONTH_DAY = Pattern.compile(
            "\\b([A-Za-z]{3,9})\\s+(\\d{1,2})(?:st|nd|rd|th)?[,\\s]+(20\\d{2})\\b");
    private static final Pattern LAST_DATE = Pattern.compile(
            "(?:last\\s*date|closing\\s*date|apply\\s*by|last\\s*date\\s*to\\s*apply|extended\\s+(?:till|upto|up\\s*to)|walk[\\s-]?in\\s*date|date\\s*of\\s*walk)[:\\s-]*([^\\n|;]{6,90})",
            Pattern.CASE_INSENSITIVE);

    public static String parseDateFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher iso = ISO.matcher(text);
        if (iso.find()) {
            return iso(iso.group(1), iso.group(2), iso.group(3));
        }
        Matcher dmy = DMY.matcher(text);
        if (dmy.find()) {
            return iso(dmy.group(3), dmy.group(2), dmy.group(1));
        }
        Matcher dayMonth = DAY_MONTH.matcher(text);
        if (dayMonth.find()) {
            Integer month = MONTHS.get(dayMonth.group(2).toLowerCase(Locale.ROOT));
            if (month != null) {
                return iso(dayMonth.group(3), String.valueOf(month), dayMonth.group(1));
            }
        }
        Matcher monthDay = MONTH_DAY.matcher(text);
        if (monthDay.find()) {
            Integer month = MONTHS.get(monthDay.group(1).toLowerCase(Locale.ROOT));
            if (month != null) {
                return iso(monthDay.group(3), String.valueOf(month), monthDay.group(2));
            }
        }
        return null;
    }

    public static String findLastDateHint(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher labeled = LAST_DATE.matcher(text);
        if (!labeled.find()) {
            return null;
        }
        return parseDateFromText(labeled.group(1));
    }

    /** The last real date in a table row. A bare notification year in the title is not enough. */
    public static String lastDateIn(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String found = null;
        Matcher iso = ISO.matcher(text);
        while (iso.find()) {
            String parsed = iso(iso.group(1), iso.group(2), iso.group(3));
            if (parsed != null) {
                found = parsed;
            }
        }
        Matcher dmy = DMY.matcher(text);
        while (dmy.find()) {
            String parsed = iso(dmy.group(3), dmy.group(2), dmy.group(1));
            if (parsed != null) {
                found = parsed;
            }
        }
        Matcher dayMonth = DAY_MONTH.matcher(text);
        while (dayMonth.find()) {
            Integer month = MONTHS.get(dayMonth.group(2).toLowerCase(Locale.ROOT));
            if (month != null) {
                String parsed = iso(dayMonth.group(3), String.valueOf(month), dayMonth.group(1));
                if (parsed != null) {
                    found = parsed;
                }
            }
        }
        Matcher monthDay = MONTH_DAY.matcher(text);
        while (monthDay.find()) {
            Integer month = MONTHS.get(monthDay.group(1).toLowerCase(Locale.ROOT));
            if (month != null) {
                String parsed = iso(monthDay.group(3), String.valueOf(month), monthDay.group(2));
                if (parsed != null) {
                    found = parsed;
                }
            }
        }
        return found;
    }

    private static String iso(String year, String month, String day) {
        try {
            return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day))
                    .toString();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
