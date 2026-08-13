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

    // ANCHOR: Classify Storage
    public static StorageType classifyStorage(File item) {

        String path = item.getAbsolutePath().toLowerCase();

        String windowsFolder = System.getenv("SystemRoot");

        String programFiles = System.getenv("ProgramFiles");

        String programFilesX86 = System.getenv("ProgramFiles(x86)");

        String programData = System.getenv("ProgramData");

        String systemDrive = System.getenv("SystemDrive");

        // ANCHOR: Protected Windows Locations
        if (isInsideOrSame(item, windowsFolder)
                || isInsideOrSame(item, programFiles)
                || isInsideOrSame(item, programFilesX86)
                || isInsideOrSame(item, programData)
                || isInsideOrSame(
                        item,
                        systemDrive + "\\Recovery")) {

            return StorageType.PROTECTED_SYSTEM;
        }

        // ANCHOR: Developer Tools
        if (path.startsWith("c:\\msys64")
                || path.contains("\\.m2")
                || path.contains("\\.vscode")
                || path.contains("\\.jdks")) {

            return StorageType.DEV_TOOL;
        }

        // ANCHOR: User Data
        if (path.contains("\\downloads")
                || path.contains("\\documents")
                || path.contains("\\desktop")
                || path.contains("\\videos")
                || path.contains("\\pictures")) {

            return StorageType.USER_DATA;
        }

        // ANCHOR: Applications
        if (path.startsWith("c:\\riot games")
                || path.startsWith("c:\\ldplayer")
                || path.startsWith("c:\\office 2019")
                || path.startsWith("c:\\office 2024")) {

            return StorageType.APPLICATION;
        }
        // ANCHOR: Recycle Bin
        if (path.contains("\\$recycle.bin")) {
            return StorageType.RECYCLE_BIN;
        }

        // ANCHOR: Temporary Data
        if (path.contains("\\appdata\\local\\temp")) {
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