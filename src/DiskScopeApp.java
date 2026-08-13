import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class DiskScopeApp extends Application {

    public static void main(String[] args) {
        launch(DiskScopeApp.class, args);
    }

    @Override
    public void start(Stage stage) {
        MainView mainView = new MainView();

        Scene scene = new Scene(mainView, 1260, 780);
        scene.getStylesheets().add(
                getClass().getResource("/styles/diskscope.css").toExternalForm());

        stage.setTitle("DiskScope");
        stage.setMinWidth(1080);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.show();

        mainView.start();
    }
}
