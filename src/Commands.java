import java.io.File;
import java.util.Scanner;

public class Commands {

    // ANCHOR: Handle Delete Command
    public static boolean handle(
            String input,
            Scanner scanner) {

        if (!input.toLowerCase().startsWith("delete ")) {
            return false;
        }

        String path = input.substring(7).trim();

        File item = new File(path);

        DeleteResult result = ConsoleDelete.confirmAndDelete(
                item,
                scanner);

        System.out.println(
                "Delete result: " + result);

        if (result == DeleteResult.SUCCESS) {

            // ANCHOR: Refresh Active Scanner After Delete
            DiskScanner.invalidateCache(item);

            File drive = item.toPath()
                    .getRoot()
                    .toFile();

            System.out.println(
                    "\n=== Updated Drive Storage ===");

            ConsoleDisplay.displayDriveInformation(drive);
        }

        return true;
    }
}