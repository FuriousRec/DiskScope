import java.io.File;
import java.util.List;
import java.util.Scanner;

public class Main {

        public static void main(String[] args) {

                // ANCHOR: Scan Progress
                DiskScanner.setProgress(
                                new ConsoleProgress());

                // ANCHOR: Detect Drives
                File[] drives = File.listRoots();

                if (drives == null
                                || drives.length == 0) {

                        System.out.println(
                                        "No drives found.");

                        return;
                }

                // ANCHOR: Display Drive Information
                for (File drive : drives) {
                        ConsoleDisplay.displayDriveInformation(drive);
                }

                // ANCHOR: Analyze Main Drive
                File rootDrive = drives[0];

                // ANCHOR: Initialize Primary Scanner
                DiskScanner.initialize(rootDrive);

                System.out.println(
                                "\n=== Top-Level Folders ===");

                List<FolderInfo> topLevelFolders =
                                DiskScanner.scanFolders(rootDrive);

                ConsoleDisplay.displayFolders(
                                rootDrive,
                                topLevelFolders);

                ConsoleDisplay.displayScanSummary();

                // ANCHOR: User Input
                Scanner scanner = new Scanner(System.in);

                while (true) {

                        System.out.print(
                                        "\nEnter folder path to analyze "
                                                        + "(or type exit/refresh): ");

                        String folderPath = scanner.nextLine().trim();

                        // ANCHOR: Exit
                        if (folderPath.equalsIgnoreCase("exit")) {
                                break;
                        }

                        // ANCHOR: Refresh
                        if (folderPath.equalsIgnoreCase("refresh")) {

                                DiskScanner.refresh();

                                System.out.println(
                                                "Scanner refreshed.");

                                continue;
                        }

                        // ANCHOR: Commands
                        if (Commands.handle(
                                        folderPath,
                                        scanner)) {

                                continue;
                        }

                        // ANCHOR: Validate Folder
                        File selectedFolder =
                                        new File(folderPath);

                        if (!selectedFolder.exists()) {

                                System.out.println(
                                                "Error: Folder does not exist.");

                                continue;
                        }

                        if (!selectedFolder.isDirectory()) {

                                System.out.println(
                                                "Error: Please enter a folder path.");

                                continue;
                        }

                        // ANCHOR: Analyze Selected Folder
                        System.out.println(
                                        "\n=== Contents of "
                                                        + selectedFolder.getPath()
                                                        + " ===");

                        // ANCHOR: Display Subfolders
                        List<FolderInfo> subfolders =
                                        DiskScanner.scanFolders(
                                                        selectedFolder);

                        System.out.println(
                                        "\n=== Folders ===");

                        ConsoleDisplay.displayFolders(
                                        selectedFolder,
                                        subfolders);

                        // ANCHOR: Display Direct Files
                        List<FileInfo> directFiles =
                                        DiskScanner.scanDirectFiles(
                                                        selectedFolder);

                        System.out.println(
                                        "\n=== Files Directly Inside This Folder ===");

                        ConsoleDisplay.displayFiles(
                                        directFiles);

                        // ANCHOR: Find Largest Files
                        List<FileInfo> largestFiles =
                                        DiskScanner.scanLargestFiles(
                                                        selectedFolder,
                                                        10);

                        ConsoleDisplay.displayLargestFiles(
                                        selectedFolder,
                                        largestFiles);
                }

                // ANCHOR: Close Program
                scanner.close();

                System.out.println(
                                "DiskScope closed.");
        }
}