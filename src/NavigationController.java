import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

import javafx.concurrent.Task;

public class NavigationController {

    public interface Listener {
        void scanStarted(File targetFolder);

        void scanProgress(String message);

        void scanCompleted(ScanSnapshot snapshot);

        void scanFailed(Throwable throwable);
    }

    private final Listener listener;
    private final Deque<File> backStack = new ArrayDeque<>();

    private File currentDrive;
    private File currentFolder;
    private File indexedDrive;
    private boolean running;

    public NavigationController(Listener listener) {
        this.listener = listener;
    }

    public void scanDrive(File drive) {
        if (drive == null) {
            return;
        }

        backStack.clear();
        currentDrive = drive;
        runScan(drive, drive, false, true);
    }

    public void openFolder(File folder) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return;
        }

        File drive = folder.toPath().getRoot() == null
                ? currentDrive
                : folder.toPath().getRoot().toFile();

        boolean needsIndex = indexedDrive == null || !sameDrive(indexedDrive, drive);

        runScan(drive, folder, true, needsIndex);
    }

    public void refresh() {
        if (currentDrive == null || currentFolder == null) {
            return;
        }

        runScan(currentDrive, currentFolder, false, indexedDrive == null);
    }

    public void back() {
        if (backStack.isEmpty()) {
            return;
        }

        File previous = backStack.pop();
        File drive = previous.toPath().getRoot().toFile();
        runScan(drive, previous, false, indexedDrive == null || !sameDrive(indexedDrive, drive));
    }

    public void up() {
        if (currentFolder == null) {
            return;
        }

        File parent = currentFolder.getParentFile();
        if (parent != null) {
            openFolder(parent);
        }
    }

    private void runScan(File drive, File targetFolder, boolean rememberCurrentFolder, boolean initializeScanner) {
        if (running || drive == null || targetFolder == null) {
            return;
        }

        if (rememberCurrentFolder && currentFolder != null) {
            backStack.push(currentFolder);
        }

        running = true;
        listener.scanStarted(targetFolder);

        Task<ScanSnapshot> task = new Task<>() {
            @Override
            protected ScanSnapshot call() {
                long scanStart = System.nanoTime();

                DiskScanner.setProgress((path, scanned) ->
                        updateMessage("Pac-Man is chewing through " + scanned + " folders"));

                if (initializeScanner) {
                    updateMessage("Indexing " + drive.getAbsolutePath());
                    DiskScanner.initialize(drive);
                } else {
                    updateMessage("Using current index");
                }

                updateMessage("Sorting folders by size");
                List<StorageItem> folders = DiskScanner.scanFolders(targetFolder)
                        .stream()
                        .map(folder -> StorageItem.fromFolder(targetFolder, folder))
                        .sorted(Comparator.comparingLong(StorageItem::sizeInBytes).reversed())
                        .toList();

                updateMessage("Reading direct files");
                List<StorageItem> directFiles = DiskScanner.scanDirectFiles(targetFolder)
                        .stream()
                        .map(StorageItem::fromFile)
                        .sorted(Comparator.comparingLong(StorageItem::sizeInBytes).reversed())
                        .toList();

                List<StorageItem> browseItems = new ArrayList<>();
                browseItems.addAll(folders);
                browseItems.addAll(directFiles);

                updateMessage("Finding safe large files");
                List<StorageItem> largestFiles = DiskScanner.scanLargestFiles(targetFolder, 20)
                        .stream()
                        .map(StorageItem::fromFile)
                        .filter(StorageItem::cleanupCandidate)
                        .sorted(Comparator.comparingLong(StorageItem::sizeInBytes).reversed())
                        .toList();

                double scanSeconds = (System.nanoTime() - scanStart) / 1_000_000_000.0;

                return new ScanSnapshot(
                        drive,
                        targetFolder,
                        browseItems,
                        largestFiles,
                        DiskScanner.getScannerModeName(),
                        DiskScanner.getIndexedFileCount(),
                        DiskScanner.getIndexedDirectoryCount(),
                        DiskScanner.getInaccessibleCount(),
                        scanSeconds);
            }
        };

        task.messageProperty().addListener((ignored, oldValue, newValue) -> {
            if (newValue != null && !newValue.isBlank()) {
                listener.scanProgress(newValue);
            }
        });

        task.setOnSucceeded(ignored -> {
            running = false;
            ScanSnapshot snapshot = task.getValue();
            currentDrive = drive;
            currentFolder = targetFolder;
            if (initializeScanner) {
                indexedDrive = drive;
            }
            listener.scanCompleted(snapshot);
        });

        task.setOnFailed(ignored -> {
            running = false;
            listener.scanFailed(task.getException());
        });

        Thread worker = new Thread(task, "diskscope-scan");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean sameDrive(File first, File second) {
        return first != null
                && second != null
                && first.getAbsolutePath().equalsIgnoreCase(second.getAbsolutePath());
    }

    public record ScanSnapshot(
            File drive,
            File targetFolder,
            List<StorageItem> browseItems,
            List<StorageItem> largestItems,
            String scannerMode,
            long indexedFiles,
            long indexedFolders,
            int inaccessibleFolders,
            double scanSeconds) {
    }
}
