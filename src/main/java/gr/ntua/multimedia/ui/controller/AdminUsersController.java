package gr.ntua.multimedia.ui.controller;

import gr.ntua.multimedia.domain.Admin;
import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.service.MediaLabSystem;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.HashSet;

public class AdminUsersController {
    private final MediaLabSystem system;

    public AdminUsersController(MediaLabSystem system) { this.system = system; }

    public VBox createView(Admin admin) {
        ListView<User> users = new ListView<>();
        users.getItems().setAll(system.listUsers(admin));

        TextField first = new TextField(); first.setPromptText("First name");
        TextField last = new TextField(); last.setPromptText("Last name");
        TextField role = new TextField(); role.setPromptText("Role (SIMPLE/AUTHOR/ADMIN)");
        TextField username = new TextField(); username.setPromptText("Username");
        PasswordField password = new PasswordField(); password.setPromptText("Password");
        Button add = new Button("Add user");
        add.setOnAction(e -> {
            system.addUser(admin, first.getText(), last.getText(), role.getText(), new HashSet<>(), username.getText(), password.getText());
            users.getItems().setAll(system.listUsers(admin));
        });

        Button delete = new Button("Delete selected");
        delete.setOnAction(e -> {
            User u = users.getSelectionModel().getSelectedItem();
            if (u != null) {
                system.deleteUser(admin, u.getUsername());
                users.getItems().setAll(system.listUsers(admin));
            }
        });

        VBox root = new VBox(8, users, first, last, role, username, password, add, delete);
        root.setPadding(new Insets(10));
        return root;
    }
}