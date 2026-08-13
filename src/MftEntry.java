public class MftEntry {

    private final long fileId;
    private final long parentId;
    private final String name;
    private final boolean directory;

    // ANCHOR: File Size
    private long sizeInBytes = -1;

    public MftEntry(
            long fileId,
            long parentId,
            String name,
            boolean directory) {

        this.fileId = fileId;
        this.parentId = parentId;
        this.name = name;
        this.directory = directory;
    }

    public long fileId() {
        return fileId;
    }

    public long parentId() {
        return parentId;
    }

    public String name() {
        return name;
    }

    public boolean directory() {
        return directory;
    }

    // ANCHOR: Get File Size
    public long sizeInBytes() {
        return sizeInBytes;
    }

    // ANCHOR: Set File Size
    public void setSizeInBytes(long sizeInBytes) {
        this.sizeInBytes = sizeInBytes;
    }
}