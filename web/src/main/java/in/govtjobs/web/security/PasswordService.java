package in.govtjobs.web.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    public static final int SCRYPT_N = 16384;
    public static final int SCRYPT_R = 8;
    public static final int SCRYPT_P = 1;
    public static final int SCRYPT_KEYLEN = 64;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String hash(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] key = SCrypt.generate(password.getBytes(StandardCharsets.UTF_8), salt, SCRYPT_N, SCRYPT_R, SCRYPT_P, SCRYPT_KEYLEN);
        return "scrypt$%d$%d$%d$%s$%s".formatted(SCRYPT_N, SCRYPT_R, SCRYPT_P, HexFormat.of().formatHex(salt), HexFormat.of().formatHex(key));
    }

    public boolean verify(String password, String stored) {
        if (password == null || stored == null) return false;
        String[] parts = stored.split("\\$");
        try {
            if ("scrypt".equals(parts[0]) && parts.length == 6) {
                int n = Integer.parseInt(parts[1]);
                int r = Integer.parseInt(parts[2]);
                int p = Integer.parseInt(parts[3]);
                if (n != SCRYPT_N || r != SCRYPT_R || p != SCRYPT_P) {
                    return false;
                }
                byte[] salt = HexFormat.of().parseHex(parts[4]);
                byte[] expected = HexFormat.of().parseHex(parts[5]);
                if (expected.length != SCRYPT_KEYLEN) {
                    return false;
                }
                byte[] derived = SCrypt.generate(password.getBytes(StandardCharsets.UTF_8), salt, n, r, p, SCRYPT_KEYLEN);
                return MessageDigest.isEqual(derived, expected);
            }
            if ("pbkdf2".equals(parts[0]) && parts.length == 5) {
                int iterations = Integer.parseInt(parts[1]);
                if (iterations < 1 || iterations > 600_000) {
                    return false;
                }
                String digest = parts[2] == null || parts[2].isBlank() ? "SHA256" : parts[2].toUpperCase();
                if ("SHA256".equals(digest) || "SHA-256".equals(digest)) digest = "PBKDF2WithHmacSHA256";
                else if (!digest.startsWith("PBKDF2")) digest = "PBKDF2WithHmac" + digest.replace("-", "");
                byte[] salt = HexFormat.of().parseHex(parts[3]);
                byte[] expected = HexFormat.of().parseHex(parts[4]);
                if (expected.length == 0 || expected.length > 64) {
                    return false;
                }
                PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, expected.length * 8);
                byte[] derived = SecretKeyFactory.getInstance(digest).generateSecret(spec).getEncoded();
                return MessageDigest.isEqual(derived, expected);
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }
}
