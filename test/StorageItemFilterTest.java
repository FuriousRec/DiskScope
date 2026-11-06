import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class StorageItemFilterTest {

    @Test
    void matchesAllTermsAcrossNamePathCategoryAndRecommendation() {
        StorageItem report = item(
                "C:\\Users\\Frananya\\Downloads\\Quarterly Report.PDF",
                StorageType.USER_DATA,
                CleanupAction.SAFE_TO_REVIEW,
                "User",
                "Review");
        StorageItem archive = item(
                "C:\\Temp\\archive.zip",
                StorageType.TEMPORARY_DATA,
                CleanupAction.SAFE_TO_CLEAN,
                "Temp",
                "Safe");

        assertEquals(
                List.of(report),
                StorageItemFilter.filter(
                        List.of(report, archive),
                        "DOWNLOADS user REVIEW report"));
    }

    @Test
    void supportsHumanReadableEnumNamesAsSearchTerms() {
        StorageItem system = item(
                "C:\\Windows\\memory.dmp",
                StorageType.PROTECTED_SYSTEM,
                CleanupAction.DO_NOT_DELETE,
                "System",
                "Protected");

        assertEquals(
                List.of(system),
                StorageItemFilter.filter(
                        List.of(system),
                        "protected system do not delete"));
    }

    @Test
    void blankQueryPreservesOrderWithoutAliasingMutableInput() {
        StorageItem first = item(
                "C:\\Temp\\first.log",
                StorageType.TEMPORARY_DATA,
                CleanupAction.SAFE_TO_CLEAN,
                "Temp",
                "Safe");
        StorageItem second = item(
                "C:\\Temp\\second.log",
                StorageType.TEMPORARY_DATA,
                CleanupAction.SAFE_TO_CLEAN,
                "Temp",
                "Safe");
        List<StorageItem> source = new ArrayList<>(List.of(first, second));

        List<StorageItem> filtered = StorageItemFilter.filter(source, "  ");

        assertEquals(List.of(first, second), filtered);
        assertNotSame(source, filtered);
    }

    @Test
    void returnsEmptyListWhenOneTermDoesNotMatch() {
        StorageItem archive = item(
                "C:\\Temp\\archive.zip",
                StorageType.TEMPORARY_DATA,
                CleanupAction.SAFE_TO_CLEAN,
                "Temp",
                "Safe");

        assertTrue(StorageItemFilter.filter(
                List.of(archive),
                "archive document").isEmpty());
    }

    @Test
    void combinesHumanReadableSizeConstraintsWithTextTerms() {
        StorageItem smallLog = item(
                "C:\\Temp\\small.log", 512L << 10,
                StorageType.TEMPORARY_DATA, CleanupAction.SAFE_TO_CLEAN,
                "Temp", "Safe");
        StorageItem largeArchive = item(
                "C:\\Temp\\archive.zip", 2L << 20,
                StorageType.TEMPORARY_DATA, CleanupAction.SAFE_TO_CLEAN,
                "Temp", "Safe");
        StorageItem video = item(
                "C:\\Users\\test\\Videos\\movie.mp4", 2L << 30,
                StorageType.USER_DATA, CleanupAction.SAFE_TO_REVIEW,
                "User", "Review");

        assertEquals(
                List.of(largeArchive),
                StorageItemFilter.filter(
                        List.of(smallLog, largeArchive, video),
                        "TEMP size:>=1mb size:<1GB"));
    }

    @Test
    void respectsDecimalAndInclusiveSizeBoundaries() {
        long oneAndHalfGiB = 1536L << 20;
        StorageItem image = item(
                "C:\\Users\\test\\Pictures\\disk.img", oneAndHalfGiB,
                StorageType.USER_DATA, CleanupAction.SAFE_TO_REVIEW,
                "User", "Review");

        assertEquals(List.of(image),
                StorageItemFilter.filter(List.of(image), "size:>=1.5GB"));
        assertEquals(List.of(image),
                StorageItemFilter.filter(List.of(image), "size:<=1536mb"));
        assertTrue(StorageItemFilter.filter(
                List.of(image), "size:>1.5GB").isEmpty());
        assertTrue(StorageItemFilter.filter(
                List.of(image), "size:<1536MB").isEmpty());
    }

    private static StorageItem item(
            String path,
            StorageType storageType,
            CleanupAction cleanupAction,
            String typeLabel,
            String recommendation) {

        return item(path, 100, storageType, cleanupAction,
                typeLabel, recommendation);
    }

    private static StorageItem item(
            String path,
            long size,
            StorageType storageType,
            CleanupAction cleanupAction,
            String typeLabel,
            String recommendation) {

        File file = new File(path);
        return new StorageItem(
                file,
                file.getName(),
                size,
                false,
                storageType,
                cleanupAction,
                typeLabel,
                recommendation,
                "test-badge",
                true,
                false);
    }
}
