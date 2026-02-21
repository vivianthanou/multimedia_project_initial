package gr.ntua.multimedia.ui;

import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.persistence.JsonStorage;
import gr.ntua.multimedia.service.MediaLabSystem;
import gr.ntua.multimedia.ui.controller.DashboardController;
import gr.ntua.multimedia.ui.controller.LoginController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;

public class MainApp extends Application {
    private JsonStorage storage;
    private MediaLabSystem system;

    @Override
    public void start(Stage stage) {
        storage = new JsonStorage(Path.of("data", "medialab.json"));
        system = storage.loadOrCreateDefault();

        showLogin(stage);
    }

    private void showLogin(Stage stage) {
        LoginController loginController = new LoginController(system);
        Scene loginScene = new Scene(loginController.createView(user -> showDashboard(stage, user)), 420, 200);
        stage.setTitle("MediaLab DMS");
        stage.setScene(loginScene);
        stage.show();
    }

    private void showDashboard(Stage stage, User user) {
        DashboardController dashboardController = new DashboardController(system);

        Scene dashboardScene = new Scene(
                dashboardController.createView(user, () -> {
                    // SAVE on logout
                    storage.save(system);
                    showLogin(stage);
                }),
                900, 640
        );
        stage.setScene(dashboardScene);
    }


    @Override
    public void stop() {
        if (storage != null && system != null) {
            storage.save(system);
        }
    }
}