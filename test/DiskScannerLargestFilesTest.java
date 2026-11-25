import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiskScannerLargestFilesTest {
    @TempDir Path root;

    private static final Predicate<FileInfo> ELIGIBLE =
            file -> Path.of(file.path()).getFileName().toString().startsWith("keep-");

    @BeforeEach
    @AfterEach
    void resetScanner() throws Exception {
        Method clear = DiskScanner.class.getDeclaredMethod("clearFastState");
        clear.setAccessible(true);
        clear.invoke(null);
        StorageScanner.clearCache();
        StorageScanner.setProgress(null);
    }

    @Test
    void fallbackFiltersBeforeLimitingAndSearchesNestedFiles() throws Exception {
        createFiles();
        assertLargestEligibleFiles();
    }

    @Test
    void fastIndexFiltersBeforeBoundedHeapSelection() throws Exception {
        installFastIndex();
        assertTrue(DiskScanner.isFastMode());
        assertLargestEligibleFiles();
    }

    @Test
    void fastQueriesVisitSelfReferencingRootOnlyOnce() throws Exception {
        installFastIndex();
        Map<Long, List<MftEntry>> children = map("childrenByParentId");
        List<MftEntry> rootChildren = new ArrayList<>(children.get(5L));
        rootChildren.add(cycleReference(5, 5, "root"));
        children.put(5L, rootChildren);
        assertCycleSafeQueries();
    }

    @Test
    void fastQueriesStopLongerDirectoryCyclesWithoutDuplicatingFiles() throws Exception {
        installFastIndex();
        Map<Long, List<MftEntry>> children = map("childrenByParentId");
        List<MftEntry> nestedChildren = new ArrayList<>(children.get(6L));
        nestedChildren.add(cycleReference(5, 6, "back-to-root"));
        children.put(6L, nestedChildren);
        assertCycleSafeQueries();
    }

    private void assertCycleSafeQueries() {
        List<FileInfo> all = DiskScanner.scanAllFiles(root.toFile());
        assertEquals(List.of("blocked-a.bin", "blocked-b.bin", "keep-large.bin",
                "keep-medium.bin", "keep-small.bin"), names(all));
        assertEquals(250L, all.stream().mapToLong(FileInfo::sizeInBytes).sum());
        assertTrue(all.stream().noneMatch(file -> file.path().contains("back-to-root")));
        assertEquals(List.of("blocked-a.bin", "blocked-b.bin", "keep-large.bin"),
                names(DiskScanner.scanLargestFiles(root.toFile(), 3)));
        assertEquals(List.of("keep-large.bin", "keep-medium.bin"),
                names(DiskScanner.scanLargestFiles(root.toFile(), 2, ELIGIBLE)));
    }

    private static MftEntry cycleReference(long id, long parent, String name) {
        return new MftEntry(id, parent, name, true) {
            private int expansions;
            @Override public long fileId() {
                // Bound regressions even if a broken traversal ignores interruption:
                // fail promptly rather than let the test JVM loop or exhaust memory.
                if (++expansions > 100) {
                    throw new AssertionError("Directory cycle was traversed repeatedly");
                }
                return super.fileId();
            }
        };
    }

    @Test
    void legacyQueryAndCachedFilesRemainUnfiltered() throws Exception {
        createFiles();
        assertLargestEligibleFiles();
        assertEquals(List.of("blocked-a.bin", "blocked-b.bin"),
                names(DiskScanner.scanLargestFiles(root.toFile(), 2)));
        assertEquals(5, StorageScanner.scanAllFiles(root.toFile()).size());
    }

    @Test
    void bothModesHandleNoMatchesOversizedAndNonpositiveLimits() throws Exception {
        createFiles();
        for (boolean fast : List.of(false, true)) {
            if (fast) installFastIndex();
            assertTrue(DiskScanner.scanLargestFiles(root.toFile(), 20, file -> false).isEmpty());
            assertEquals(List.of("keep-large.bin", "keep-medium.bin", "keep-small.bin"),
                    names(DiskScanner.scanLargestFiles(root.toFile(), 20, ELIGIBLE)));
            for (int limit : new int[] {0, -1}) {
                assertTrue(DiskScanner.scanLargestFiles(root.toFile(), limit,
                        file -> { throw new AssertionError("Predicate must not run"); }).isEmpty());
            }
        }
    }

    @Test
    void rejectsNullEligibilityPredicate() {
        assertThrows(NullPointerException.class,
                () -> DiskScanner.scanLargestFiles(root.toFile(), 2, null));
    }

    private void assertLargestEligibleFiles() {
        // The old top-two-then-filter sequence returns no rows for this fixture.
        assertTrue(DiskScanner.scanLargestFiles(root.toFile(), 2).stream()
                .filter(ELIGIBLE).toList().isEmpty());
        assertEquals(List.of("keep-large.bin", "keep-medium.bin"),
                names(DiskScanner.scanLargestFiles(root.toFile(), 2, ELIGIBLE)));
    }

    private void createFiles() throws Exception {
        Files.write(root.resolve("blocked-a.bin"), new byte[100]);
        Files.write(root.resolve("blocked-b.bin"), new byte[90]);
        Files.write(root.resolve("keep-large.bin"), new byte[30]);
        Files.write(root.resolve("keep-small.bin"), new byte[10]);
        Path nested = Files.createDirectories(root.resolve("nested"));
        Files.write(nested.resolve("keep-medium.bin"), new byte[20]);
    }

    private static List<String> names(List<FileInfo> files) {
        return files.stream().map(file -> Path.of(file.path()).getFileName().toString()).toList();
    }

    private void installFastIndex() throws Exception {
        // Install synthetic RAM metadata: exercise the real fast-query path without
        // opening a Windows volume. Reflection avoids adding production test hooks.
        setField("fastScanResult", new FastScanner.FastScanResult(
                Map.of(), Map.of(), root.toString(), 5, 2, 0, 0, 0, 0));
        Field mode = field("scannerMode");
        mode.set(null, Arrays.stream(mode.getType().getEnumConstants())
                .filter(value -> value.toString().equals("FAST_NTFS")).findFirst().orElseThrow());
        Method normalize = DiskScanner.class.getDeclaredMethod("normalizePath", String.class);
        normalize.setAccessible(true);
        Map<String, Long> directoryIds = map("directoryIdByPath");
        directoryIds.put((String) normalize.invoke(null, root.toFile().getAbsolutePath()), 5L);
        Map<Long, List<MftEntry>> children = map("childrenByParentId");
        children.put(5L, List.of(entry(10, 5, "blocked-a.bin", 100),
                entry(11, 5, "blocked-b.bin", 90), entry(12, 5, "keep-large.bin", 30),
                entry(13, 5, "keep-small.bin", 10), new MftEntry(6, 5, "nested", true)));
        children.put(6L, List.of(entry(14, 6, "keep-medium.bin", 20)));
    }

    private static MftEntry entry(long id, long parent, String name, long size) {
        MftEntry entry = new MftEntry(id, parent, name, false);
        entry.setSizeInBytes(size);
        return entry;
    }

    private static Field field(String name) throws Exception {
        Field field = DiskScanner.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void setField(String name, Object value) throws Exception {
        field(name).set(null, value);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> map(String name) throws Exception {
        return (Map<K, V>) field(name).get(null);
    }
}
