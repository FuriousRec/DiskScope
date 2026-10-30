import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

public class MainView extends StackPane implements NavigationController.Listener {

    private enum ViewMode {
        BROWSE,
        LARGEST,
        CLEANUP
    }

    private final NavigationController navigationController = new NavigationController(this);

    private final BorderPane layout = new BorderPane();
    private final DriveOverviewPane driveOverviewPane = new DriveOverviewPane();
    private final StorageBrowserTableView storageTable = new StorageBrowserTableView();

    private final ComboBox<File> drivePicker = new ComboBox<>();
    private final FlowPane breadcrumbBar = new FlowPane(6, 6);
    private final Label statusLabel = new Label("Idle");
    private final Label scannerModeLabel = new Label("Scanner: --");
    private final Label tableCaption = new Label("Run a scan to browse storage.");
    private final TextField resultFilter = new TextField();
    private final Label propertyBadge = new Label("PROPERTY OF\nFURIOUSREC");
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final PacmanLoader pacmanLoader = new PacmanLoader();
    private final VBox consoleLines = new VBox(8);
    private final StackPane loadingOverlay = new StackPane();

    private final Button fullScanButton = new Button("Scan");
    private final Button refreshButton = new Button("Refresh view");
    private final Button backButton = new Button("Back");
    private final Button upButton = new Button("Up");
    private final Button browseTab = new Button("Browse");
    private final Button largestTab = new Button("Largest Files");
    private final Button cleanupTab = new Button("Cleanup");
    private final Button exportResultsButton = new Button("Export CSV");

    private NavigationController.ScanSnapshot currentSnapshot;
    private ViewMode viewMode = ViewMode.BROWSE;

    public MainView() {
        getStyleClass().add("app-root");
        buildLayout();
        configureActions();
    }

    public void start() {
        File[] drives = File.listRoots();
        drivePicker.getItems().setAll(drives);

        if (drives.length > 0) {
            drivePicker.getSelectionModel().select(0);
            driveOverviewPane.updateDrive(drives[0]);
            renderBreadcrumb(drives[0]);
            log("Ready. No scan started yet.");
        }
    }

    @Override
    public void scanStarted(File targetFolder) {
        setBusy(true);
        renderBreadcrumb(targetFolder);
        log("Scan started: " + targetFolder.getAbsolutePath());
    }

    @Override
    public void scanProgress(String message) {
        statusLabel.setText(message);
    }

    @Override
    public void scanCompleted(NavigationController.ScanSnapshot snapshot) {
        setBusy(false);
        currentSnapshot = snapshot;
        renderSnapshot(snapshot);
        log("Scan complete: " + snapshot.browseItems().size() + " visible items.");
    }

    @Override
    public void scanFailed(Throwable throwable) {
        setBusy(false);
        statusLabel.setText("Scan failed");
        log("Scan failed: " + throwable.getClass().getSimpleName());
        showWarning("Scan failed", throwable.getMessage());
    }

    private void buildLayout() {
        layout.getStyleClass().add("window-shell");
        layout.setTop(createTopBar());
        layout.setCenter(createContent());
        layout.setRight(driveOverviewPane);

        loadingOverlay.getStyleClass().add("loading-overlay");
        loadingOverlay.setVisible(false);
        loadingOverlay.setManaged(false);

        VBox loadingCard = new VBox(14);
        loadingCard.getStyleClass().add("loading-card");
        loadingCard.setAlignment(Pos.CENTER);
        progressIndicator.getStyleClass().add("scan-progress");
        Label loadingTitle = new Label("Scanning storage");
        loadingTitle.getStyleClass().add("loading-title");
        loadingCard.getChildren().addAll(pacmanLoader, progressIndicator, loadingTitle, statusLabel);
        loadingOverlay.getChildren().add(loadingCard);

        propertyBadge.getStyleClass().add("floating-property-badge");
        propertyBadge.setMouseTransparent(true);
        StackPane.setAlignment(propertyBadge, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(propertyBadge, new Insets(0, 28, 24, 0));

        getChildren().addAll(layout, loadingOverlay, propertyBadge);
    }

    private HBox createTopBar() {
        HBox topBar = new HBox(12);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label brandMark = new Label("DS");
        brandMark.getStyleClass().add("brand-mark");

        VBox titleBox = new VBox(2);
        Label title = new Label("DiskScope");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("storage radar / cleanup control");
        subtitle.getStyleClass().add("app-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        drivePicker.getStyleClass().add("drive-picker");
        drivePicker.setCellFactory(ignored -> driveCell());
        drivePicker.setButtonCell(driveCell());
        drivePicker.setOnAction(ignored -> {
            File selectedDrive = drivePicker.getValue();
            if (selectedDrive != null) {
                driveOverviewPane.updateDrive(selectedDrive);
                renderBreadcrumb(selectedDrive);
            }
        });

        fullScanButton.getStyleClass().addAll("action-button", "primary-action");
        scannerModeLabel.getStyleClass().add("scanner-pill");
        refreshButton.getStyleClass().addAll("action-button", "secondary-action");
        backButton.getStyleClass().addAll("action-button", "ghost-action");
        upButton.getStyleClass().addAll("action-button", "ghost-action");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topBar.getChildren().addAll(
                brandMark,
                titleBox,
                scannerModeLabel,
                spacer,
                backButton,
                upButton,
                drivePicker,
                fullScanButton,
                refreshButton);

        return topBar;
    }

    private VBox createContent() {
        VBox content = new VBox(14);
        content.getStyleClass().add("main-surface");

        HBox pathRow = new HBox(10);
        pathRow.setAlignment(Pos.CENTER_LEFT);
        breadcrumbBar.getStyleClass().add("breadcrumb-bar");

        Button browseButton = new Button("Browse folder");
        browseButton.getStyleClass().addAll("action-button", "secondary-action");
        browseButton.setOnAction(ignored -> chooseFolder());

        HBox.setHgrow(breadcrumbBar, Priority.ALWAYS);
        pathRow.getChildren().addAll(breadcrumbBar, browseButton);

        HBox tabRow = new HBox(8);
        tabRow.getStyleClass().add("tab-row");
        browseTab.getStyleClass().addAll("view-tab", "selected-tab");
        largestTab.getStyleClass().add("view-tab");
        cleanupTab.getStyleClass().add("view-tab");
        tableCaption.getStyleClass().add("section-caption");
        resultFilter.setPromptText("Filter results...");
        resultFilter.getStyleClass().add("result-filter");
        resultFilter.textProperty().addListener(
                (observable, previous, current) -> renderActiveTable());
        exportResultsButton.getStyleClass().addAll("action-button", "secondary-action");
        exportResultsButton.setDisable(true);

        HBox tabSpacer = new HBox();
        HBox.setHgrow(tabSpacer, Priority.ALWAYS);
        tabRow.getChildren().addAll(
                browseTab, largestTab, cleanupTab, tabSpacer,
                tableCaption, resultFilter, exportResultsButton);

        VBox.setVgrow(storageTable, Priority.ALWAYS);

        content.getChildren().addAll(pathRow, tabRow, storageTable, createConsole());
        return content;
    }

    private TitledPane createConsole() {
        VBox console = new VBox(10);
        console.getStyleClass().add("console-box");
        consoleLines.getStyleClass().add("console-lines");
        console.getChildren().add(consoleLines);

        TitledPane scanDetails = new TitledPane("Scan details", console);
        scanDetails.getStyleClass().add("scan-details");
        scanDetails.setExpanded(false);
        return scanDetails;
    }

    private void configureActions() {
        storageTable.setDetailsHandler(this::showDetails);
        storageTable.setDeleteHandler(this::confirmDelete);

        browseTab.setOnAction(ignored -> switchMode(ViewMode.BROWSE));
        largestTab.setOnAction(ignored -> switchMode(ViewMode.LARGEST));
        cleanupTab.setOnAction(ignored -> switchMode(ViewMode.CLEANUP));
        exportResultsButton.setOnAction(ignored -> exportVisibleResults());

        fullScanButton.setOnAction(ignored -> {
            File selectedDrive = drivePicker.getValue();
            if (selectedDrive != null) {
                navigationController.scanDrive(selectedDrive);
            }
        });

        refreshButton.setOnAction(ignored -> navigationController.refresh());
        backButton.setOnAction(ignored -> navigationController.back());
        upButton.setOnAction(ignored -> navigationController.up());
    }

    private void renderSnapshot(NavigationController.ScanSnapshot snapshot) {
        if (!snapshot.drive().equals(drivePicker.getValue())) {
            drivePicker.getSelectionModel().select(snapshot.drive());
        }

        scannerModeLabel.setText(snapshot.scannerMode());
        renderBreadcrumb(snapshot.targetFolder());
        statusLabel.setText("Ready");
        fullScanButton.setText("Rescan drive");

        driveOverviewPane.update(snapshot);
        renderActiveTable();
    }

    private void switchMode(ViewMode newMode) {
        viewMode = newMode;
        browseTab.getStyleClass().remove("selected-tab");
        largestTab.getStyleClass().remove("selected-tab");
        cleanupTab.getStyleClass().remove("selected-tab");

        Button selected = switch (newMode) {
            case BROWSE -> browseTab;
            case LARGEST -> largestTab;
            case CLEANUP -> cleanupTab;
        };
        selected.getStyleClass().add("selected-tab");
        renderActiveTable();
    }

    private void renderActiveTable() {
        if (currentSnapshot == null) {
            storageTable.setItems(List.of());
            exportResultsButton.setDisable(true);
            return;
        }

        List<StorageItem> items = switch (viewMode) {
            case BROWSE -> currentSnapshot.browseItems();
            case LARGEST -> currentSnapshot.largestItems();
            case CLEANUP -> currentSnapshot.browseItems()
                    .stream()
                    .filter(StorageItem::cleanupCandidate)
                    .toList();
        };

        String caption = switch (viewMode) {
            case BROWSE -> "Folders and direct files in this location.";
            case LARGEST -> "Largest safe cleanup candidates only.";
            case CLEANUP -> "Protected/system items are hidden from cleanup.";
        };

        List<StorageItem> filteredItems = StorageItemFilter.filter(items, resultFilter.getText());
        if (!resultFilter.getText().isBlank()) {
            caption += " " + filteredItems.size() + " match"
                    + (filteredItems.size() == 1 ? "." : "es.");
        }

        tableCaption.setText(caption);
        storageTable.setItems(filteredItems);
        exportResultsButton.setDisable(filteredItems.isEmpty());
    }

    private void showDetails(StorageItem item) {
        if (item.directory()) {
            navigationController.openFolder(item.file());
            return;
        }

        openItem(item.file());
    }

    private void renderBreadcrumb(File folder) {
        breadcrumbBar.getChildren().clear();

        if (folder == null) {
            Label empty = new Label("No folder selected");
            empty.getStyleClass().add("path-label");
            breadcrumbBar.getChildren().add(empty);
            return;
        }

        Path path = folder.toPath().toAbsolutePath().normalize();
        Path root = path.getRoot();

        if (root != null) {
            addBreadcrumbSegment(root.toFile(), root.toString().replace("\\", ""));
        }

        Path current = root;
        for (Path segment : path) {
            current = current == null ? segment : current.resolve(segment);

            Label divider = new Label(">");
            divider.getStyleClass().add("breadcrumb-divider");
            breadcrumbBar.getChildren().add(divider);

            addBreadcrumbSegment(current.toFile(), segment.toString());
        }
    }

    private void addBreadcrumbSegment(File target, String text) {
        Button segment = new Button(text);
        segment.getStyleClass().add("breadcrumb-button");
        segment.setOnAction(ignored -> navigationController.openFolder(target));
        breadcrumbBar.getChildren().add(segment);
    }

    private void exportVisibleResults() {
        List<StorageItem> visibleItems = storageTable.items();
        if (visibleItems.isEmpty()) {
            showWarning("Nothing to export", "The current view has no visible rows.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export visible DiskScope results");
        chooser.setInitialFileName("diskscope-results.csv");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV files", "*.csv"));

        File selected = chooser.showSaveDialog(getScene().getWindow());
        if (selected == null) {
            return;
        }

        try {
            StorageItemCsvExporter.write(selected.toPath(), visibleItems);
            log("Exported " + visibleItems.size() + " visible rows to " + selected.getName());
            showInfo(
                    "Export complete",
                    visibleItems.size() + " visible row"
                            + (visibleItems.size() == 1 ? " was" : "s were")
                            + " exported to:\n"
                            + selected.getAbsolutePath());
        } catch (IOException exception) {
            showWarning("Export failed", exception.getMessage());
        }
    }

    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose folder to scan");

        File selectedDrive = drivePicker.getValue();
        if (selectedDrive != null && selectedDrive.exists()) {
            chooser.setInitialDirectory(selectedDrive);
        }

        File selected = chooser.showDialog(getScene().getWindow());
        if (selected != null) {
            navigationController.openFolder(selected);
        }
    }

    private void confirmDelete(StorageItem item) {
        if (!item.canDelete() || !DeleteManager.canDelete(item.file())) {
            showWarning("Delete blocked", "Protected system items cannot be deleted from DiskScope.");
            return;
        }

        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType move = new ButtonType("Move to Recycle Bin", ButtonBar.ButtonData.OK_DONE);

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "", cancel, move);
        confirm.setTitle("Move to Recycle Bin");
        confirm.setHeaderText("Move \"" + item.name() + "\" to Recycle Bin?");
        confirm.setContentText(
                "Full path: " + item.file().getAbsolutePath()
                        + "\nSize: " + formatSize(item.sizeInBytes())
                        + "\nCategory: " + displayStorageType(item.storageType())
                        + "\n\nThis item will be moved to the Windows Recycle Bin.");

        confirm.showAndWait().ifPresent(button -> {
            if (button != move) {
                return;
            }

            DeleteResult result = DeleteManager.moveToRecycleBin(item.file());

            if (result == DeleteResult.SUCCESS) {
                DiskScanner.invalidateCache(item.file());
                navigationController.refresh();
                showInfo("Moved to Recycle Bin", "\"" + item.name() + "\" was moved to the Recycle Bin.");
            } else {
                showWarning("Recycle failed", result.name());
            }
        });
    }

    private void openItem(File item) {
        try {
            java.awt.Desktop.getDesktop().open(item.isDirectory() ? item : item.getParentFile());
        } catch (Exception exception) {
            showWarning("Could not open item", exception.getMessage());
        }
    }

    private void setBusy(boolean busy) {
        loadingOverlay.setVisible(busy);
        loadingOverlay.setManaged(busy);
        fullScanButton.setDisable(busy);
        refreshButton.setDisable(busy);
        backButton.setDisable(busy);
        upButton.setDisable(busy);
        exportResultsButton.setDisable(busy || storageTable.items().isEmpty());

        if (busy) {
            pacmanLoader.play();
        } else {
            pacmanLoader.pause();
        }
    }

    private void log(String message) {
        Label line = new Label("> " + message);
        line.getStyleClass().add("console-line");
        consoleLines.getChildren().add(0, line);

        while (consoleLines.getChildren().size() > 7) {
            consoleLines.getChildren().remove(consoleLines.getChildren().size() - 1);
        }
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null || message.isBlank() ? "No details available." : message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String displayStorageType(StorageType storageType) {
        return switch (storageType) {
            case PROTECTED_SYSTEM -> "System / Protected";
            case APPLICATION -> "Application";
            case DEV_TOOL -> "Developer Tool";
            case USER_DATA -> "User Data";
            case TEMPORARY_DATA -> "Temporary Data";
            case RECYCLE_BIN -> "Recycle Bin";
            case UNKNOWN -> "Unknown";
        };
    }

    private String formatSize(long bytes) {
        double value = Math.max(0, bytes);
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;

        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }

        return unit <= 1
                ? String.format(Locale.ROOT, "%.0f %s", value, units[unit])
                : String.format(Locale.ROOT, "%.2f %s", value, units[unit]);
    }

    private ListCell<File> driveCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getAbsolutePath());
            }
        };
    }

    private static final class PacmanLoader extends javafx.scene.canvas.Canvas {

        private double mouth = 34;
        private double offset = 0;
        private final javafx.animation.Timeline timeline;

        private PacmanLoader() {
            super(230, 44);
            getStyleClass().add("pacman-loader");
            timeline = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.millis(145), ignored -> {
                        mouth = mouth == 34 ? 8 : 34;
                        offset = (offset + 8) % 32;
                        draw();
                    }));
            timeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
            draw();
        }

        private void play() {
            timeline.play();
        }

        private void pause() {
            timeline.pause();
        }

        private void draw() {
            javafx.scene.canvas.GraphicsContext graphics = getGraphicsContext2D();
            graphics.clearRect(0, 0, getWidth(), getHeight());
            graphics.setFill(javafx.scene.paint.Color.web("#141728"));
            graphics.fillRoundRect(0, 6, getWidth(), 32, 16, 16);

            graphics.setFill(javafx.scene.paint.Color.web("#57f1ff"));
            for (int index = 0; index < 7; index++) {
                double x = 72 + index * 28 - offset;
                if (x > 48 && x < getWidth() - 14) {
                    graphics.fillOval(x, 19, 5, 5);
                }
            }

            graphics.setFill(javafx.scene.paint.Color.web("#ff4f8b"));
            graphics.fillArc(18, 8, 30, 30, mouth / 2, 360 - mouth, javafx.scene.shape.ArcType.ROUND);
        }
    }
}
