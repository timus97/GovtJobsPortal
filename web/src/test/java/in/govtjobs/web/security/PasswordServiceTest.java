package in.govtjobs.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.jupiter.api.Test;

class PasswordServiceTest {

    private final PasswordService passwords = new PasswordService();

    @Test
    void verifyAcceptsCurrentScryptParamsOnly() {
        String hash = passwords.hash("password1234");
        assertThat(passwords.verify("password1234", hash)).isTrue();
        assertThat(passwords.verify("wrong-password", hash)).isFalse();
        String hugeN = hash.replace("scrypt$16384$", "scrypt$1048576$");
        assertThat(passwords.verify("password1234", hugeN)).isFalse();
        String otherR = hash.replace("$8$", "$4$");
        assertThat(passwords.verify("password1234", otherR)).isFalse();
    }

    @Test
    void hashRejectsEmptyAndVerifyRejectsMalformedOrForeignParams() throws Exception {
        assertThatThrownBy(() -> passwords.hash(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> passwords.hash("")).isInstanceOf(IllegalArgumentException.class);
        assertThat(passwords.verify(null, "scrypt$1$1$1$aa$bb")).isFalse();
        assertThat(passwords.verify("password1234", null)).isFalse();
        assertThat(passwords.verify("password1234", "nope")).isFalse();
        assertThat(passwords.verify("password1234", "scrypt$1$1$1$zz")).isFalse();
        assertThat(passwords.verify("password1234", "scrypt$nope$8$1$aa$bb")).isFalse();

        String hash = passwords.hash("password1234");
        String[] parts = hash.split("\\$");
        String shortKey = hash.substring(0, hash.lastIndexOf('$') + 1) + "abcd";
        assertThat(passwords.verify("password1234", shortKey)).isFalse();
        String badP = "scrypt$" + parts[1] + "$" + parts[2] + "$2$" + parts[4] + "$" + parts[5];
        assertThat(passwords.verify("password1234", badP)).isFalse();

        byte[] salt = new byte[] {1, 2, 3, 4};
        byte[] derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec("password1234".toCharArray(), salt, 1_000, 256))
                .getEncoded();
        String saltHex = HexFormat.of().formatHex(salt);
        String keyHex = HexFormat.of().formatHex(derived);
        assertThat(passwords.verify("password1234", "pbkdf2$1000$SHA256$" + saltHex + "$" + keyHex)).isTrue();
        assertThat(passwords.verify("password1234", "pbkdf2$1000$SHA-256$" + saltHex + "$" + keyHex)).isTrue();
        assertThat(passwords.verify("password1234", "pbkdf2$1000$$" + saltHex + "$" + keyHex)).isTrue();
        assertThat(passwords.verify("password1234", "pbkdf2$1000$PBKDF2WithHmacSHA256$" + saltHex + "$" + keyHex))
                .isTrue();
        assertThat(passwords.verify("nope", "pbkdf2$1000$SHA256$" + saltHex + "$" + keyHex)).isFalse();
        assertThat(passwords.verify("password1234", "pbkdf2$0$SHA256$" + saltHex + "$" + keyHex)).isFalse();
        assertThat(passwords.verify("password1234", "pbkdf2$600001$SHA256$" + saltHex + "$" + keyHex)).isFalse();
        assertThat(passwords.verify("password1234", "pbkdf2$1000$SHA256$" + saltHex + "$")).isFalse();
        assertThat(passwords.verify("password1234", "pbkdf2$1000$NO-SUCH$" + saltHex + "$" + keyHex)).isFalse();
        assertThat(passwords.verify("password1234", "pbkdf2$1000$SHA1$" + saltHex + "$abcd")).isFalse();
    }
}
