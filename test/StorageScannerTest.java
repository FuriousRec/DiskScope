import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageScannerTest {
    @TempDir
    Path root;

    @BeforeEach
    @AfterEach
    void resetScanner() {
        StorageScanner.clearCache();
        StorageScanner.setProgress(null);
    }

    @Test
    void relativePathInvalidatesAbsoluteAncestorCaches() throws IOException {
        Path directory = Files.createDirectories(root.resolve("nested"));
        Path file = Files.write(directory.resolve("data.bin"), new byte[3]);
        assertEquals(3, StorageScanner.calculateFolderSize(root.toFile()));
        assertEquals(3, StorageScanner.scanAllFiles(root.toFile()).getFirst().sizeInBytes());

        Files.write(file, new byte[7]);
        // Resolve relative to the process directory, including any required "..".
        Path relative = Path.of("").toAbsolutePath().relativize(file.toAbsolutePath());
        StorageScanner.invalidateCache(relative.toFile());

        assertEquals(7, StorageScanner.calculateFolderSize(root.toFile()));
        assertEquals(7, StorageScanner.scanAllFiles(root.toFile()).getFirst().sizeInBytes());
    }

    @Test
    void directoryInvalidationClearsDescendantsButPreservesLookalikeSibling() throws IOException {
        Path target = Files.createDirectories(root.resolve("cache").resolve("child"));
        Path sibling = Files.createDirectories(root.resolve("cache-backup"));
        Path file = Files.write(target.resolve("data.bin"), new byte[3]);
        Files.write(sibling.resolve("keep.bin"), new byte[5]);
        assertEquals(3, StorageScanner.calculateFolderSize(target.toFile()));
        StorageScanner.scanAllFiles(target.toFile());
        assertEquals(5, StorageScanner.calculateFolderSize(sibling.toFile()));
        StorageScanner.scanAllFiles(sibling.toFile());

        Files.write(file, new byte[7]);
        StorageScanner.invalidateCache(root.resolve("cache").resolve(".").toFile());
        long scanned = StorageScanner.getScannedCount();
        assertEquals(5, StorageScanner.calculateFolderSize(sibling.toFile()));
        assertEquals(scanned, StorageScanner.getScannedCount(), "Sibling cache should survive");
        assertEquals(7, StorageScanner.calculateFolderSize(target.toFile()));
        assertEquals(7, StorageScanner.scanAllFiles(target.toFile()).getFirst().sizeInBytes());
    }

    @Test
    void alreadyDeletedFileInvalidatesAncestorCaches() throws IOException {
        Path file = Files.write(root.resolve("gone.bin"), new byte[3]);
        assertEquals(3, StorageScanner.calculateFolderSize(root.toFile()));
        assertEquals(1, StorageScanner.scanAllFiles(root.toFile()).size());

        Files.delete(file); // Only a file created inside this test's temporary directory.
        StorageScanner.invalidateCache(root.resolve(".").resolve("gone.bin").toFile());

        assertEquals(0, StorageScanner.calculateFolderSize(root.toFile()));
        assertTrue(StorageScanner.scanAllFiles(root.toFile()).isEmpty());
    }
}
