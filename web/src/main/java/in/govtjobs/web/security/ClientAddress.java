package in.govtjobs.web.security;

/**
 * Socket addresses for auth decisions. Only numeric IP literals are accepted.
 * Hostnames are not resolved, and this class does not read forwarded headers.
 */
public final class ClientAddress {

    private ClientAddress() {}

    public static boolean isLoopback(String remote) {
        byte[] raw = literalBytes(remote);
        if (raw == null) {
            return false;
        }
        if (raw.length == 4) {
            return (raw[0] & 0xff) == 127;
        }
        if (raw.length != 16) {
            return false;
        }
        if (isIpv4Mapped(raw)) {
            return (raw[12] & 0xff) == 127;
        }
        for (int i = 0; i < 15; i++) {
            if (raw[i] != 0) {
                return false;
            }
        }
        return raw[15] == 1;
    }

    private static boolean isIpv4Mapped(byte[] raw) {
        for (int i = 0; i < 10; i++) {
            if (raw[i] != 0) {
                return false;
            }
        }
        return raw[10] == (byte) 0xff && raw[11] == (byte) 0xff;
    }

    private static byte[] literalBytes(String remote) {
        if (remote == null) {
            return null;
        }
        String host = remote.trim();
        if (host.startsWith("[") && host.endsWith("]") && host.length() > 2) {
            host = host.substring(1, host.length() - 1).trim();
        }
        int zone = host.indexOf('%');
        if (zone >= 0) {
            host = host.substring(0, zone);
        }
        if (host.isEmpty() || host.indexOf(' ') >= 0) {
            return null;
        }
        if (host.indexOf(':') >= 0) {
            return parseIpv6(host);
        }
        return parseIpv4(host);
    }

    private static byte[] parseIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            Integer octet = decimalOctet(parts[i]);
            if (octet == null) {
                return null;
            }
            out[i] = octet.byteValue();
        }
        return out;
    }

    private static Integer decimalOctet(String part) {
        if (part.isEmpty() || part.length() > 3) {
            return null;
        }
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        int value = Integer.parseInt(part);
        if (value > 255) {
            return null;
        }
        return value;
    }

    private static byte[] parseIpv6(String host) {
        String v4 = null;
        int dot = host.indexOf('.');
        if (dot >= 0) {
            int colon = host.lastIndexOf(':');
            if (colon < 0 || dot < colon) {
                return null;
            }
            v4 = host.substring(colon + 1);
            host = host.substring(0, colon);
        }
        String[] sides = host.split("::", -1);
        if (sides.length > 2) {
            return null;
        }
        int[] left = hexGroups(sides[0]);
        int[] right = sides.length == 2 ? hexGroups(sides[1]) : new int[0];
        if (left == null || right == null) {
            return null;
        }
        byte[] v4bytes = null;
        int v4Groups = 0;
        if (v4 != null) {
            v4bytes = parseIpv4(v4);
            if (v4bytes == null) {
                return null;
            }
            v4Groups = 2;
        }
        int explicit = left.length + right.length + v4Groups;
        if (sides.length == 1) {
            if (explicit != 8) {
                return null;
            }
        } else if (explicit > 7) {
            return null;
        }
        byte[] out = new byte[16];
        int group = 0;
        for (int value : left) {
            writeGroup(out, group++, value);
        }
        if (sides.length == 2) {
            group += 8 - explicit;
        }
        for (int value : right) {
            writeGroup(out, group++, value);
        }
        if (v4bytes != null) {
            System.arraycopy(v4bytes, 0, out, group * 2, 4);
        }
        return out;
    }

    private static int[] hexGroups(String side) {
        if (side.isEmpty()) {
            return new int[0];
        }
        String[] parts = side.split(":", -1);
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 4 || !isHex(parts[i])) {
                return null;
            }
            out[i] = Integer.parseInt(parts[i], 16);
        }
        return out;
    }

    private static boolean isHex(String part) {
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lower = c >= 'a' && c <= 'f';
            boolean upper = c >= 'A' && c <= 'F';
            if (!digit && !lower && !upper) {
                return false;
            }
        }
        return true;
    }

    private static void writeGroup(byte[] out, int group, int value) {
        out[group * 2] = (byte) (value >> 8);
        out[group * 2 + 1] = (byte) value;
    }
}
