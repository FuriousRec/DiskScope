import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageItemCsvExporterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exportsUtf8RowsWithCsvEscapingAndFormulaNeutralization() {
        StorageItem item = new StorageItem(
                new File("reports", "quarter,one.txt"),
                "=HYPERLINK(\"https://example.test\")",
                42,
                false,
                StorageType.USER_DATA,
                CleanupAction.SAFE_TO_REVIEW,
                "User",
                "Review \"manually\", then clean\nif appropriate",
                "review-badge",
                true,
                true);

        String csv = StorageItemCsvExporter.toCsv(List.of(item));

        assertTrue(csv.startsWith(
                "\uFEFF\"Name\",\"Path\",\"SizeBytes\",\"Kind\","
                        + "\"StorageType\",\"CleanupAction\",\"Recommendation\"\r\n"));
        assertTrue(csv.contains(
                "\"'=HYPERLINK(\"\"https://example.test\"\")\""));
        assertTrue(csv.contains("\"42\"") == false);
        assertTrue(csv.contains(",42,\"File\",\"USER_DATA\",\"SAFE_TO_REVIEW\","));
        assertTrue(csv.contains(
                "\"Review \"\"manually\"\", then clean\nif appropriate\""));
        assertTrue(csv.endsWith("\r\n"));
    }

    @Test
    void writesExactlyTheGeneratedUtf8Document() throws Exception {
        StorageItem item = new StorageItem(
                new File("Résumé.txt"),
                "Résumé.txt",
                7,
                false,
                StorageType.USER_DATA,
                CleanupAction.SAFE_TO_REVIEW,
                "User",
                "Review",
                "review-badge",
                true,
                true);
        Path destination = temporaryDirectory.resolve("visible-results.csv");
        String expected = StorageItemCsvExporter.toCsv(List.of(item));

        StorageItemCsvExporter.write(destination, List.of(item));

        assertEquals(expected, Files.readString(destination, StandardCharsets.UTF_8));
    }
}
