import java.awt.Desktop;
import java.io.File;

public class DeleteManager {

    // ANCHOR: Can Delete
    public static boolean canDelete(File item) {

        if (item == null || !item.exists()) {
            return false;
        }

        // Never delete a drive root like C:\
        if (item.getParentFile() == null) {
            return false;
        }

        StorageType storageType = StorageClassifier.classifyStorage(item);

        // Hard blocks
        if (storageType == StorageType.PROTECTED_SYSTEM
                || storageType == StorageType.UNKNOWN
                || storageType == StorageType.RECYCLE_BIN) {

            return false;
        }

        // Don't delete important user folders themselves
        if (item.isDirectory()) {

            String name = item.getName().toLowerCase();

            if (name.equals("downloads")
                    || name.equals("documents")
                    || name.equals("desktop")
                    || name.equals("pictures")
                    || name.equals("videos")) {

                return false;
            }
        }

      return storageType == StorageType.USER_DATA
        || storageType == StorageType.TEMPORARY_DATA
        || storageType == StorageType.APPLICATION
        || storageType == StorageType.DEV_TOOL;
    }

    // ANCHOR: Move To Recycle Bin
    public static DeleteResult moveToRecycleBin(File item) {

        if (item == null || !item.exists()) {
            return DeleteResult.NOT_FOUND;
        }

        if (!canDelete(item)) {
            return DeleteResult.BLOCKED;
        }

        if (!Desktop.isDesktopSupported()) {
            return DeleteResult.NOT_SUPPORTED;
        }

        Desktop desktop = Desktop.getDesktop();

        if (!desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            return DeleteResult.NOT_SUPPORTED;
        }

        try {

            boolean moved = desktop.moveToTrash(item);

            if (moved) {
                return DeleteResult.SUCCESS;
            }

            return DeleteResult.FAILED;

        } catch (Exception exception) {
            return DeleteResult.FAILED;
        }
        
    }
    
}