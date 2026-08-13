import java.io.File;
import java.util.List;

public class ConsoleDisplay {

        // ANCHOR: Display Drive Information
        public static void displayDriveInformation(File drive) {

                double totalSpaceInGb = drive.getTotalSpace()
                                / (1024.0 * 1024 * 1024);

                double freeSpaceInGb = drive.getFreeSpace()
                                / (1024.0 * 1024 * 1024);

                double usedSpaceInGb = totalSpaceInGb
                                - freeSpaceInGb;

                double usedPercentage = totalSpaceInGb > 0
                                ? (usedSpaceInGb / totalSpaceInGb) * 100
                                : 0;

                System.out.println("Drive: " + drive);

                System.out.printf(
                                "Total space: %.1f GB%n",
                                totalSpaceInGb);

                System.out.printf(
                                "Free space: %.1f GB%n",
                                freeSpaceInGb);

                System.out.printf(
                                "Used space: %.1f GB%n",
                                usedSpaceInGb);

                System.out.printf(
                                "Used: %.1f%%%n",
                                usedPercentage);
        }

        // ANCHOR: Display Folders
        public static void displayFolders(
                        File parentFolder,
                        List<FolderInfo> folders) {

                for (FolderInfo folderInfo : folders) {

                        File folder = new File(
                                        parentFolder,
                                        folderInfo.name());

                        StorageType storageType =
                                        StorageClassifier.classifyStorage(
                                                        folder);

                        CleanupAction cleanupAction =
                                        StorageClassifier.getCleanupAction(
                                                        storageType);

                        double sizeInGb = folderInfo.sizeInBytes()
                                        / (1024.0 * 1024 * 1024);

                        if (sizeInGb >= 0.1) {

                                System.out.printf(
                                                "%-30s %8.1f GB   [%s]   [%s]%n",
                                                folderInfo.name(),
                                                sizeInGb,
                                                storageType,
                                                cleanupAction);
                        }
                }
        }

        // ANCHOR: Display Files
        public static void displayFiles(
                        List<FileInfo> files) {

                for (FileInfo fileInfo : files) {

                        double sizeInGb = fileInfo.sizeInBytes()
                                        / (1024.0 * 1024 * 1024);

                        if (sizeInGb >= 0.1) {

                                System.out.printf(
                                                "%s: %.1f GB%n",
                                                fileInfo.path(),
                                                sizeInGb);
                        }
                }
        }

        // ANCHOR: Display Largest Files
        public static void displayLargestFiles(
                        File selectedFolder,
                        List<FileInfo> largestFiles) {

                System.out.println(
                                "\n=== 10 Largest Files ===");

                for (int index = 0;
                                index < Math.min(
                                                10,
                                                largestFiles.size());
                                index++) {

                        FileInfo fileInfo =
                                        largestFiles.get(index);

                        double sizeInGb = fileInfo.sizeInBytes()
                                        / (1024.0 * 1024 * 1024);

                        String relativePath;

                        try {
                                relativePath = selectedFolder
                                                .toPath()
                                                .relativize(
                                                                new File(
                                                                                fileInfo.path())
                                                                                .toPath())
                                                .toString();

                        } catch (IllegalArgumentException exception) {
                                relativePath = fileInfo.path();
                        }

                        System.out.printf(
                                        "%d. %s: %.1f GB%n",
                                        index + 1,
                                        relativePath,
                                        sizeInGb);
                }
        }

        // ANCHOR: Scan Summary
        public static void displayScanSummary() {

                System.out.println(
                                "\n=== Scan Summary ===");

                System.out.println(
                                "Scanner: "
                                                + DiskScanner.getScannerModeName());

                if (DiskScanner.isFastMode()) {

                        System.out.printf(
                                        "%,d files indexed.%n",
                                        DiskScanner.getIndexedFileCount());

                        System.out.printf(
                                        "%,d directories indexed.%n",
                                        DiskScanner.getIndexedDirectoryCount());

                } else {

                        System.out.printf(
                                        "%,d folders scanned.%n",
                                        DiskScanner.getIndexedDirectoryCount());

                        System.out.printf(
                                        "%,d folders could not be accessed.%n",
                                        DiskScanner.getInaccessibleCount());
                }
        }
}