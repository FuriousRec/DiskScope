import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.junit.jupiter.api.Test;

class StoragePresentationEligibilityTest {
    @Test
    void rankingEligibilityUsesOnlyPathsWithoutFilesystemQueries() {
        File indexedFile = new File(new File("Downloads"), "cache.bin") {
            @Override public boolean exists() {
                throw new AssertionError("Indexed ranking must not check file existence");
            }
            @Override public boolean isDirectory() {
                throw new AssertionError("Indexed ranking must not inspect the filesystem");
            }
            @Override public boolean isFile() {
                throw new AssertionError("Indexed ranking must not inspect the filesystem");
            }
        };
        assertTrue(StoragePresentation.isCleanupCandidate(indexedFile));
    }
}
