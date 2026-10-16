package in.govtjobs.web.store;

import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Package-visible path rules for student admit/result files. */
final class StudentFilePaths {

    private static final Logger log = LoggerFactory.getLogger(StudentFilePaths.class);
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern FILE_NAME = Pattern.compile("(admit|result)\\.(pdf|jpg|png)");

    private StudentFilePaths() {}

    static boolean isSafeId(String id) {
        return id != null && SAFE_ID.matcher(id).matches();
    }

    static boolean isSafeStoredPath(String studentId, String itemId, String storedPath) {
        if (!isSafeId(studentId) || !isSafeId(itemId) || storedPath == null || storedPath.isBlank()) {
            return false;
        }
        String norm = storedPath.replace('\\', '/');
        if (norm.startsWith("/") || norm.contains("..") || norm.contains("//")) {
            return false;
        }
        String[] parts = norm.split("/");
        if (parts.length != 3) {
            return false;
        }
        return studentId.equals(parts[0]) && itemId.equals(parts[1]) && FILE_NAME.matcher(parts[2]).matches();
    }

    static Path resolveUnder(Path root, String storedPath) {
        if (root == null || storedPath == null) {
            return null;
        }
        String rel = storedPath.replace('\\', '/');
        if (rel.startsWith("/") || rel.contains("..")) {
            return null;
        }
        Path base = root.toAbsolutePath().normalize();
        Path resolved = base.resolve(rel).normalize();
        if (!resolved.startsWith(base)) {
            return null;
        }
        return resolved;
    }

    static Path itemDir(Path filesDir, String studentId, String itemId) {
        if (!isSafeId(studentId) || !isSafeId(itemId) || filesDir == null) {
            return null;
        }
        return resolveUnder(filesDir, studentId + "/" + itemId);
    }

    /**
     * Owner-only mode. A failure is logged without the file bytes or a student email.
     * Callers must not keep the upload unless this returns true.
     */
    static boolean restrictOwnerReadWrite(Path path) {
        if (path == null || !Files.exists(path)) {
            return false;
        }
        try {
            boolean directory = Files.isDirectory(path);
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                Set<PosixFilePermission> mode =
                        PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------");
                Files.setPosixFilePermissions(path, mode);
                return Files.getPosixFilePermissions(path).equals(mode);
            }
            if (isWindows()) {
                return windowsOwnerOnly(path, directory);
            }
            AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (view == null) {
                log.warn("desk.file_acl_unavailable");
                return false;
            }
            UserPrincipal owner = currentUser(path);
            if (owner == null) {
                return false;
            }
            view.setAcl(List.of(allowReadWrite(owner)));
            return true;
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("desk.file_acl_failed");
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }

    /** Drop inherited ACEs. The account running the host keeps read, write, and delete. */
    private static boolean windowsOwnerOnly(Path path, boolean directory) throws Exception {
        UserPrincipal user = currentUser(path);
        if (user == null) {
            return false;
        }
        String rights = directory ? "(OI)(CI)(R,W,D,DC)" : "(R,W,D)";
        Process process = new ProcessBuilder(
                        icaclsCommand(),
                        path.toAbsolutePath().toString(),
                        "/inheritance:r",
                        "/grant:r",
                        user.getName() + ":" + rights)
                .redirectErrorStream(true)
                .start();
        try (InputStream output = process.getInputStream()) {
            output.readAllBytes();
        }
        if (process.waitFor() != 0) {
            log.warn("desk.file_acl_failed");
            return false;
        }
        AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (view == null) {
            return false;
        }
        String acl = view.getAcl().toString().toLowerCase(Locale.ROOT);
        return !acl.contains("everyone") && !acl.contains("\\users");
    }

    private static String icaclsCommand() {
        String root = System.getenv("SystemRoot");
        if (root != null && !root.isBlank()) {
            Path exe = Path.of(root, "System32", "icacls.exe");
            if (Files.isRegularFile(exe)) {
                return exe.toString();
            }
        }
        return "icacls";
    }

    private static UserPrincipal currentUser(Path path) throws Exception {
        String name = System.getProperty("user.name");
        if (name == null || name.isBlank()) {
            return null;
        }
        return path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(name);
    }

    private static AclEntry allowReadWrite(UserPrincipal owner) {
        return AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.of(
                        AclEntryPermission.READ_DATA,
                        AclEntryPermission.WRITE_DATA,
                        AclEntryPermission.APPEND_DATA,
                        AclEntryPermission.READ_ATTRIBUTES,
                        AclEntryPermission.WRITE_ATTRIBUTES,
                        AclEntryPermission.READ_NAMED_ATTRS,
                        AclEntryPermission.WRITE_NAMED_ATTRS,
                        AclEntryPermission.DELETE,
                        AclEntryPermission.READ_ACL,
                        AclEntryPermission.SYNCHRONIZE))
                .build();
    }

    static String storedPath(String studentId, String itemId, String kind, String ext) {
        String e = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        if (!isSafeId(studentId) || !isSafeId(itemId)) {
            return null;
        }
        if (!("admit".equals(kind) || "result".equals(kind))) {
            return null;
        }
        if (!("pdf".equals(e) || "jpg".equals(e) || "png".equals(e))) {
            return null;
        }
        return studentId + "/" + itemId + "/" + kind + "." + e;
    }
}
