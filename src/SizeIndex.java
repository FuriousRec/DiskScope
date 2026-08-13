import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class SizeIndex {

    public record SizedEntry(
            MftEntry entry,
            long sizeInBytes) {
    }

    public record Result(
            Map<Long, Long> folderSizes,
            List<SizedEntry> largestFiles,
            double seconds) {
    }

    // ANCHOR: Build Size Index
    public static Result build(
            Map<Long, MftEntry> entries,
            int largestFileCount) {

        long startTime =
                System.nanoTime();

        Map<Long, Long> folderSizes =
                new HashMap<>();

        PriorityQueue<SizedEntry> largestFiles =
                new PriorityQueue<>(
                        Comparator.comparingLong(
                                SizedEntry::sizeInBytes
                        )
                );

        for (MftEntry entry : entries.values()) {

            if (entry.directory()) {
                continue;
            }

            long fileSize =
                    entry.sizeInBytes();

            if (fileSize < 0) {
                continue;
            }

            // ANCHOR: Track Largest Files
            if (largestFileCount > 0) {

                largestFiles.add(
                        new SizedEntry(
                                entry,
                                fileSize
                        )
                );

                if (largestFiles.size()
                        > largestFileCount) {

                    largestFiles.poll();
                }
            }

            // ANCHOR: Add Size To Parent Folders
            long parentId =
                    entry.parentId();

            /*
             * 256 is just a safety guard against
             * corrupt/circular parent relationships.
             */
            for (int depth = 0;
                 depth < 256;
                 depth++) {

                MftEntry parent =
                        entries.get(parentId);

                if (parent == null
                        || !parent.directory()) {

                    break;
                }

                folderSizes.merge(
                        parent.fileId(),
                        fileSize,
                        Long::sum
                );

                if (parent.parentId()
                        == parent.fileId()) {

                    break;
                }

                parentId =
                        parent.parentId();
            }
        }

        List<SizedEntry> sortedLargest =
                new ArrayList<>(
                        largestFiles
                );

        sortedLargest.sort(
                Comparator.comparingLong(
                        SizedEntry::sizeInBytes
                ).reversed()
        );

        long endTime =
                System.nanoTime();

        double seconds =
                (endTime - startTime)
                        / 1_000_000_000.0;

        return new Result(
                folderSizes,
                sortedLargest,
                seconds
        );
    }
}