import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class StoragePresentationTest {

    @Test
    void protectsOnlyCompleteSystemLocationsAndRootFiles() {
        String drive = systemDrive();

        assertProtected(drive + "\\Recovery");
        assertProtected(drive + "\\Recovery\\WindowsRE\\winre.wim");
        assertProtected(drive + "\\System Volume Information");
        assertProtected(drive + "\\System Volume Information\\tracking.log");
        assertProtected(drive + "\\pagefile.sys");
        assertProtected(drive + "\\hiberfil.sys");
        assertProtected(drive + "\\swapfile.sys");
        assertProtected(drive + "\\DumpStack.log.tmp");
    }

    @Test
    void leavesLookalikePathsAndUserFilesReviewable() {
        String drive = systemDrive();

        assertNotProtected(drive + "\\RecoveryBackup");
        assertNotProtected(drive + "\\System Volume InformationBackup");
        assertNotProtected(drive + "\\pagefile.sys.backup");
        assertNotProtected(drive + "\\Users\\test\\Downloads\\dumpstack-notes.txt");
        assertNotProtected(drive + "\\Users\\test\\Downloads\\Windows\\notes.txt");
    }

    private static void assertProtected(String path) {
        StoragePresentation.View view = StoragePresentation.from(new File(path));

        assertTrue(view.protectedSystem(), path);
        assertEquals(StorageType.PROTECTED_SYSTEM, view.storageType(), path);
        assertEquals(CleanupAction.DO_NOT_DELETE, view.cleanupAction(), path);
        assertFalse(view.cleanupCandidate(), path);
        assertFalse(view.canDelete(), path);
    }

    private static void assertNotProtected(String path) {
        StoragePresentation.View view = StoragePresentation.from(new File(path));

        assertFalse(view.protectedSystem(), path);
        assertFalse(view.storageType() == StorageType.PROTECTED_SYSTEM, path);
    }

    private static String systemDrive() {
        String configured = System.getenv("SystemDrive");
        return configured == null || configured.isBlank() ? "C:" : configured;
    }
}
