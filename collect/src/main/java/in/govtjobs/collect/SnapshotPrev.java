package in.govtjobs.collect;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Copy jobs.json to jobs.prev.json after a daily alert attempt that did not fail. */
final class SnapshotPrev {

    private SnapshotPrev() {}

    static Path copy(Path root) throws IOException {
        Path jobs = root.resolve("data").resolve("processed").resolve("jobs.json");
        Path prev = root.resolve("data").resolve("processed").resolve("jobs.prev.json");
        if (!Files.isRegularFile(jobs)) {
            throw new IOException("Missing data/processed/jobs.json — run process first");
        }
        Files.copy(jobs, prev, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Snapshot: jobs.json → jobs.prev.json (" + Files.size(prev) + " bytes)");
        return prev;
    }
}
