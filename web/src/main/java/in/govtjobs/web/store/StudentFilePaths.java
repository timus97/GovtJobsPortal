package in.govtjobs.web.store;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Package-visible path rules for student admit/result files. */
final class StudentFilePaths {

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

    static void restrictOwnerReadWrite(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            try {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
            } catch (Exception ignored) {
                // best-effort
            }
            return;
        }
        try {
            AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (view == null) {
                return;
            }
            UserPrincipal owner = view.getOwner();
            AclEntry entry = AclEntry.newBuilder()
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
            view.setAcl(List.of(entry));
        } catch (Exception ignored) {
            // Windows ACL is best-effort
        }
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
