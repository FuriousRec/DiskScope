import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;

public class FallbackSizeReader {

    // ANCHOR: Fallback Statistics
    public record FallbackStats(
            long filesChecked,
            long sizesFound,
            long sizesMissing,
            double seconds) {
    }

    // ANCHOR: Read Missing Sizes
    public static FallbackStats readMissingSizes(
            Map<Long, MftEntry> entries,
            String driveRoot) {

        long filesChecked = 0;
        long sizesFound = 0;
        long sizesMissing = 0;

        long startTime =
                System.nanoTime();

        // ANCHOR: Check Unresolved Files
        for (MftEntry entry : entries.values()) {

            if (entry.directory()) {
                continue;
            }

            // NTFS already found this one
            if (entry.sizeInBytes() >= 0) {
                continue;
            }

            filesChecked++;

            // ANCHOR: Reconstruct File Path
            String fullPath =
                    FastScanner.buildFullPath(
                            entry.fileId(),
                            entries,
                            driveRoot
                    );

            try {

                // ANCHOR: Java Size Fallback
                long size =
                        Files.size(
                                Path.of(fullPath)
                        );

                entry.setSizeInBytes(size);
                sizesFound++;

            } catch (IOException
                     | InvalidPathException
                     | SecurityException exception) {

                sizesMissing++;
            }
        }

        long endTime =
                System.nanoTime();

        double seconds =
                (endTime - startTime)
                        / 1_000_000_000.0;

        return new FallbackStats(
                filesChecked,
                sizesFound,
                sizesMissing,
                seconds
        );
    }
}