import java.io.File;

public record StorageItem(
        File file,
        String name,
        long sizeInBytes,
        boolean directory,
        StorageType storageType,
        CleanupAction cleanupAction,
        String typeLabel,
        String recommendationLabel,
        String badgeStyle,
        boolean cleanupCandidate,
        boolean canDelete) {

    public static StorageItem fromFolder(File parent, FolderInfo folderInfo) {
        File folder = new File(parent, folderInfo.name());
        StoragePresentation.View view = StoragePresentation.from(folder);

        return new StorageItem(
                folder,
                folderInfo.name(),
                folderInfo.sizeInBytes(),
                true,
                view.storageType(),
                view.cleanupAction(),
                view.typeLabel(),
                view.actionLabel(),
                view.badgeStyle(),
                view.cleanupCandidate(),
                view.canDelete());
    }

    public static StorageItem fromFile(FileInfo fileInfo) {
        File file = new File(fileInfo.path());
        StoragePresentation.View view = StoragePresentation.from(file);

        return new StorageItem(
                file,
                file.getName(),
                fileInfo.sizeInBytes(),
                false,
                view.storageType(),
                view.cleanupAction(),
                view.typeLabel(),
                view.actionLabel(),
                view.badgeStyle(),
                view.cleanupCandidate(),
                view.canDelete());
    }
}
