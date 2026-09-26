package in.govtjobs.web.ops;

import in.govtjobs.web.config.GovtJobsProperties;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class OfficialUrlPolicy {

    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(?:\\.\\d{1,3}){3}");
    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "metadata",
            "metadata.google.internal",
            "metadata.goog",
            "instance-data");

    private final GovtJobsProperties props;

    public OfficialUrlPolicy(GovtJobsProperties props) {
        this.props = props;
    }

    public URI requireAllowed(String raw) {
        URI uri = requireHttps(raw);
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host must be a .gov.in or .nic.in official site");
        }
        host = host.toLowerCase(Locale.ROOT);
        if (isLiteralIp(host) || BLOCKED_HOSTS.contains(host)) {
            throw new IllegalArgumentException("Host must be a .gov.in or .nic.in official site");
        }
        if (!isAllowedHost(host)) {
            throw new IllegalArgumentException("Host must be a .gov.in or .nic.in official site");
        }
        if (resolvesToBlockedAddress(host)) {
            throw new IllegalArgumentException("Host must be a .gov.in or .nic.in official site");
        }
        return uri;
    }

    public URI requireHttps(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty() || !trimmed.regionMatches(true, 0, "https://", 0, 8)) {
            throw new IllegalArgumentException("Only https official URLs are accepted");
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Only https official URLs are accepted");
        }
        if (uri.getScheme() == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only https official URLs are accepted");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Only https official URLs are accepted");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Only https official URLs are accepted");
        }
        return uri;
    }

    public boolean isAllowedHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (isLiteralIp(h) || BLOCKED_HOSTS.contains(h)) {
            return false;
        }
        List<String> suffixes = props.getOps().getAllowedSuffixes();
        if (suffixes != null) {
            for (String suffix : suffixes) {
                if (suffix == null || suffix.isBlank()) continue;
                String s = suffix.toLowerCase(Locale.ROOT);
                if (!s.startsWith(".")) {
                    s = "." + s;
                }
                if (h.endsWith(s) || h.equals(s.substring(1))) {
                    return true;
                }
            }
        }
        List<String> extras = props.getOps().getExtraHosts();
        if (extras != null) {
            for (String extra : extras) {
                if (matchesExtra(h, extra)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isHttps(String raw) {
        return raw != null && raw.trim().regionMatches(true, 0, "https://", 0, 8);
    }

    private static boolean matchesExtra(String host, String extra) {
        if (extra == null || extra.isBlank()) {
            return false;
        }
        String e = extra.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        return host.equals(e) || host.equals("www." + e) || host.endsWith("." + e);
    }

    static boolean isLiteralIp(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        if (host.indexOf(':') >= 0) {
            return true;
        }
        return IPV4.matcher(host).matches();
    }

    static boolean isBlockedAddress(InetAddress addr) {
        if (addr == null) {
            return true;
        }
        if (addr.isAnyLocalAddress()
                || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }
        byte[] raw = addr.getAddress();
        if (raw.length == 4) {
            return isCarrierGradeNat(raw);
        }
        if (raw.length == 16) {
            if ((raw[0] & 0xfe) == 0xfc) {
                return true;
            }
            if (isIpv4Mapped(raw)) {
                return isBlockedAddress(v4FromMapped(raw));
            }
        }
        return false;
    }

    private static boolean isCarrierGradeNat(byte[] raw) {
        int a = raw[0] & 0xff;
        int b = raw[1] & 0xff;
        return a == 100 && b >= 64 && b <= 127;
    }

    private static boolean isIpv4Mapped(byte[] raw) {
        for (int i = 0; i < 10; i++) {
            if (raw[i] != 0) {
                return false;
            }
        }
        return raw[10] == (byte) 0xff && raw[11] == (byte) 0xff;
    }

    private static InetAddress v4FromMapped(byte[] raw) {
        try {
            return InetAddress.getByAddress(new byte[] {raw[12], raw[13], raw[14], raw[15]});
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static boolean resolvesToBlockedAddress(String host) {
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (isBlockedAddress(addr)) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            return true;
        }
    }
}
