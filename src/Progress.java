public interface Progress {

    void update(
            String path,
            long scanned
    );
}