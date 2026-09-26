package in.govtjobs.web.security;

import static org.assertj.core.api.Assertions.assertThat;

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
}
