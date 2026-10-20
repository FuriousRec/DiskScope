import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StorageScanner {

        // ANCHOR: Folder Size Cache
        private static final Map<String, Long> folderSizeCache = new HashMap<>();
        // ANCHOR: File Scan Cache
        private static final Map<String, List<FileInfo>> fileScanCache = new HashMap<>();

        // ANCHOR: Inaccessible Folders
        private static final Set<String> inaccessibleFolders = new HashSet<>();

        private static Progress progress;
        private static long scannedFolders = 0;

        // ANCHOR: Set Progress
        public static void setProgress(Progress newProgress) {
                progress = newProgress;
        }

        // ANCHOR: Safe Folder Access
        private static File[] listFilesSafely(File folder) {

                try {

                        File[] contents = folder.listFiles();

                        if (contents == null) {
                                inaccessibleFolders.add(
                                                folder.getAbsolutePath());
                        }

                        return contents;

                } catch (SecurityException exception) {

                        inaccessibleFolders.add(
                                        folder.getAbsolutePath());

                        return null;
                }
        }

        // ANCHOR: Calculate Folder Size
        public static long calculateFolderSize(File folder) {

                String folderPath = folder.getAbsolutePath();

                // Use cache first
                if (folderSizeCache.containsKey(folderPath)) {
                        return folderSizeCache.get(folderPath);
                }

                // Only count folders we actually have to scan
                scannedFolders++;

                if (progress != null) {
                        progress.update(
                                        folder.getAbsolutePath(),
                                        scannedFolders);
                }

                long totalSize = 0;

                File[] contents = listFilesSafely(folder);

                if (contents == null) {
                        return 0;
                }

                for (File item : contents) {

                        if (item.isFile()) {
                                totalSize += item.length();
                        }

                        // Recursion to see if there is a folder within a folder
                        if (item.isDirectory()) {
                                totalSize += calculateFolderSize(item);
                        }
                }

                folderSizeCache.put(
                                folderPath,
                                totalSize);

                return totalSize;
        }

        // ANCHOR: Scan Folders
        public static List<FolderInfo> scanFolders(
                        File parentFolder) {

                List<FolderInfo> folderSizes = new ArrayList<>();

                File[] items = listFilesSafely(parentFolder);

                if (items == null) {
                        return folderSizes;
                }

                for (File item : items) {

                        if (item.isDirectory()) {

                                long sizeInBytes = calculateFolderSize(item);

                                folderSizes.add(
                                                new FolderInfo(
                                                                item.getName(),
                                                                sizeInBytes));
                        }
                }

                folderSizes.sort(
                                Comparator
                                                .comparingLong(
                                                                FolderInfo::sizeInBytes)
                                                .reversed());

                return folderSizes;
        }

        // ANCHOR: Scan Direct Files
        public static List<FileInfo> scanDirectFiles(
                        File parentFolder) {

                List<FileInfo> files = new ArrayList<>();

                File[] items = listFilesSafely(parentFolder);

                if (items == null) {
                        return files;
                }

                for (File item : items) {

                        if (item.isFile()) {

                                files.add(
                                                new FileInfo(
                                                                item.getAbsolutePath(),
                                                                item.length()));
                        }
                }

                files.sort(
                                Comparator
                                                .comparingLong(
                                                                FileInfo::sizeInBytes)
                                                .reversed());

                return files;
        }

        // ANCHOR: Find Files Recursively
        public static void findFilesRecursively(
                        File folder,
                        List<FileInfo> foundFiles) {

                File[] contents = listFilesSafely(folder);

                if (contents == null) {
                        return;
                }

                for (File item : contents) {

                        if (item.isFile()) {

                                foundFiles.add(
                                                new FileInfo(
                                                                item.getAbsolutePath(),
                                                                item.length()));
                        }

                        if (item.isDirectory()) {

                                findFilesRecursively(
                                                item,
                                                foundFiles);
                        }
                }
        }

        // ANCHOR: Scan All Files
        public static List<FileInfo> scanAllFiles(
                        File folder) {

                String folderPath = folder.getAbsolutePath();

                // Use previous scan if available
                if (fileScanCache.containsKey(folderPath)) {
                        return new ArrayList<>(
                                        fileScanCache.get(folderPath));
                }

                List<FileInfo> files = new ArrayList<>();

                findFilesRecursively(
                                folder,
                                files);

                files.sort(
                                Comparator
                                                .comparingLong(
                                                                FileInfo::sizeInBytes)
                                                .reversed());

                fileScanCache.put(
                                folderPath,
                                new ArrayList<>(files));

                return files;
        }

        // ANCHOR: Scan Statistics
        public static int getInaccessibleCount() {
                return inaccessibleFolders.size();
        }

        public static long getScannedCount() {
                return scannedFolders;
        }

        // ANCHOR: Clear Cache
        public static void clearCache() {

                folderSizeCache.clear();
                fileScanCache.clear();
                inaccessibleFolders.clear();
                scannedFolders = 0;
        }

        // ANCHOR: Invalidate Cache
        public static void invalidateCache(File item) {
                java.nio.file.Path changedPath =
                                item.toPath().toAbsolutePath().normalize();

                // A change affects cached descendants and every cached ancestor.
                // Path comparisons respect directory boundaries and platform case rules.
                // No filesystem lookup is needed: the item may already be deleted.
                folderSizeCache.keySet().removeIf(
                                cachedPath -> overlaps(cachedPath, changedPath));
                fileScanCache.keySet().removeIf(
                                cachedPath -> overlaps(cachedPath, changedPath));
        }

        private static boolean overlaps(
                        String cachedPath,
                        java.nio.file.Path changedPath) {
                java.nio.file.Path cached =
                                java.nio.file.Path.of(cachedPath).toAbsolutePath().normalize();
                return cached.startsWith(changedPath) || changedPath.startsWith(cached);
        }
}
