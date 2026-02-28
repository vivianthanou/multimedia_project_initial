package gr.ntua.multimedia.ui;

import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.persistence.MediaLabStorage;
import gr.ntua.multimedia.service.MediaLabSystem;
import gr.ntua.multimedia.ui.controller.DashboardController;
import gr.ntua.multimedia.ui.controller.LoginController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.nio.file.Path;

public class MainApp extends Application {

    private MediaLabStorage storage;
    private MediaLabSystem system;

    @Override
    public void start(Stage stage) {
        storage = new MediaLabStorage(Path.of("medialab"));
        system = storage.loadOrCreateDefault();

        showLogin(stage);
    }

    private void showLogin(Stage stage) {
        LoginController loginController = new LoginController(system);
        Scene loginScene = new Scene(
                loginController.createView(user -> showDashboard(stage, user)),
                420, 200
        );
        stage.setTitle("MediaLab Documents");
        stage.setScene(loginScene);
        stage.show();
    }

    private void showDashboard(Stage stage, User user) {
        DashboardController dashboardController = new DashboardController(system);

        Scene dashboardScene = new Scene(
                dashboardController.createView(user, () -> {
                    storage.save(system);
                    showLogin(stage);
                }),
                900, 640
        );
        stage.setScene(dashboardScene);

        String msg = system.buildLoginPopupMessage(user);
        if (msg != null) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Notification");
                alert.setHeaderText(null);
                alert.setContentText(msg);
                alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                alert.showAndWait();
            });
        }
    }

    @Override
    public void stop() {
        if (storage != null && system != null) {
            storage.save(system);
        }
    }
}