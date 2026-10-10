import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SizeIndexTest {

    @Test
    void aggregatesAFileIntoEveryAncestorExactlyOnce() {
        MftEntry root = directory(5, 5, "C:");
        MftEntry projects = directory(10, 5, "projects");
        MftEntry file = file(20, 10, "archive.zip", 120);

        SizeIndex.Result result = SizeIndex.build(entries(root, projects, file), 10);

        assertEquals(120L, result.folderSizes().get(root.fileId()));
        assertEquals(120L, result.folderSizes().get(projects.fileId()));
    }

    @Test
    void stopsAtParentCyclesWithoutInflatingFolderSizes() {
        MftEntry folderA = directory(10, 11, "A");
        MftEntry folderB = directory(11, 10, "B");
        MftEntry file = file(20, 10, "data.bin", 42);

        SizeIndex.Result result = SizeIndex.build(entries(folderA, folderB, file), 10);

        assertEquals(Map.of(10L, 42L, 11L, 42L), result.folderSizes());
    }

    @Test
    void ignoresFilesWhoseSizeCouldNotBeResolved() {
        MftEntry root = directory(5, 5, "C:");
        MftEntry unresolved = file(20, 5, "unknown.bin", -1);

        SizeIndex.Result result = SizeIndex.build(entries(root, unresolved), 10);

        assertTrue(result.folderSizes().isEmpty());
        assertTrue(result.largestFiles().isEmpty());
    }

    @Test
    void returnsOnlyTheRequestedLargestFilesInDescendingOrder() {
        MftEntry root = directory(5, 5, "C:");
        MftEntry small = file(20, 5, "small.bin", 10);
        MftEntry large = file(21, 5, "large.bin", 100);
        MftEntry medium = file(22, 5, "medium.bin", 50);

        SizeIndex.Result result =
                SizeIndex.build(entries(root, small, large, medium), 2);

        assertEquals(List.of(large, medium),
                result.largestFiles().stream()
                        .map(SizeIndex.SizedEntry::entry)
                        .toList());
        assertEquals(List.of(100L, 50L),
                result.largestFiles().stream()
                        .map(SizeIndex.SizedEntry::sizeInBytes)
                        .toList());
    }

    @Test
    void disablesLargestFileTrackingWhenLimitIsNotPositive() {
        MftEntry root = directory(5, 5, "C:");
        MftEntry file = file(20, 5, "data.bin", 42);

        assertTrue(SizeIndex.build(entries(root, file), 0)
                .largestFiles().isEmpty());
        assertTrue(SizeIndex.build(entries(root, file), -1)
                .largestFiles().isEmpty());
    }

    private static MftEntry directory(long id, long parentId, String name) {
        return new MftEntry(id, parentId, name, true);
    }

    private static MftEntry file(
            long id,
            long parentId,
            String name,
            long size) {

        MftEntry entry = new MftEntry(id, parentId, name, false);
        entry.setSizeInBytes(size);
        return entry;
    }

    private static Map<Long, MftEntry> entries(MftEntry... values) {
        Map<Long, MftEntry> result = new LinkedHashMap<>();
        for (MftEntry entry : values) {
            result.put(entry.fileId(), entry);
        }
        return result;
    }
}
