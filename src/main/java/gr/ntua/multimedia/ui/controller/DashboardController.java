package gr.ntua.multimedia.ui.controller;

import gr.ntua.multimedia.domain.Admin;
import gr.ntua.multimedia.domain.Author;
import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.service.MediaLabSystem;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

public class DashboardController {
    private final MediaLabSystem system;

    public DashboardController(MediaLabSystem system) { this.system = system; }

    public BorderPane createView(User user) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        Label title = new Label("Logged in as " + user.getUsername() + " (" + user.getRoleName() + ")");

        ListView<String> notifications = new ListView<>();
        notifications.getItems().addAll(system.getNotificationsOnLogin(user));

        TabPane tabs = new TabPane();
        tabs.getTabs().add(new Tab("Documents", new DocumentsController(system).createView(user)));
        if (user instanceof Admin admin) {
            tabs.getTabs().add(new Tab("Users", new AdminUsersController(system).createView(admin)));
            tabs.getTabs().add(new Tab("Categories", new AdminCategoriesController(system).createView(admin)));
        }

        root.setTop(new VBox(8, title, new Label("Notifications"), notifications));
        root.setCenter(tabs);
        return root;
    }
}