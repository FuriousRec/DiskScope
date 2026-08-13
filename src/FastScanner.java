import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;

public class FastScanner {

        // ANCHOR: Windows Control Codes
        private static final int FSCTL_QUERY_USN_JOURNAL = 0x000900F4;

        private static final int FSCTL_ENUM_USN_DATA = 0x000900B3;

        private static final int ERROR_HANDLE_EOF = 38;

        private static final int OUTPUT_BUFFER_SIZE = 1024 * 1024;

        // ANCHOR: MFT Batch Statistics
        private record MftBatchStats(
                        long records,
                        long files,
                        long directories) {
        }

        // ANCHOR: Complete MFT Scan Result
        private record MftScanResult(
                        Map<Long, MftEntry> entries,
                        long records,
                        long files,
                        long directories,
                        long bytesRead,
                        int batches,
                        double seconds) {
        }

        // ANCHOR: Public Fast Scan Result
        public record FastScanResult(
                        Map<Long, MftEntry> entries,
                        Map<Long, Long> folderSizes,
                        String driveRoot,
                        long files,
                        long directories,
                        double mftSeconds,
                        double sizeSeconds,
                        double fallbackSeconds,
                        double aggregationSeconds) {
        }

        public static void main(String[] args) {

                // ANCHOR: Start Fast Scan
                FastScanResult result = scanVolume("C");

                if (result == null) {
                        return;
                }

                System.out.println("\n=== FAST SCANNER READY ===");
                System.out.printf("Files indexed: %,d%n", result.files());
                System.out.printf("Directories indexed: %,d%n", result.directories());

                // ANCHOR: Show Example Paths
                printExamplePaths(
                                result.entries(),
                                result.driveRoot(),
                                10);
        }

        // ANCHOR: Scan Volume
        public static FastScanResult scanVolume(
                        String driveLetter) {

                String normalizedDriveLetter = driveLetter.replace(":", "").trim().toUpperCase();
                String driveRoot = normalizedDriveLetter + ":\\";
                String volumePath = "\\\\.\\" + normalizedDriveLetter + ":";

                System.out.println(
                                "Opening volume: " + volumePath);

                HANDLE volumeHandle = Kernel32.INSTANCE.CreateFile(
                                volumePath,

                                WinNT.GENERIC_READ
                                                | WinNT.GENERIC_WRITE,

                                WinNT.FILE_SHARE_READ
                                                | WinNT.FILE_SHARE_WRITE,

                                null,

                                WinNT.OPEN_EXISTING,

                                0,

                                null);

                // ANCHOR: Check Volume Handle
                if (WinBase.INVALID_HANDLE_VALUE
                                .equals(volumeHandle)) {

                        int windowsError = Kernel32.INSTANCE.GetLastError();

                        System.out.println(
                                        "Failed to open volume.");

                        System.out.println(
                                        "Windows error: "
                                                        + windowsError);

                        return null;
                }

                System.out.println(
                                "SUCCESS: "
                                                + normalizedDriveLetter
                                                + ": volume opened!");

                try {

                        // ANCHOR: Query USN Journal
                        queryUsnJournal(volumeHandle);

                        // ANCHOR: Read Entire MFT
                        MftScanResult mftResult = readEntireMft(volumeHandle);

                        // ANCHOR: Read File Sizes
                        NtfsSizeReader.SizeStats sizeStats = NtfsSizeReader.readSizes(
                                        volumeHandle,
                                        mftResult.entries());

                        // ANCHOR: Java Size Fallback
                        FallbackSizeReader.FallbackStats fallbackStats = FallbackSizeReader.readMissingSizes(
                                        mftResult.entries(),
                                        driveRoot);

                        // ANCHOR: Build RAM Size Index
                        SizeIndex.Result sizeIndex = SizeIndex.build(

                                        mftResult.entries(),
                                        10);
                        System.out.println(
                                        "\n=== SIZE SCAN RESULTS ===");

                        System.out.printf(
                                        "Files checked: %,d%n",
                                        sizeStats.filesChecked());

                        System.out.printf(
                                        "Sizes found: %,d%n",
                                        sizeStats.sizesFound());

                        System.out.printf(
                                        "Sizes missing: %,d%n",
                                        sizeStats.sizesMissing());
                        System.out.printf(
                                        "Request failed: %,d%n",
                                        sizeStats.requestFailed());

                        System.out.printf(
                                        "Record mismatch: %,d%n",
                                        sizeStats.recordMismatch());

                        System.out.printf(
                                        "Invalid records: %,d%n",
                                        sizeStats.invalidRecord());

                        System.out.printf(
                                        "No DATA attribute: %,d%n",
                                        sizeStats.noDataAttribute());
                        System.out.printf(
                                        "Has ATTRIBUTE_LIST: %,d%n",
                                        sizeStats.hasAttributeList());

                        System.out.printf(
                                        "No ATTRIBUTE_LIST: %,d%n",
                                        sizeStats.noAttributeList());

                        System.out.printf(
                                        "Resolved via ATTRIBUTE_LIST: %,d%n",
                                        sizeStats.resolvedViaAttributeList());

                        System.out.printf(
                                        "ATTRIBUTE_LIST unresolved: %,d%n",
                                        sizeStats.attributeListUnresolved());

                        System.out.printf(
                                        "Nonresident ATTRIBUTE_LIST: %,d%n",
                                        sizeStats.nonResidentAttributeList());

                        System.out.printf(
                                        "Size read time: %.3f seconds%n",
                                        sizeStats.seconds());

                        System.out.printf(
                                        "RAM aggregation: %.3f seconds%n",
                                        sizeIndex.seconds());
                        System.out.println(
                                        "\n=== FALLBACK SIZE SCAN ===");

                        System.out.printf(
                                        "Files checked: %,d%n",
                                        fallbackStats.filesChecked());

                        System.out.printf(
                                        "Sizes recovered: %,d%n",
                                        fallbackStats.sizesFound());

                        System.out.printf(
                                        "Still missing: %,d%n",
                                        fallbackStats.sizesMissing());

                        System.out.printf(
                                        "Fallback time: %.3f seconds%n",
                                        fallbackStats.seconds());

                       

                        return new FastScanResult(
                                        mftResult.entries(),
                                        sizeIndex.folderSizes(),
                                        driveRoot,
                                        mftResult.files(),
                                        mftResult.directories(),
                                        mftResult.seconds(),
                                        sizeStats.seconds(),
                                        fallbackStats.seconds(),
                                        sizeIndex.seconds());

                } finally {

                        // ANCHOR: Close Volume Handle
                        Kernel32.INSTANCE.CloseHandle(
                                        volumeHandle);

                        System.out.println(
                                        "Volume handle closed.");
                }
        }

        // ANCHOR: Query USN Journal
        private static void queryUsnJournal(
                        HANDLE volumeHandle) {

                Memory outputBuffer = new Memory(56);

                IntByReference bytesReturned = new IntByReference();

                boolean success = Kernel32.INSTANCE.DeviceIoControl(
                                volumeHandle,

                                FSCTL_QUERY_USN_JOURNAL,

                                null,

                                0,

                                outputBuffer,

                                (int) outputBuffer.size(),

                                bytesReturned,

                                null);

                if (!success) {

                        int windowsError = Kernel32.INSTANCE.GetLastError();

                        System.out.println(
                                        "Failed to query USN journal.");

                        System.out.println(
                                        "Windows error: "
                                                        + windowsError);

                        return;
                }

                long journalId = outputBuffer.getLong(0);

                long firstUsn = outputBuffer.getLong(8);

                long nextUsn = outputBuffer.getLong(16);

                System.out.println(
                                "USN Journal found!");

                System.out.println(
                                "Journal ID: "
                                                + journalId);

                System.out.println(
                                "First USN: "
                                                + firstUsn);

                System.out.println(
                                "Next USN: "
                                                + nextUsn);
        }

        // ANCHOR: Create MFT Enumeration Input
        private static Memory createMftInput(
                        long startFileReferenceNumber) {

                Memory inputBuffer = new Memory(24);

                // StartFileReferenceNumber
                inputBuffer.setLong(
                                0,
                                startFileReferenceNumber);

                // LowUsn
                inputBuffer.setLong(
                                8,
                                0);

                // HighUsn
                inputBuffer.setLong(
                                16,
                                Long.MAX_VALUE);

                return inputBuffer;
        }

        // ANCHOR: Read Entire MFT
        private static MftScanResult readEntireMft(
                        HANDLE volumeHandle) {

                long startFileReferenceNumber = 0;

                int batchCount = 0;

                long totalBytesReturned = 0;
                long totalRecords = 0;
                long totalFiles = 0;
                long totalDirectories = 0;

                // ANCHOR: MFT Entry Index
                Map<Long, MftEntry> mftEntries = new HashMap<>();

                // Reuse one 1 MB native buffer
                Memory outputBuffer = new Memory(
                                OUTPUT_BUFFER_SIZE);

                IntByReference bytesReturned = new IntByReference();

                long startTime = System.nanoTime();

                while (true) {

                        // ANCHOR: Create Next MFT Request
                        Memory inputBuffer = createMftInput(
                                        startFileReferenceNumber);

                        bytesReturned.setValue(0);

                        // ANCHOR: Request MFT Batch
                        boolean success = Kernel32.INSTANCE
                                        .DeviceIoControl(
                                                        volumeHandle,

                                                        FSCTL_ENUM_USN_DATA,

                                                        inputBuffer,

                                                        (int) inputBuffer
                                                                        .size(),

                                                        outputBuffer,

                                                        (int) outputBuffer
                                                                        .size(),

                                                        bytesReturned,

                                                        null);

                        // ANCHOR: Handle MFT End Or Error
                        if (!success) {

                                int windowsError = Kernel32.INSTANCE
                                                .GetLastError();

                                if (windowsError == ERROR_HANDLE_EOF) {

                                        System.out.println(
                                                        "\nMFT enumeration complete.");

                                        break;
                                }

                                System.out.println(
                                                "\nMFT enumeration failed.");

                                System.out.println(
                                                "Windows error: "
                                                                + windowsError);

                                break;
                        }

                        int currentBytesReturned = bytesReturned.getValue();

                        if (currentBytesReturned <= 8) {
                                break;
                        }

                        batchCount++;

                        totalBytesReturned += currentBytesReturned;

                        // ANCHOR: Decode Current MFT Batch
                        MftBatchStats batchStats = analyzeMftBatch(
                                        outputBuffer,
                                        currentBytesReturned,
                                        mftEntries);

                        totalRecords += batchStats.records();

                        totalFiles += batchStats.files();

                        totalDirectories += batchStats.directories();

                        // First 8 bytes tell us where
                        // the next request should start.
                        long nextFileReferenceNumber = outputBuffer.getLong(0);

                        // ANCHOR: Prevent Infinite Loop
                        if (nextFileReferenceNumber == startFileReferenceNumber) {

                                System.out.println(
                                                "No further MFT progress.");

                                break;
                        }

                        // ANCHOR: Continue MFT Enumeration
                        startFileReferenceNumber = nextFileReferenceNumber;
                }

                long endTime = System.nanoTime();

                double seconds = (endTime - startTime)
                                / 1_000_000_000.0;

                return new MftScanResult(
                                mftEntries,
                                totalRecords,
                                totalFiles,
                                totalDirectories,
                                totalBytesReturned,
                                batchCount,
                                seconds);
        }

        // ANCHOR: Analyze MFT Batch
        private static MftBatchStats analyzeMftBatch(
                        Memory outputBuffer,
                        int bytesReturned,
                        Map<Long, MftEntry> mftEntries) {

                /*
                 * First 8 bytes are the continuation
                 * file reference number.
                 *
                 * Actual records begin after that.
                 */
                long recordOffset = 8;

                long recordCount = 0;
                long fileCount = 0;
                long directoryCount = 0;

                while (recordOffset + 8 <= bytesReturned) {

                        // ANCHOR: Read Record Header
                        int recordLength = outputBuffer.getInt(
                                        recordOffset);

                        if (recordLength <= 0) {
                                break;
                        }

                        long recordEnd = recordOffset
                                        + recordLength;

                        if (recordEnd > bytesReturned) {

                                break;
                        }

                        int majorVersion = outputBuffer.getShort(
                                        recordOffset + 4) & 0xFFFF;

                        recordCount++;

                        /*
                         * Current implementation handles
                         * USN_RECORD_V2.
                         */
                        if (majorVersion == 2
                                        && recordLength >= 60) {

                                // ANCHOR: Read File Attributes
                                int fileAttributes = outputBuffer.getInt(
                                                recordOffset
                                                                + 52);

                                boolean isDirectory = (fileAttributes & 0x10) != 0;

                                // ANCHOR: Read File IDs
                                long fileReferenceNumber = outputBuffer.getLong(
                                                recordOffset
                                                                + 8);

                                long parentFileReferenceNumber = outputBuffer.getLong(
                                                recordOffset
                                                                + 16);

                                // ANCHOR: Read Filename Information
                                int fileNameLength = outputBuffer.getShort(
                                                recordOffset
                                                                + 56)
                                                & 0xFFFF;

                                int fileNameOffset = outputBuffer.getShort(
                                                recordOffset
                                                                + 58)
                                                & 0xFFFF;

                                long fileNameStart = recordOffset
                                                + fileNameOffset;

                                long fileNameEnd = fileNameStart
                                                + fileNameLength;

                                // ANCHOR: Validate Filename Bounds
                                boolean validFileName = fileNameOffset >= 60
                                                && fileNameLength >= 0
                                                && fileNameStart >= recordOffset
                                                && fileNameEnd <= recordEnd;

                                if (validFileName) {

                                        // ANCHOR: Decode Filename
                                        byte[] fileNameBytes = outputBuffer
                                                        .getByteArray(
                                                                        fileNameStart,
                                                                        fileNameLength);

                                        String fileName = new String(
                                                        fileNameBytes,
                                                        StandardCharsets.UTF_16LE);

                                        // ANCHOR: Store MFT Entry
                                        mftEntries.put(
                                                        fileReferenceNumber,

                                                        new MftEntry(
                                                                        fileReferenceNumber,
                                                                        parentFileReferenceNumber,
                                                                        fileName,
                                                                        isDirectory));

                                        // ANCHOR: Count Entry Type
                                        if (isDirectory) {
                                                directoryCount++;
                                        } else {
                                                fileCount++;
                                        }
                                }
                        }

                        // ANCHOR: Move To Next MFT Record
                        recordOffset += recordLength;
                }

                return new MftBatchStats(
                                recordCount,
                                fileCount,
                                directoryCount);
        }

        // ANCHOR: Build Full Path
        public static String buildFullPath(
                        long fileId,
                        Map<Long, MftEntry> mftEntries,
                        String driveRoot) {

                List<String> pathParts = new ArrayList<>();

                Set<Long> visitedIds = new HashSet<>();

                long currentId = fileId;

                while (true) {

                        // Prevent damaged/circular parent chains
                        if (!visitedIds.add(currentId)) {
                                break;
                        }

                        MftEntry currentEntry = mftEntries.get(
                                        currentId);

                        if (currentEntry == null) {
                                break;
                        }

                        String currentName = currentEntry.name();

                        /*
                         * NTFS root can appear as "."
                         * so don't add that to the path.
                         */
                        if (currentName != null
                                        && !currentName.isBlank()
                                        && !currentName.equals(".")) {

                                pathParts.add(
                                                currentName);
                        }

                        // Root/self-parent protection
                        if (currentEntry.parentId() == currentEntry.fileId()) {

                                break;
                        }

                        currentId = currentEntry.parentId();
                }

                Collections.reverse(
                                pathParts);

                if (pathParts.isEmpty()) {
                        return driveRoot;
                }

                return driveRoot
                                + String.join(
                                                "\\",
                                                pathParts);
        }

        // ANCHOR: Print Scan Results
        private static void printScanResults(
                        MftScanResult result) {

                System.out.println(
                                "\n=== FAST SCAN BENCHMARK ===");

                System.out.println(
                                "Batches read: "
                                                + result.batches());

                System.out.println(
                                "MFT data read: "
                                                + result.bytesRead()
                                                + " bytes");

                System.out.println(
                                "MFT records: "
                                                + result.records());

                System.out.println(
                                "Files: "
                                                + result.files());

                System.out.println(
                                "Directories: "
                                                + result.directories());

                System.out.println(
                                "Entries stored: "
                                                + result.entries()
                                                                .size());

                System.out.printf(
                                "Time: %.3f seconds%n",
                                result.seconds());
        }

        // ANCHOR: Print Example Full Paths
        private static void printExamplePaths(
                        Map<Long, MftEntry> mftEntries,
                        String driveRoot,
                        int amount) {

                System.out.println(
                                "\n=== Example MFT Paths ===");

                int printed = 0;

                for (MftEntry entry : mftEntries.values()) {

                        String name = entry.name();

                        if (name == null
                                        || name.isBlank()) {

                                continue;
                        }

                        /*
                         * Skip most NTFS internal names
                         * in this little demo.
                         */
                        if (name.startsWith("$")) {
                                continue;
                        }

                        String fullPath = buildFullPath(
                                        entry.fileId(),
                                        mftEntries,
                                        driveRoot);

                        System.out.println(
                                        (entry.directory()
                                                        ? "[DIR]  "
                                                        : "[FILE] ")
                                                        + fullPath);

                        printed++;

                        if (printed >= amount) {
                                break;
                        }
                }
        }

        // ANCHOR: Folder Size Verification
        private static void printFolderSizeVerification(
                        Map<Long, MftEntry> entries,
                        Map<Long, Long> folderSizes,
                        String driveRoot,
                        String[] targetPaths) {

                System.out.println(
                                "\n=== FOLDER SIZE VERIFICATION ===");

                for (String targetPath : targetPaths) {

                        boolean found = false;

                        for (MftEntry entry : entries.values()) {

                                if (!entry.directory()) {
                                        continue;
                                }

                                String fullPath = buildFullPath(
                                                entry.fileId(),
                                                entries,
                                                driveRoot);

                                if (!normalizePath(fullPath)
                                                .equalsIgnoreCase(
                                                                normalizePath(targetPath))) {

                                        continue;
                                }

                                long sizeInBytes = folderSizes.getOrDefault(
                                                entry.fileId(),
                                                0L);

                                double sizeInGb = sizeInBytes
                                                / (1024.0 * 1024 * 1024);

                                System.out.printf(
                                                "%-35s %,d bytes  (%.2f GB)%n",
                                                targetPath,
                                                sizeInBytes,
                                                sizeInGb);

                                found = true;
                                break;
                        }

                        if (!found) {

                                System.out.println(
                                                targetPath
                                                                + " -> NOT FOUND");
                        }
                }
        }

        // ANCHOR: Normalize Windows Path
        private static String normalizePath(
                        String path) {

                String normalized = path.replace('/', '\\');

                while (normalized.endsWith("\\")
                                && normalized.length() > 3) {

                        normalized = normalized.substring(
                                        0,
                                        normalized.length() - 1);
                }

                return normalized;
        }
}