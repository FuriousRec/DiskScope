import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StorageClassifier {

    // ANCHOR: Check Path
    private static boolean isInsideOrSame(
            File item,
            String basePath) {

        if (basePath == null || basePath.isBlank()) {
            return false;
        }

        Path itemPath = item.toPath()
                .toAbsolutePath()
                .normalize();

        Path protectedPath = Paths.get(basePath)
                .toAbsolutePath()
                .normalize();

        return itemPath.startsWith(protectedPath);
    }

    private static boolean hasPathSegment(
            File item,
            String expectedSegment) {

        Path path = item.toPath()
                .toAbsolutePath()
                .normalize();

        for (Path segment : path) {
            if (segment.toString()
                    .equalsIgnoreCase(expectedSegment)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasPathSegments(
            File item,
            String... expectedSegments) {

        Path path = item.toPath()
                .toAbsolutePath()
                .normalize();

        for (int start = 0;
                start <= path.getNameCount()
                        - expectedSegments.length;
                start++) {

            boolean matches = true;

            for (int offset = 0;
                    offset < expectedSegments.length;
                    offset++) {

                if (!path.getName(start + offset)
                        .toString()
                        .equalsIgnoreCase(
                                expectedSegments[offset])) {

                    matches = false;
                    break;
                }
            }

            if (matches) {
                return true;
            }
        }

        return false;
    }

    // ANCHOR: Classify Storage
    public static StorageType classifyStorage(File item) {

        String windowsFolder = System.getenv("SystemRoot");

        String programFiles = System.getenv("ProgramFiles");

        String programFilesX86 = System.getenv("ProgramFiles(x86)");

        String programData = System.getenv("ProgramData");

        String systemDrive = System.getenv("SystemDrive");

        String recoveryFolder = systemDrive == null
                ? null
                : systemDrive + "\\Recovery";

        // ANCHOR: Protected Windows Locations
        if (isInsideOrSame(item, windowsFolder)
                || isInsideOrSame(item, programFiles)
                || isInsideOrSame(item, programFilesX86)
                || isInsideOrSame(item, programData)
                || isInsideOrSame(item, recoveryFolder)) {

            return StorageType.PROTECTED_SYSTEM;
        }

        // ANCHOR: Developer Tools
        if (isInsideOrSame(item, "C:\\msys64")
                || hasPathSegment(item, ".m2")
                || hasPathSegment(item, ".vscode")
                || hasPathSegment(item, ".jdks")) {

            return StorageType.DEV_TOOL;
        }

        // ANCHOR: User Data
        if (hasPathSegment(item, "Downloads")
                || hasPathSegment(item, "Documents")
                || hasPathSegment(item, "Desktop")
                || hasPathSegment(item, "Videos")
                || hasPathSegment(item, "Pictures")) {

            return StorageType.USER_DATA;
        }

        // ANCHOR: Applications
        if (isInsideOrSame(item, "C:\\Riot Games")
                || isInsideOrSame(item, "C:\\LDPlayer")
                || isInsideOrSame(item, "C:\\Office 2019")
                || isInsideOrSame(item, "C:\\Office 2024")) {

            return StorageType.APPLICATION;
        }

        // ANCHOR: Recycle Bin
        if (hasPathSegment(item, "$Recycle.Bin")) {
            return StorageType.RECYCLE_BIN;
        }

        // ANCHOR: Temporary Data
        if (hasPathSegments(
                item,
                "AppData",
                "Local",
                "Temp")) {

            return StorageType.TEMPORARY_DATA;
        }

        return StorageType.UNKNOWN;
    }

    // ANCHOR: Decide Cleanup Action
    public static CleanupAction getCleanupAction(
            StorageType storageType) {

        return switch (storageType) {

            case PROTECTED_SYSTEM, RECYCLE_BIN ->
                CleanupAction.DO_NOT_DELETE;

            case APPLICATION, DEV_TOOL ->
                CleanupAction.DELETE_WITH_CONFIRMATION;

            case USER_DATA ->
                CleanupAction.SAFE_TO_REVIEW;

            case TEMPORARY_DATA ->
                CleanupAction.SAFE_TO_CLEAN;

            case UNKNOWN ->
                CleanupAction.REVIEW_MANUALLY;
        };

    }
}
