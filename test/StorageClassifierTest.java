import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class StorageClassifierTest {

    @Test
    void classifiesKnownRootPathsAndTheirDescendants() {
        assertClassification(
                "C:\\msys64",
                StorageType.DEV_TOOL);
        assertClassification(
                "C:\\MSYS64\\usr\\bin",
                StorageType.DEV_TOOL);
        assertClassification(
                "C:\\Riot Games\\Example\\data",
                StorageType.APPLICATION);
    }

    @Test
    void classifiesKnownPathSegments() {
        assertClassification(
                "C:\\Users\\test\\.m2\\repository",
                StorageType.DEV_TOOL);
        assertClassification(
                "C:\\Users\\test\\Downloads\\file.zip",
                StorageType.USER_DATA);
        assertClassification(
                "C:\\$Recycle.Bin\\S-1-5-21",
                StorageType.RECYCLE_BIN);
        assertClassification(
                "C:\\Users\\test\\AppData\\Local\\Temp\\cache.bin",
                StorageType.TEMPORARY_DATA);
    }

    @Test
    void doesNotClassifyLookalikeRootNames() {
        assertClassification(
                "C:\\msys64-backup",
                StorageType.UNKNOWN);
        assertClassification(
                "C:\\Riot GamesBackup",
                StorageType.UNKNOWN);
    }

    @Test
    void doesNotClassifyLookalikeSegmentNames() {
        assertClassification(
                "C:\\Users\\test\\.m2-cache",
                StorageType.UNKNOWN);
        assertClassification(
                "C:\\Users\\test\\DownloadsBackup",
                StorageType.UNKNOWN);
        assertClassification(
                "C:\\$Recycle.BinBackup",
                StorageType.UNKNOWN);
        assertClassification(
                "C:\\Users\\test\\AppData\\Local\\TempBackup",
                StorageType.UNKNOWN);
    }

    private static void assertClassification(
            String path,
            StorageType expected) {

        assertEquals(
                expected,
                StorageClassifier.classifyStorage(
                        new File(path)));
    }
}
