public class ConsoleProgress
        implements Progress {

    @Override
    public void update(
            String path,
            long scanned) {

        if (scanned % 5000 == 0) {

            System.out.printf(
                    "Scanning... %,d folders scanned%n",
                    scanned
            );
        }
    }
}