import java.io.File;
import java.util.Locale;

public final class StoragePresentation {

    private StoragePresentation() {
    }

    public static View from(File item) {
        StorageType storageType = StorageClassifier.classifyStorage(item);
        CleanupAction cleanupAction = StorageClassifier.getCleanupAction(storageType);

        if (isProtectedSystemPath(item, storageType)) {
            return new View(
                    StorageType.PROTECTED_SYSTEM,
                    CleanupAction.DO_NOT_DELETE,
                    "System",
                    "Protected",
                    "system-badge",
                    true,
                    false,
                    false);
        }

        if (storageType == StorageType.RECYCLE_BIN) {
            return new View(
                    storageType,
                    cleanupAction,
                    "Recycle",
                    "Manage",
                    "recycle-badge",
                    false,
                    false,
                    false);
        }

        boolean canDelete = DeleteManager.canDelete(item);

        return new View(
                storageType,
                cleanupAction,
                typeLabel(storageType),
                actionLabel(cleanupAction),
                actionStyle(cleanupAction),
                false,
                canShowAsCleanupCandidate(storageType),
                canDelete);
    }

    private static boolean isProtectedSystemPath(File item, StorageType storageType) {
        if (storageType == StorageType.PROTECTED_SYSTEM) {
            return true;
        }

        String path = item.getAbsolutePath().replace('/', '\\').toLowerCase(Locale.ROOT);
        String name = item.getName().toLowerCase(Locale.ROOT);
        String systemDrive = System.getenv("SystemDrive");
        String drivePrefix = systemDrive == null || systemDrive.isBlank()
                ? "c:"
                : systemDrive.toLowerCase(Locale.ROOT);

        return path.equals(drivePrefix + "\\pagefile.sys")
                || path.equals(drivePrefix + "\\hiberfil.sys")
                || path.equals(drivePrefix + "\\swapfile.sys")
                || path.startsWith(drivePrefix + "\\system volume information")
                || path.startsWith(drivePrefix + "\\recovery")
                || name.startsWith("dumpstack")
                || path.contains("\\windows\\")
                || path.equals(drivePrefix + "\\windows");
    }

    private static boolean canShowAsCleanupCandidate(StorageType storageType) {
        return storageType != StorageType.PROTECTED_SYSTEM
                && storageType != StorageType.RECYCLE_BIN
                && storageType != StorageType.UNKNOWN;
    }

    private static String typeLabel(StorageType storageType) {
        return switch (storageType) {
            case PROTECTED_SYSTEM -> "System";
            case APPLICATION -> "App";
            case DEV_TOOL -> "Dev";
            case USER_DATA -> "User";
            case TEMPORARY_DATA -> "Temp";
            case RECYCLE_BIN -> "Recycle";
            case UNKNOWN -> "Unknown";
        };
    }

    private static String actionLabel(CleanupAction cleanupAction) {
        return switch (cleanupAction) {
            case DO_NOT_DELETE -> "Protected";
            case DELETE_WITH_CONFIRMATION -> "Confirm";
            case SAFE_TO_REVIEW -> "Review";
            case SAFE_TO_CLEAN -> "Safe";
            case REVIEW_MANUALLY -> "Review";
        };
    }

    private static String actionStyle(CleanupAction cleanupAction) {
        return switch (cleanupAction) {
            case DO_NOT_DELETE -> "system-badge";
            case DELETE_WITH_CONFIRMATION -> "confirm-badge";
            case SAFE_TO_REVIEW -> "review-badge";
            case SAFE_TO_CLEAN -> "safe-badge";
            case REVIEW_MANUALLY -> "unknown-badge";
        };
    }

    public record View(
            StorageType storageType,
            CleanupAction cleanupAction,
            String typeLabel,
            String actionLabel,
            String badgeStyle,
            boolean protectedSystem,
            boolean cleanupCandidate,
            boolean canDelete) {
    }
}
