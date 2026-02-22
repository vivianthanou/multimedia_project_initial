package gr.ntua.multimedia.ui.controller;

import gr.ntua.multimedia.domain.Admin;
import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.service.MediaLabSystem;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class DashboardController {
    private final MediaLabSystem system;

    public DashboardController(MediaLabSystem system) {
        this.system = system;
    }

    public BorderPane createView(User user, Runnable onLogout) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Header: user info + logout
        Label title = new Label("Logged in as " + user.getUsername() + " (" + user.getRoleName() + ")");
        Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> onLogout.run());
        HBox header = new HBox(10, title, logoutBtn);

        // ===== Summary panel (Part 1 of assignment) =====
        Label categoriesCount = new Label();
        Label documentsCount = new Label();
        Label followedCount = new Label();

        Runnable refreshSummary = () -> {
            int totalCategories = system.getCategories().size();
            int totalDocuments = system.getDocuments().size();
            int followedDocs = user.getFollowedDocumentIds().size();

            categoriesCount.setText("Categories: " + totalCategories);
            documentsCount.setText("Documents: " + totalDocuments);
            followedCount.setText("Followed: " + followedDocs);
        };
        refreshSummary.run();

        HBox summaryBar = new HBox(20, categoriesCount, documentsCount, followedCount);
        summaryBar.setPadding(new Insets(6, 0, 6, 0));

        VBox topBox = new VBox(8, header, summaryBar);
        topBox.setPadding(new Insets(0, 0, 10, 0));

        // ===== Functional area (Part 2) =====
        TabPane tabs = new TabPane();

        // Pass onDataChanged callback to controllers
        Runnable onDataChanged = refreshSummary;

        tabs.getTabs().add(new Tab("Documents",
                wrapScrollable(new DocumentsController(system, onDataChanged).createView(user))
        ));

        if (user instanceof Admin admin) {
            tabs.getTabs().add(new Tab("Users",
                    wrapScrollable(new AdminUsersController(system, onDataChanged).createView(admin))
            ));
            tabs.getTabs().add(new Tab("Categories",
                    wrapScrollable(new AdminCategoriesController(system, onDataChanged).createView(admin))
            ));
        }

        root.setTop(topBox);
        root.setCenter(tabs);
        return root;
    }

    private Node wrapScrollable(Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setFitToHeight(false);
        sp.setPannable(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }
}
