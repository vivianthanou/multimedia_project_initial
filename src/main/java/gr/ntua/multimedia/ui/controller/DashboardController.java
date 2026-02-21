package gr.ntua.multimedia.ui.controller;

import gr.ntua.multimedia.domain.Admin;
import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.service.MediaLabSystem;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

public class DashboardController {
    private final MediaLabSystem system;

    public DashboardController(MediaLabSystem system) {
        this.system = system;
    }

    public BorderPane createView(User user, Runnable onLogout) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        Label title = new Label("Logged in as " + user.getUsername() + " (" + user.getRoleName() + ")");
        Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> onLogout.run());

        HBox header = new HBox(10, title, logoutBtn);
        // Notifications area (limit height so it doesn't eat the whole window)
        ListView<String> notifications = new ListView<>();
        notifications.getItems().setAll(system.getNotificationsOnLogin(user));
        notifications.setPrefHeight(140);
        notifications.setMaxHeight(180);

        VBox topBox = new VBox(8, header, new Label("Notifications"), notifications);
        topBox.setPadding(new Insets(0, 0, 10, 0)); // space below top area

        TabPane tabs = new TabPane();

        // Wrap each tab content in a ScrollPane so long pages can scroll
        tabs.getTabs().add(new Tab("Documents", wrapScrollable(new DocumentsController(system).createView(user))));

        if (user instanceof Admin admin) {
            tabs.getTabs().add(new Tab("Users", wrapScrollable(new AdminUsersController(system).createView(admin))));
            tabs.getTabs().add(new Tab("Categories", wrapScrollable(new AdminCategoriesController(system).createView(admin))));
        }

        // Let the center take all remaining space
        root.setTop(topBox);
        root.setCenter(tabs);

        return root;
    }

    private Node wrapScrollable(Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setFitToHeight(false); // we want vertical scrolling if needed
        sp.setPannable(true);
        // Optional: make scrolling smoother for big forms
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }
}
