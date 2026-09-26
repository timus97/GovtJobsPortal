package in.govtjobs.web.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudentFilePathsTest {

    @TempDir
    Path tmp;

    @Test
    void storedPathMustBeStudentItemKindExt() {
        String sid = "54f5ee10-b4fd-4716-a866-e6f84dea2b75";
        String item = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        assertThat(StudentFilePaths.isSafeStoredPath(sid, item, sid + "/" + item + "/admit.pdf")).isTrue();
        assertThat(StudentFilePaths.isSafeStoredPath(sid, item, sid + "/" + item + "/result.png")).isTrue();
        assertThat(StudentFilePaths.isSafeStoredPath(sid, item, sid + "/../" + item + "/admit.pdf")).isFalse();
        assertThat(StudentFilePaths.isSafeStoredPath(sid, item, sid + "/" + item + "/../admit.pdf")).isFalse();
        assertThat(StudentFilePaths.isSafeStoredPath(sid, item, sid + "/" + item + "/notes.txt")).isFalse();
        assertThat(StudentFilePaths.isSafeStoredPath(sid, item, "other/" + item + "/admit.pdf")).isFalse();
    }

    @Test
    void resolveStaysUnderFilesDir() {
        String sid = "54f5ee10-b4fd-4716-a866-e6f84dea2b75";
        String item = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        Path ok = StudentFilePaths.resolveUnder(tmp, sid + "/" + item + "/admit.pdf");
        assertThat(ok).isNotNull();
        assertThat(ok.startsWith(tmp.toAbsolutePath().normalize())).isTrue();
        assertThat(StudentFilePaths.resolveUnder(tmp, sid + "/../etc/passwd")).isNull();
    }
}
