package in.govtjobs.web.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudentFileAclTest {

    @Test
    void admitFileAndItsFolderStayOwnerOnly(@TempDir Path dir) throws Exception {
        Path folder = dir.resolve("student").resolve("item");
        Files.createDirectories(folder);
        Path file = folder.resolve("admit.pdf");
        Files.writeString(file, "%PDF-1.4");
        assertThat(StudentFilePaths.restrictOwnerReadWrite(folder)).isTrue();
        assertThat(StudentFilePaths.restrictOwnerReadWrite(file)).isTrue();
        assertThat(Files.readString(file)).isEqualTo("%PDF-1.4");
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            String fileAcl = Files.getFileAttributeView(file, AclFileAttributeView.class)
                    .getAcl()
                    .toString()
                    .toLowerCase(Locale.ROOT);
            String folderAcl = Files.getFileAttributeView(folder, AclFileAttributeView.class)
                    .getAcl()
                    .toString()
                    .toLowerCase(Locale.ROOT);
            assertThat(fileAcl).doesNotContain("everyone").doesNotContain("\\users");
            assertThat(folderAcl).doesNotContain("everyone").doesNotContain("\\users");
        }
        assertThat(StudentFilePaths.restrictOwnerReadWrite(dir.resolve("missing.pdf"))).isFalse();
    }
}
