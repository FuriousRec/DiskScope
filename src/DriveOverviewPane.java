import java.util.Locale;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;

public class DriveOverviewPane extends VBox {

    private final DonutMeter donutMeter = new DonutMeter();
    private final Label percentLabel = new Label("--");
    private final Label driveLabel = new Label("No drive");
    private final Label totalLabel = new Label("--");
    private final Label usedLabel = new Label("--");
    private final Label freeLabel = new Label("--");
    private final Label indexedLabel = new Label("Indexed: --");
    private final Label blockedLabel = new Label("Blocked paths: --");
    private final Label scanTimeLabel = new Label("Last scan: --");

    public DriveOverviewPane() {
        getStyleClass().add("stats-panel");

        Label title = new Label("STORAGE");
        title.getStyleClass().add("panel-kicker");

        StackPane donutShell = new StackPane();
        donutShell.getStyleClass().add("donut-shell");
        VBox center = new VBox(2);
        center.setAlignment(Pos.CENTER);
        percentLabel.getStyleClass().add("primary-metric");
        driveLabel.getStyleClass().add("metric-caption");
        center.getChildren().addAll(percentLabel, driveLabel);
        donutShell.getChildren().addAll(donutMeter, center);

        totalLabel.getStyleClass().add("mini-stat-number");
        usedLabel.getStyleClass().add("mini-stat-number");
        freeLabel.getStyleClass().add("mini-stat-number");
        indexedLabel.getStyleClass().add("stat-line");
        blockedLabel.getStyleClass().add("stat-line");
        scanTimeLabel.getStyleClass().add("stat-line");

        getChildren().addAll(
                title,
                donutShell,
                statCard("Total", totalLabel),
                statCard("Used", usedLabel),
                statCard("Free", freeLabel),
                scanTimeLabel,
                indexedLabel,
                blockedLabel,
                legend());
    }

    public void update(NavigationController.ScanSnapshot snapshot) {
        updateDrive(snapshot.drive());
        indexedLabel.setText(String.format(
                Locale.ROOT,
                "Indexed: %,d files / %,d folders",
                snapshot.indexedFiles(),
                snapshot.indexedFolders()));
        blockedLabel.setText("Blocked paths: " + snapshot.inaccessibleFolders());
        scanTimeLabel.setText(String.format(Locale.ROOT, "Scan time: %.2fs", snapshot.scanSeconds()));
    }

    public void updateDrive(java.io.File drive) {
        if (drive == null) {
            return;
        }

        long total = drive.getTotalSpace();
        long free = drive.getFreeSpace();
        long used = Math.max(0, total - free);
        double usedPercent = total <= 0 ? 0 : (double) used / total;

        percentLabel.setText(String.format(Locale.ROOT, "%.0f%%", usedPercent * 100));
        driveLabel.setText("used on " + drive.getAbsolutePath());
        totalLabel.setText(formatSize(total));
        usedLabel.setText(formatSize(used));
        freeLabel.setText(formatSize(free));
        donutMeter.setUsedPercent(usedPercent);
    }

    private VBox statCard(String name, Label valueLabel) {
        VBox card = new VBox(4);
        card.getStyleClass().add("mini-stat-card");
        Label title = new Label(name);
        title.getStyleClass().add("mini-stat-name");
        card.getChildren().addAll(title, valueLabel);
        return card;
    }

    private HBox legend() {
        HBox legend = new HBox(10);
        legend.getStyleClass().add("legend-row");
        legend.getChildren().addAll(
                legendLabel("User", "cyan-dot"),
                legendLabel("Apps", "pink-dot"),
                legendLabel("System", "red-dot"));
        return legend;
    }

    private Label legendLabel(String text, String dotClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("legend-label", dotClass);
        return label;
    }

    private String formatSize(long bytes) {
        double value = Math.max(0, bytes);
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;

        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }

        if (unit <= 1) {
            return String.format(Locale.ROOT, "%.0f %s", value, units[unit]);
        }

        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    private static final class DonutMeter extends Pane {

        private final Arc freeArc = new Arc(86, 86, 70, 70, 90, -360);
        private final Arc usedArc = new Arc(86, 86, 70, 70, 90, 0);

        private DonutMeter() {
            setMinSize(172, 172);
            setPrefSize(172, 172);
            setMaxSize(172, 172);

            freeArc.getStyleClass().add("donut-free");
            usedArc.getStyleClass().add("donut-used");

            for (Arc arc : java.util.List.of(freeArc, usedArc)) {
                arc.setType(ArcType.OPEN);
                arc.setFill(null);
                arc.setStrokeWidth(18);
            }

            getChildren().addAll(freeArc, usedArc);
        }

        private void setUsedPercent(double percent) {
            double clamped = Math.max(0, Math.min(1, percent));
            usedArc.setLength(-360 * clamped);
        }
    }
}
