import java.io.File;
import java.util.Scanner;

public class ConsoleDelete {

    // ANCHOR: Confirm Delete
    public static DeleteResult confirmAndDelete(
            File item,
            Scanner scanner) {

        if (item == null || !item.exists()) {
            return DeleteResult.NOT_FOUND;
        }

        if (!DeleteManager.canDelete(item)) {
            return DeleteResult.BLOCKED;
        }
        System.out.println("\nMove to Recycle Bin:");
        System.out.println(item.getAbsolutePath());

        System.out.print("Type DELETE to confirm: ");

        String confirmation = scanner.nextLine();

        if (!confirmation.equals("DELETE")) {
            return DeleteResult.CANCELLED;
        }

        return DeleteManager.moveToRecycleBin(item);
    }
}