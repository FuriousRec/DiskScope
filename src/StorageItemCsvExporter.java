import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Exports the currently visible storage rows for review outside DiskScope. */
final class StorageItemCsvExporter {
    private static final String HEADER =
            "\"Name\",\"Path\",\"SizeBytes\",\"Kind\",\"StorageType\","
                    + "\"CleanupAction\",\"Recommendation\"\r\n";

    private StorageItemCsvExporter() {
    }

    static void write(Path destination, List<StorageItem> items) throws IOException {
        Objects.requireNonNull(destination, "destination");
        Files.writeString(
                destination,
                toCsv(items),
                StandardCharsets.UTF_8);
    }

    static String toCsv(List<StorageItem> items) {
        Objects.requireNonNull(items, "items");

        StringBuilder csv = new StringBuilder();
        // A UTF-8 BOM lets current Windows spreadsheet applications recognize
        // non-ASCII filenames without requiring an import wizard.
        csv.append('\uFEFF').append(HEADER);

        for (StorageItem item : items) {
            Objects.requireNonNull(item, "items must not contain null");
            appendText(csv, item.name());
            csv.append(',');
            appendText(csv, item.file().getAbsolutePath());
            csv.append(',').append(item.sizeInBytes()).append(',');
            appendText(csv, item.directory() ? "Directory" : "File");
            csv.append(',');
            appendText(csv, item.storageType().name());
            csv.append(',');
            appendText(csv, item.cleanupAction().name());
            csv.append(',');
            appendText(csv, item.recommendationLabel());
            csv.append("\r\n");
        }

        return csv.toString();
    }

    private static void appendText(StringBuilder csv, String value) {
        String safe = neutralizeFormula(Objects.requireNonNull(value, "CSV field"));
        csv.append('"')
                .append(safe.replace("\"", "\"\""))
                .append('"');
    }

    private static String neutralizeFormula(String value) {
        int firstContent = 0;
        while (firstContent < value.length()
                && Character.isWhitespace(value.charAt(firstContent))) {
            firstContent++;
        }

        if (firstContent < value.length()
                && "=+-@".indexOf(value.charAt(firstContent)) >= 0) {
            return "'" + value;
        }

        return value;
    }
}
