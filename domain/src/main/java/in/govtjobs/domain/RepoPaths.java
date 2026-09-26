package in.govtjobs.domain;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the git repo root so web and collect find {@code data/} whether
 * Maven is launched from the parent or a module directory.
 */
public final class RepoPaths {

    private RepoPaths() {}

    public static Path root() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path cursor = dir; cursor != null; cursor = cursor.getParent()) {
            if (Files.isDirectory(cursor.resolve("data"))
                    && Files.isRegularFile(cursor.resolve("pom.xml"))) {
                return cursor;
            }
        }
        return dir;
    }

    public static Path data() {
        return root().resolve("data");
    }
}
