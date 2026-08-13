import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;

public class DiskScanner {

    // ANCHOR: Scanner Mode
    private enum ScannerMode {
        FAST_NTFS,
        FALLBACK
    }

    // ANCHOR: Fast Scanner State
    private static ScannerMode scannerMode = ScannerMode.FALLBACK;
    private static FastScanner.FastScanResult fastScanResult;
    private static File rootDrive;

    // Parent MFT id -> children directly inside that folder
    private static final Map<Long, List<MftEntry>> childrenByParentId = new HashMap<>();

    // Normalized directory path -> MFT id
    private static final Map<String, Long> directoryIdByPath = new HashMap<>();

    // Low 48 bits of an NTFS file reference contain the MFT record number.
    private static final long MFT_INDEX_MASK = 0x0000FFFFFFFFFFFFL;
    private static final long NTFS_ROOT_MFT_INDEX = 5L;

    // ANCHOR: Folder Traversal Node
    private record FolderNode(
            long fileId,
            String fullPath) {
    }

    // ANCHOR: Set Progress
    public static void setProgress(Progress progress) {
        StorageScanner.setProgress(progress);
    }

    // ANCHOR: Initialize Scanner
    public static boolean initialize(File drive) {

        rootDrive = drive;

        clearFastState();
        StorageScanner.clearCache();

        String driveLetter = extractDriveLetter(drive);

        if (driveLetter == null) {
            useFallback("Drive is not a Windows drive-letter volume.");
            return false;
        }

        try {
            System.out.println("\n=== Initializing Fast Scanner ===");

            FastScanner.FastScanResult result = FastScanner.scanVolume(driveLetter);

            if (result == null || result.entries().isEmpty()) {
                useFallback("Fast NTFS scan unavailable.");
                return false;
            }

            fastScanResult = result;

            // ANCHOR: Build Query Indexes
            buildFastIndexes();

            scannerMode = ScannerMode.FAST_NTFS;

            System.out.println("\nFast NTFS scanner active.");
            System.out.printf("Indexed %,d files and %,d directories.%n",
                    result.files(),
                    result.directories());

            return true;

        } catch (RuntimeException | LinkageError exception) {

            useFallback(
                    "Fast scanner failed: "
                            + exception.getClass().getSimpleName());

            return false;
        }
    }

    // ANCHOR: Refresh Scanner
    public static boolean refresh() {

        if (rootDrive == null) {
            return false;
        }

        System.out.println("Refreshing DiskScope index...");

        return initialize(rootDrive);
    }

    // ANCHOR: Scan Folders
    public static List<FolderInfo> scanFolders(
            File parentFolder) {

        if (!isFastMode()) {
            return StorageScanner.scanFolders(parentFolder);
        }

        Long parentId = findDirectoryId(parentFolder);

        if (parentId == null) {
            printFastPathMiss(parentFolder);
            return List.of();
        }

        List<FolderInfo> folders = new ArrayList<>();

        for (MftEntry child : childrenByParentId.getOrDefault(
                parentId,
                List.of())) {

            if (!child.directory()) {
                continue;
            }

            long sizeInBytes = fastScanResult.folderSizes()
                    .getOrDefault(
                            child.fileId(),
                            0L);

            folders.add(
                    new FolderInfo(
                            child.name(),
                            sizeInBytes));
        }

        folders.sort(
                Comparator.comparingLong(
                        FolderInfo::sizeInBytes)
                        .reversed());

        return folders;
    }

    // ANCHOR: Scan Direct Files
    public static List<FileInfo> scanDirectFiles(
            File parentFolder) {

        if (!isFastMode()) {
            return StorageScanner.scanDirectFiles(parentFolder);
        }

        Long parentId = findDirectoryId(parentFolder);

        if (parentId == null) {
            printFastPathMiss(parentFolder);
            return List.of();
        }

        List<FileInfo> files = new ArrayList<>();

        for (MftEntry child : childrenByParentId.getOrDefault(
                parentId,
                List.of())) {

            if (child.directory()) {
                continue;
            }

            String fullPath = joinPath(
                    parentFolder.getAbsolutePath(),
                    child.name());

            files.add(
                    new FileInfo(
                            fullPath,
                            Math.max(
                                    0L,
                                    child.sizeInBytes())));
        }

        files.sort(
                Comparator.comparingLong(
                        FileInfo::sizeInBytes)
                        .reversed());

        return files;
    }

    // ANCHOR: Scan Largest Files
    public static List<FileInfo> scanLargestFiles(
            File folder,
            int limit) {

        if (limit <= 0) {
            return List.of();
        }

        if (!isFastMode()) {

            List<FileInfo> fallbackFiles = StorageScanner.scanAllFiles(folder);

            return new ArrayList<>(
                    fallbackFiles.subList(
                            0,
                            Math.min(
                                    limit,
                                    fallbackFiles.size())));
        }

        Long folderId = findDirectoryId(folder);

        if (folderId == null) {
            printFastPathMiss(folder);
            return List.of();
        }

        PriorityQueue<FileInfo> largestFiles = new PriorityQueue<>(
                Comparator.comparingLong(
                        FileInfo::sizeInBytes));

        Deque<FolderNode> foldersToVisit = new ArrayDeque<>();

        foldersToVisit.push(
                new FolderNode(
                        folderId,
                        folder.getAbsolutePath()));

        // ANCHOR: RAM Folder Traversal
        while (!foldersToVisit.isEmpty()) {

            FolderNode currentFolder = foldersToVisit.pop();

            for (MftEntry child : childrenByParentId.getOrDefault(
                    currentFolder.fileId(),
                    List.of())) {

                String childPath = joinPath(
                        currentFolder.fullPath(),
                        child.name());

                if (child.directory()) {

                    foldersToVisit.push(
                            new FolderNode(
                                    child.fileId(),
                                    childPath));

                    continue;
                }

                FileInfo fileInfo = new FileInfo(
                        childPath,
                        Math.max(
                                0L,
                                child.sizeInBytes()));

                largestFiles.add(fileInfo);

                if (largestFiles.size() > limit) {
                    largestFiles.poll();
                }
            }
        }

        List<FileInfo> sortedLargestFiles = new ArrayList<>(largestFiles);

        sortedLargestFiles.sort(
                Comparator.comparingLong(
                        FileInfo::sizeInBytes)
                        .reversed());

        return sortedLargestFiles;
    }

    // ANCHOR: Scan All Files
    public static List<FileInfo> scanAllFiles(
            File folder) {

        if (!isFastMode()) {
            return StorageScanner.scanAllFiles(folder);
        }

        Long folderId = findDirectoryId(folder);

        if (folderId == null) {
            printFastPathMiss(folder);
            return List.of();
        }

        List<FileInfo> files = new ArrayList<>();
        Deque<FolderNode> foldersToVisit = new ArrayDeque<>();

        foldersToVisit.push(
                new FolderNode(
                        folderId,
                        folder.getAbsolutePath()));

        while (!foldersToVisit.isEmpty()) {

            FolderNode currentFolder = foldersToVisit.pop();

            for (MftEntry child : childrenByParentId.getOrDefault(
                    currentFolder.fileId(),
                    List.of())) {

                String childPath = joinPath(
                        currentFolder.fullPath(),
                        child.name());

                if (child.directory()) {

                    foldersToVisit.push(
                            new FolderNode(
                                    child.fileId(),
                                    childPath));

                } else {

                    files.add(
                            new FileInfo(
                                    childPath,
                                    Math.max(
                                            0L,
                                            child.sizeInBytes())));
                }
            }
        }

        files.sort(
                Comparator.comparingLong(
                        FileInfo::sizeInBytes)
                        .reversed());

        return files;
    }

    // ANCHOR: Invalidate After Delete
    public static void invalidateCache(File item) {

        StorageScanner.invalidateCache(item);

        /*
         * The MFT-backed index is a snapshot.
         * Refresh it after a successful deletion so
         * folder totals and file lists remain correct.
         */
        if (isFastMode()) {
            refresh();
        }
    }

    // ANCHOR: Scanner Information
    public static boolean isFastMode() {
        return scannerMode == ScannerMode.FAST_NTFS
                && fastScanResult != null;
    }

    public static String getScannerModeName() {
        return isFastMode()
                ? "FAST_NTFS"
                : "JAVA_FALLBACK";
    }

    public static long getIndexedFileCount() {
        return isFastMode()
                ? fastScanResult.files()
                : 0;
    }

    public static long getIndexedDirectoryCount() {
        return isFastMode()
                ? fastScanResult.directories()
                : StorageScanner.getScannedCount();
    }

    public static int getInaccessibleCount() {
        return isFastMode()
                ? 0
                : StorageScanner.getInaccessibleCount();
    }

    // ANCHOR: Build Fast Query Indexes
    private static void buildFastIndexes() {

        childrenByParentId.clear();
        directoryIdByPath.clear();

        Map<Long, MftEntry> entries = fastScanResult.entries();

        // ANCHOR: Register NTFS Root
        // FSCTL_ENUM_USN_DATA may not give us a normal named root entry,
        // but top-level entries still point to MFT record 5 as their parent.
        Long rootFileId = findNtfsRootReference(entries);

        if (rootFileId != null) {
            directoryIdByPath.put(
                    normalizePath(fastScanResult.driveRoot()),
                    rootFileId);
        }

        for (MftEntry entry : entries.values()) {

            childrenByParentId
                    .computeIfAbsent(
                            entry.parentId(),
                            ignored -> new ArrayList<>())
                    .add(entry);

            if (!entry.directory()) {
                continue;
            }

            String fullPath = FastScanner.buildFullPath(
                    entry.fileId(),
                    entries,
                    fastScanResult.driveRoot());

            directoryIdByPath.put(
                    normalizePath(fullPath),
                    entry.fileId());
        }
    }

    // ANCHOR: Find Folder MFT Id
    private static Long findDirectoryId(
            File folder) {

        return directoryIdByPath.get(
                normalizePath(
                        folder.getAbsolutePath()));
    }

    // ANCHOR: Find NTFS Root Reference
    private static Long findNtfsRootReference(
            Map<Long, MftEntry> entries) {

        // Best case: the root record itself was enumerated.
        for (MftEntry entry : entries.values()) {

            long mftIndex = entry.fileId() & MFT_INDEX_MASK;

            if (entry.directory()
                    && mftIndex == NTFS_ROOT_MFT_INDEX) {
                return entry.fileId();
            }
        }

        // Common case: root record is not stored as a normal USN entry,
        // so recover its full file reference from a top-level child's parent.
        for (MftEntry entry : entries.values()) {

            long parentMftIndex = entry.parentId() & MFT_INDEX_MASK;

            if (parentMftIndex == NTFS_ROOT_MFT_INDEX) {
                return entry.parentId();
            }
        }

        return null;
    }

    // ANCHOR: Fast Path Miss
    private static void printFastPathMiss(
            File folder) {

        System.out.println(
                "Fast index could not resolve: "
                        + folder.getAbsolutePath());
        System.out.println(
                "Type refresh to rebuild the fast index.");
    }

    // ANCHOR: Use Java Fallback
    private static void useFallback(
            String reason) {

        clearFastState();
        scannerMode = ScannerMode.FALLBACK;

        System.out.println(reason);
        System.out.println(
                "Using normal Java StorageScanner fallback.");
    }

    // ANCHOR: Clear Fast State
    private static void clearFastState() {

        fastScanResult = null;
        childrenByParentId.clear();
        directoryIdByPath.clear();
        scannerMode = ScannerMode.FALLBACK;
    }

    // ANCHOR: Extract Drive Letter
    private static String extractDriveLetter(
            File drive) {

        if (drive == null) {
            return null;
        }

        String path = drive.getAbsolutePath();

        if (path.length() >= 2
                && path.charAt(1) == ':') {

            return String.valueOf(
                    Character.toUpperCase(
                            path.charAt(0)));
        }

        return null;
    }

    // ANCHOR: Normalize Windows Path
    private static String normalizePath(
            String path) {

        String normalized = path
                .replace('/', '\\')
                .toLowerCase(Locale.ROOT);

        while (normalized.endsWith("\\")
                && normalized.length() > 3) {

            normalized = normalized.substring(
                    0,
                    normalized.length() - 1);
        }

        return normalized;
    }

    // ANCHOR: Join Windows Path
    private static String joinPath(
            String parentPath,
            String childName) {

        if (parentPath.endsWith("\\")
                || parentPath.endsWith("/")) {

            return parentPath + childName;
        }

        return parentPath
                + File.separator
                + childName;
    }
        // ANCHOR: User Visible File Safety
private static boolean isUserVisibleFile(String path) {

    File file = new File(path);

    StorageType storageType =
            StorageClassifier.classifyStorage(file);

    return switch (storageType) {

        case USER_DATA,
             TEMPORARY_DATA,
             APPLICATION,
             DEV_TOOL -> true;

        case PROTECTED_SYSTEM,
             RECYCLE_BIN,
             UNKNOWN -> false;
    };
}
}
