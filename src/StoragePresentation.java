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

        String systemDrive = System.getenv("SystemDrive");
        File driveRoot = new File(
                systemDrive == null || systemDrive.isBlank()
                        ? "C:\\"
                        : systemDrive + "\\");

        return isInsideOrSame(item, new File(driveRoot, "System Volume Information"))
                || isInsideOrSame(item, new File(driveRoot, "Recovery"))
                || isSamePath(item, new File(driveRoot, "pagefile.sys"))
                || isSamePath(item, new File(driveRoot, "hiberfil.sys"))
                || isSamePath(item, new File(driveRoot, "swapfile.sys"))
                || isRootDumpStackFile(item, driveRoot);
    }

    private static boolean isInsideOrSame(File item, File protectedLocation) {
        return item.toPath()
                .toAbsolutePath()
                .normalize()
                .startsWith(protectedLocation.toPath().toAbsolutePath().normalize());
    }

    private static boolean isSamePath(File first, File second) {
        return first.toPath()
                .toAbsolutePath()
                .normalize()
                .equals(second.toPath().toAbsolutePath().normalize());
    }

    private static boolean isRootDumpStackFile(File item, File driveRoot) {
        File parent = item.getParentFile();
        return parent != null
                && isSamePath(parent, driveRoot)
                && item.getName().toLowerCase(Locale.ROOT).startsWith("dumpstack");
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
