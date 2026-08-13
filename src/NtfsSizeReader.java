import java.util.Map;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;

public class NtfsSizeReader {

    // ANCHOR: NTFS Control Codes
    private static final int FSCTL_GET_NTFS_VOLUME_DATA =
            0x00090064;

    private static final int FSCTL_GET_NTFS_FILE_RECORD =
            0x00090068;

    // ANCHOR: NTFS Attribute Types
    private static final int ATTRIBUTE_LIST =
            0x20;

    private static final int DATA_ATTRIBUTE =
            0x80;

    // Low 48 bits identify the MFT segment number.
    private static final long MFT_INDEX_MASK =
            0x0000FFFFFFFFFFFFL;

    // ANCHOR: File Record Error Codes
    private static final int RECORD_OK = 0;
    private static final int RECORD_REQUEST_FAILED = -1;
    private static final int RECORD_MISMATCH = -2;
    private static final int RECORD_INVALID = -3;

    // ANCHOR: Size Scan Statistics
    public record SizeStats(
            long filesChecked,
            long sizesFound,
            long sizesMissing,
            long requestFailed,
            long recordMismatch,
            long invalidRecord,
            long noDataAttribute,
            long hasAttributeList,
            long noAttributeList,
            long resolvedViaAttributeList,
            long attributeListUnresolved,
            long nonResidentAttributeList,
            double seconds) {
    }

    // ANCHOR: NTFS Volume Information
    private record VolumeInfo(
            int bytesPerSector,
            int bytesPerFileRecord) {
    }

    // ANCHOR: Attribute List Search Result
    private record AttributeListTarget(
            boolean hasAttributeList,
            boolean nonResident,
            long targetMftIndex) {
    }

    // ANCHOR: File Record Read Result
    private record FileRecordResult(
            int status,
            int recordLength) {
    }

    // ANCHOR: Read All File Sizes
    public static SizeStats readSizes(
            HANDLE volumeHandle,
            Map<Long, MftEntry> entries) {

        VolumeInfo volumeInfo =
                readVolumeInfo(volumeHandle);

        if (volumeInfo == null) {

            return new SizeStats(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        System.out.println(
                "\n=== NTFS FILE SIZE SCAN ==="
        );

        System.out.println(
                "Bytes per MFT record: "
                        + volumeInfo.bytesPerFileRecord()
        );

        // ANCHOR: Reusable Native Buffers
        Memory inputBuffer =
                new Memory(8);

        Memory outputBuffer =
                new Memory(
                        12L
                                + volumeInfo.bytesPerFileRecord()
                                + 64
                );

        IntByReference bytesReturned =
                new IntByReference();

        byte[] baseRecord =
                new byte[
                        volumeInfo.bytesPerFileRecord()
                ];

        byte[] extensionRecord =
                new byte[
                        volumeInfo.bytesPerFileRecord()
                ];

        // ANCHOR: Scan Counters
        long filesChecked = 0;
        long sizesFound = 0;
        long sizesMissing = 0;

        long requestFailed = 0;
        long recordMismatch = 0;
        long invalidRecord = 0;

        long noDataAttribute = 0;

        long hasAttributeList = 0;
        long noAttributeList = 0;

        long resolvedViaAttributeList = 0;
        long attributeListUnresolved = 0;
        long nonResidentAttributeList = 0;

        long startTime =
                System.nanoTime();

        // ANCHOR: Scan Every File Entry
        for (MftEntry entry : entries.values()) {

            if (entry.directory()) {
                continue;
            }

            filesChecked++;

            long requestedMftIndex =
                    entry.fileId()
                            & MFT_INDEX_MASK;

            // ANCHOR: Read Base File Record
            FileRecordResult baseResult =
                    readFileRecord(
                            volumeHandle,
                            requestedMftIndex,
                            inputBuffer,
                            outputBuffer,
                            bytesReturned,
                            baseRecord
                    );

            // ANCHOR: Handle Base Record Failure
            if (baseResult.status()
                    != RECORD_OK) {

                if (baseResult.status()
                        == RECORD_REQUEST_FAILED) {

                    requestFailed++;

                } else if (baseResult.status()
                        == RECORD_MISMATCH) {

                    recordMismatch++;

                } else {

                    invalidRecord++;
                }

                sizesMissing++;
                continue;
            }

            int baseRecordLength =
                    baseResult.recordLength();

            // ANCHOR: Apply Base Record Fixups
            applyFixups(
                    baseRecord,
                    baseRecordLength,
                    volumeInfo.bytesPerSector()
            );

            // ANCHOR: Try DATA In Base Record
            long size =
                    findFileSize(
                            baseRecord,
                            baseRecordLength
                    );

            if (size >= 0) {

                entry.setSizeInBytes(size);
                sizesFound++;

                continue;
            }

            /*
             * No normal unnamed $DATA was found
             * in the base record.
             */
            noDataAttribute++;

            // ANCHOR: Find DATA Through ATTRIBUTE_LIST
            AttributeListTarget listTarget =
                    findAttributeListDataTarget(
                            baseRecord,
                            baseRecordLength,
                            requestedMftIndex
                    );

            if (!listTarget.hasAttributeList()) {

                noAttributeList++;
                sizesMissing++;

                continue;
            }

            hasAttributeList++;

            // Nonresident ATTRIBUTE_LIST requires
            // a separate raw-cluster reader.
            if (listTarget.nonResident()) {

                nonResidentAttributeList++;
                attributeListUnresolved++;
                sizesMissing++;

                continue;
            }

            if (listTarget.targetMftIndex() < 0) {

                attributeListUnresolved++;
                sizesMissing++;

                continue;
            }

            // ANCHOR: Read Extension MFT Record
            FileRecordResult extensionResult =
                    readFileRecord(
                            volumeHandle,
                            listTarget.targetMftIndex(),
                            inputBuffer,
                            outputBuffer,
                            bytesReturned,
                            extensionRecord
                    );

            if (extensionResult.status()
                    != RECORD_OK) {

                attributeListUnresolved++;
                sizesMissing++;

                continue;
            }

            int extensionRecordLength =
                    extensionResult.recordLength();

            // ANCHOR: Apply Extension Record Fixups
            applyFixups(
                    extensionRecord,
                    extensionRecordLength,
                    volumeInfo.bytesPerSector()
            );

            // ANCHOR: Read DATA From Extension Record
            long extensionSize =
                    findFileSize(
                            extensionRecord,
                            extensionRecordLength
                    );

            if (extensionSize >= 0) {

                entry.setSizeInBytes(
                        extensionSize
                );

                resolvedViaAttributeList++;
                sizesFound++;

            } else {

                attributeListUnresolved++;
                sizesMissing++;
            }

            // ANCHOR: Size Scan Progress
            if (filesChecked % 100_000 == 0) {

                System.out.printf(
                        "Size scan... %,d files%n",
                        filesChecked
                );
            }
        }

        long endTime =
                System.nanoTime();

        double seconds =
                (endTime - startTime)
                        / 1_000_000_000.0;

        return new SizeStats(
                filesChecked,
                sizesFound,
                sizesMissing,
                requestFailed,
                recordMismatch,
                invalidRecord,
                noDataAttribute,
                hasAttributeList,
                noAttributeList,
                resolvedViaAttributeList,
                attributeListUnresolved,
                nonResidentAttributeList,
                seconds
        );
    }

    // ANCHOR: Read One Exact MFT Record
    private static FileRecordResult readFileRecord(
            HANDLE volumeHandle,
            long requestedMftIndex,
            Memory inputBuffer,
            Memory outputBuffer,
            IntByReference bytesReturned,
            byte[] destination) {

        inputBuffer.setLong(
                0,
                requestedMftIndex
        );

        bytesReturned.setValue(0);

        boolean success =
                Kernel32.INSTANCE.DeviceIoControl(
                        volumeHandle,
                        FSCTL_GET_NTFS_FILE_RECORD,
                        inputBuffer,
                        (int) inputBuffer.size(),
                        outputBuffer,
                        (int) outputBuffer.size(),
                        bytesReturned,
                        null
                );

        if (!success) {

            return new FileRecordResult(
                    RECORD_REQUEST_FAILED,
                    0
            );
        }

        long returnedFileReference =
                outputBuffer.getLong(0);

        long returnedMftIndex =
                returnedFileReference
                        & MFT_INDEX_MASK;

        /*
         * Windows is allowed to return the
         * nearest lower in-use MFT record,
         * therefore we must verify it.
         */
        if (returnedMftIndex
                != requestedMftIndex) {

            return new FileRecordResult(
                    RECORD_MISMATCH,
                    0
            );
        }

        int recordLength =
                outputBuffer.getInt(8);

        if (recordLength <= 0
                || recordLength
                > destination.length) {

            return new FileRecordResult(
                    RECORD_INVALID,
                    0
            );
        }

        outputBuffer.read(
                12,
                destination,
                0,
                recordLength
        );

        return new FileRecordResult(
                RECORD_OK,
                recordLength
        );
    }

    // ANCHOR: Find DATA Target In ATTRIBUTE_LIST
    private static AttributeListTarget
            findAttributeListDataTarget(
                    byte[] record,
                    int recordLength,
                    long baseMftIndex) {

        if (!hasValidFileSignature(
                record,
                recordLength)) {

            return new AttributeListTarget(
                    false,
                    false,
                    -1
            );
        }

        int attributeOffset =
                readUnsignedShort(
                        record,
                        20
                );

        // ANCHOR: Walk Base Record Attributes
        while (attributeOffset + 24
                <= recordLength) {

            int attributeType =
                    readInt(
                            record,
                            attributeOffset
                    );

            if (attributeType == -1) {
                break;
            }

            int attributeLength =
                    readInt(
                            record,
                            attributeOffset + 4
                    );

            if (attributeLength <= 0
                    || attributeOffset
                    + attributeLength
                    > recordLength) {

                break;
            }

            if (attributeType
                    == ATTRIBUTE_LIST) {

                int formCode =
                        record[
                                attributeOffset + 8
                        ] & 0xFF;

                /*
                 * We handle the resident
                 * ATTRIBUTE_LIST first.
                 */
                if (formCode != 0) {

                    return new AttributeListTarget(
                            true,
                            true,
                            -1
                    );
                }

                int valueLength =
                        readInt(
                                record,
                                attributeOffset + 16
                        );

                int valueOffset =
                        readUnsignedShort(
                                record,
                                attributeOffset + 20
                        );

                int listStart =
                        attributeOffset
                                + valueOffset;

                int listEnd =
                        listStart
                                + valueLength;

                if (valueLength <= 0
                        || listStart
                        < attributeOffset
                        || listEnd
                        > attributeOffset
                        + attributeLength
                        || listEnd
                        > recordLength) {

                    return new AttributeListTarget(
                            true,
                            false,
                            -1
                    );
                }

                // ANCHOR: Walk ATTRIBUTE_LIST Entries
                int listEntryOffset =
                        listStart;

                while (listEntryOffset + 26
                        <= listEnd) {

                    int listAttributeType =
                            readInt(
                                    record,
                                    listEntryOffset
                            );

                    int listRecordLength =
                            readUnsignedShort(
                                    record,
                                    listEntryOffset + 4
                            );

                    int attributeNameLength =
                            record[
                                    listEntryOffset + 6
                            ] & 0xFF;

                    if (listRecordLength < 26
                            || listEntryOffset
                            + listRecordLength
                            > listEnd) {

                        break;
                    }

                    long lowestVcn =
                            readLong(
                                    record,
                                    listEntryOffset + 8
                            );

                    long segmentReference =
                            readLong(
                                    record,
                                    listEntryOffset + 16
                            );

                    long segmentMftIndex =
                            segmentReference
                                    & MFT_INDEX_MASK;

                    /*
                     * We want the normal unnamed
                     * $DATA attribute and the first
                     * extent (LowestVcn == 0).
                     */
                    if (listAttributeType
                            == DATA_ATTRIBUTE
                            && attributeNameLength == 0
                            && lowestVcn == 0
                            && segmentMftIndex
                            != baseMftIndex) {

                        return new AttributeListTarget(
                                true,
                                false,
                                segmentMftIndex
                        );
                    }

                    listEntryOffset +=
                            listRecordLength;
                }

                return new AttributeListTarget(
                        true,
                        false,
                        -1
                );
            }

            attributeOffset +=
                    attributeLength;
        }

        return new AttributeListTarget(
                false,
                false,
                -1
        );
    }

    // ANCHOR: Read NTFS Volume Information
    private static VolumeInfo readVolumeInfo(
            HANDLE volumeHandle) {

        Memory outputBuffer =
                new Memory(96);

        IntByReference bytesReturned =
                new IntByReference();

        boolean success =
                Kernel32.INSTANCE.DeviceIoControl(
                        volumeHandle,
                        FSCTL_GET_NTFS_VOLUME_DATA,
                        null,
                        0,
                        outputBuffer,
                        (int) outputBuffer.size(),
                        bytesReturned,
                        null
                );

        if (!success) {

            System.out.println(
                    "Could not read NTFS volume information."
            );

            return null;
        }

        int bytesPerSector =
                outputBuffer.getInt(40);

        int bytesPerFileRecord =
                outputBuffer.getInt(48);

        if (bytesPerSector <= 0
                || bytesPerFileRecord <= 0) {

            System.out.println(
                    "Invalid NTFS volume information."
            );

            return null;
        }

        return new VolumeInfo(
                bytesPerSector,
                bytesPerFileRecord
        );
    }

    // ANCHOR: Apply NTFS Update Sequence Fixups
    private static void applyFixups(
            byte[] record,
            int recordLength,
            int bytesPerSector) {

        if (recordLength < 8) {
            return;
        }

        int updateSequenceOffset =
                readUnsignedShort(
                        record,
                        4
                );

        int updateSequenceCount =
                readUnsignedShort(
                        record,
                        6
                );

        if (updateSequenceCount <= 1) {
            return;
        }

        if (updateSequenceOffset < 0
                || updateSequenceOffset
                + updateSequenceCount * 2
                > recordLength) {

            return;
        }

        int updateSequenceNumber =
                readUnsignedShort(
                        record,
                        updateSequenceOffset
                );

        for (int sector = 1;
             sector < updateSequenceCount;
             sector++) {

            int sectorEnd =
                    sector
                            * bytesPerSector
                            - 2;

            int replacementOffset =
                    updateSequenceOffset
                            + sector * 2;

            if (sectorEnd < 0
                    || sectorEnd + 2
                    > recordLength
                    || replacementOffset + 2
                    > recordLength) {

                break;
            }

            int currentValue =
                    readUnsignedShort(
                            record,
                            sectorEnd
                    );

            if (currentValue
                    == updateSequenceNumber) {

                record[sectorEnd] =
                        record[replacementOffset];

                record[sectorEnd + 1] =
                        record[
                                replacementOffset + 1
                        ];
            }
        }
    }

    // ANCHOR: Find Unnamed DATA Attribute
    private static long findFileSize(
            byte[] record,
            int recordLength) {

        if (!hasValidFileSignature(
                record,
                recordLength)) {

            return -1;
        }

        int attributeOffset =
                readUnsignedShort(
                        record,
                        20
                );

        // ANCHOR: Walk NTFS Attributes
        while (attributeOffset + 16
                <= recordLength) {

            int attributeType =
                    readInt(
                            record,
                            attributeOffset
                    );

            if (attributeType == -1) {
                break;
            }

            int attributeLength =
                    readInt(
                            record,
                            attributeOffset + 4
                    );

            if (attributeLength <= 0
                    || attributeOffset
                    + attributeLength
                    > recordLength) {

                break;
            }

            int formCode =
                    record[
                            attributeOffset + 8
                    ] & 0xFF;

            int nameLength =
                    record[
                            attributeOffset + 9
                    ] & 0xFF;

            // ANCHOR: Normal Unnamed DATA
            if (attributeType
                    == DATA_ATTRIBUTE
                    && nameLength == 0) {

                // ANCHOR: Resident DATA
                if (formCode == 0) {

                    if (attributeOffset + 20
                            <= recordLength) {

                        return Integer.toUnsignedLong(
                                readInt(
                                        record,
                                        attributeOffset + 16
                                )
                        );
                    }
                }

                // ANCHOR: Nonresident DATA
                if (formCode == 1) {

                    if (attributeOffset + 56
                            <= recordLength) {

                        long lowestVcn =
                                readLong(
                                        record,
                                        attributeOffset + 16
                                );

                        /*
                         * FileSize is valid in
                         * the first extent.
                         */
                        if (lowestVcn == 0) {

                            long fileSize =
                                    readLong(
                                            record,
                                            attributeOffset + 48
                                    );

                            if (fileSize >= 0) {
                                return fileSize;
                            }
                        }
                    }
                }
            }

            attributeOffset +=
                    attributeLength;
        }

        return -1;
    }

    // ANCHOR: Validate MFT FILE Signature
    private static boolean hasValidFileSignature(
            byte[] record,
            int recordLength) {

        if (recordLength < 24) {
            return false;
        }

        return record[0] == 'F'
                && record[1] == 'I'
                && record[2] == 'L'
                && record[3] == 'E';
    }

    // ANCHOR: Little Endian Helpers
    private static int readUnsignedShort(
            byte[] data,
            int offset) {

        return (data[offset] & 0xFF)
                | ((data[offset + 1]
                & 0xFF) << 8);
    }

    private static int readInt(
            byte[] data,
            int offset) {

        return (data[offset] & 0xFF)
                | ((data[offset + 1]
                & 0xFF) << 8)
                | ((data[offset + 2]
                & 0xFF) << 16)
                | ((data[offset + 3]
                & 0xFF) << 24);
    }

    private static long readLong(
            byte[] data,
            int offset) {

        long value = 0;

        for (int byteIndex = 0;
             byteIndex < 8;
             byteIndex++) {

            value |=
                    (data[offset + byteIndex]
                            & 0xFFL)
                            << (byteIndex * 8);
        }

        return value;
    }
}