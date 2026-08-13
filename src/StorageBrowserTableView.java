import java.util.Locale;
import java.util.function.Consumer;

import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class StorageBrowserTableView extends VBox {

    private final TableView<StorageItem> table = new TableView<>();
    private Consumer<StorageItem> detailsHandler = ignored -> { };
    private Consumer<StorageItem> deleteHandler = ignored -> { };

    public StorageBrowserTableView() {
        getStyleClass().add("table-module");
        buildTable();
        getChildren().add(table);
        VBox.setVgrow(table, Priority.ALWAYS);
    }

    public void setItems(java.util.List<StorageItem> items) {
        table.setItems(FXCollections.observableArrayList(items));
    }

    public void setDetailsHandler(Consumer<StorageItem> detailsHandler) {
        this.detailsHandler = detailsHandler;
    }

    public void setDeleteHandler(Consumer<StorageItem> deleteHandler) {
        this.deleteHandler = deleteHandler;
    }

    private void buildTable() {
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Run a scan to browse files and folders."));

        TableColumn<StorageItem, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        name.setPrefWidth(480);
        name.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                StorageItem row = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || item == null || row == null) {
                    setGraphic(null);
                    return;
                }

                Label kind = new Label(row.directory() ? "Folder" : "File");
                kind.getStyleClass().addAll("badge", row.directory() ? "folder-kind" : "file-kind");
                Label label = new Label(item);
                label.getStyleClass().add("table-name-label");
                HBox wrapper = new HBox(10, kind, label);
                wrapper.setFillHeight(false);
                setGraphic(wrapper);
            }
        });

        TableColumn<StorageItem, Number> size = new TableColumn<>("Size");
        size.setCellValueFactory(data -> new SimpleLongProperty(data.getValue().sizeInBytes()));
        size.setPrefWidth(120);
        size.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatSize(item.longValue()));
            }
        });

        TableColumn<StorageItem, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().typeLabel()));
        type.setPrefWidth(130);
        type.setCellFactory(ignored -> badgeCell("type-badge"));

        TableColumn<StorageItem, String> recommendation = new TableColumn<>("Recommendation");
        recommendation.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().recommendationLabel()));
        recommendation.setPrefWidth(150);
        recommendation.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                StorageItem row = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || item == null || row == null) {
                    setGraphic(null);
                    return;
                }

                setGraphic(badge(item, row.badgeStyle()));
            }
        });

        TableColumn<StorageItem, StorageItem> action = new TableColumn<>("Action");
        action.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        action.setPrefWidth(260);
        action.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(StorageItem row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }

                Button details = new Button("Details");
                details.getStyleClass().addAll("mini-button", "secondary-action");
                details.setOnAction(ignored -> detailsHandler.accept(row));

                Button recycle = new Button(row.canDelete() ? "Move to Recycle Bin" : "Protected");
                recycle.getStyleClass().addAll("mini-button", row.canDelete() ? "danger-action" : "protected-action");
                recycle.setDisable(!row.canDelete());
                recycle.setOnAction(ignored -> deleteHandler.accept(row));

                HBox actions = new HBox(8, details, recycle);
                setGraphic(actions);
            }
        });

        table.getColumns().addAll(name, size, type, recommendation, action);
        table.setRowFactory(ignored -> {
            TableRow<StorageItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    detailsHandler.accept(row.getItem());
                }
            });
            return row;
        });
    }

    private TableCell<StorageItem, String> badgeCell(String styleClass) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                setGraphic(badge(item, styleClass));
            }
        };
    }

    private Label badge(String text, String styleClass) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll("badge", styleClass);
        return badge;
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
                : String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }
}
