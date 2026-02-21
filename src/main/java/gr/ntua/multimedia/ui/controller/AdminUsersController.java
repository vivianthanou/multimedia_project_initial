package gr.ntua.multimedia.ui.controller;

import gr.ntua.multimedia.domain.Admin;
import gr.ntua.multimedia.domain.Category;
import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.exception.ValidationException;
import gr.ntua.multimedia.service.MediaLabSystem;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.HashSet;
import java.util.Set;

public class AdminUsersController {
    private final MediaLabSystem system;

    public AdminUsersController(MediaLabSystem system) {
        this.system = system;
    }

    public VBox createView(Admin admin) {
        // Users list
        ListView<User> users = new ListView<>();
        users.getItems().setAll(system.listUsers(admin));
        users.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }

                String categoriesText = item.getAllowedCategoryIds().stream()
                        .map(catId -> {
                            Category c = system.getCategories().get(catId);
                            return c != null ? c.getName() : ("<deleted:" + catId + ">");
                        })
                        .sorted()
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("-");

                setText(item.getUsername() + " (" + item.getRoleName() + ") - " +
                        item.getFirstName() + " " + item.getLastName() +
                        " | categories: " + categoriesText);
            }
        });
        VBox.setVgrow(users, Priority.ALWAYS);

        // ✅ Category multi-select list (3Α)
        ListView<Category> categories = new ListView<>();
        categories.getItems().setAll(system.getCategories().values());
        categories.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        categories.setPrefHeight(140);
        categories.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        Label categoriesHint = new Label("Allowed categories (select 1+ for SIMPLE/AUTHOR)");

        TextField first = new TextField();
        first.setPromptText("First name");

        TextField last = new TextField();
        last.setPromptText("Last name");

        TextField role = new TextField();
        role.setPromptText("Role (SIMPLE/AUTHOR/ADMIN)");

        TextField username = new TextField();
        username.setPromptText("Username");

        PasswordField password = new PasswordField();
        password.setPromptText("Password");

        Button add = new Button("Add user");
        add.setOnAction(e -> {
            try {
                String roleValue = role.getText() == null ? "" : role.getText().trim().toUpperCase();

                // ✅ collect selected categories (multiple)
                Set<String> allowedCategoryIds = new HashSet<>();
                for (Category c : categories.getSelectionModel().getSelectedItems()) {
                    allowedCategoryIds.add(c.getId());
                }

                // UI-level validation (service also enforces it)
                if (!"ADMIN".equals(roleValue) && allowedCategoryIds.isEmpty()) {
                    showError("Please select at least one category for SIMPLE/AUTHOR users.");
                    return;
                }

                system.addUser(
                        admin,
                        first.getText(),
                        last.getText(),
                        roleValue,
                        allowedCategoryIds,
                        username.getText(),
                        password.getText()
                );

                // Refresh list
                users.getItems().setAll(system.listUsers(admin));

                // Clear inputs
                first.clear();
                last.clear();
                role.clear();
                username.clear();
                password.clear();
                categories.getSelectionModel().clearSelection();

            } catch (ValidationException ex) {
                showError(ex.getMessage());
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });

        Button delete = new Button("Delete selected");
        delete.setOnAction(e -> {
            User u = users.getSelectionModel().getSelectedItem();
            if (u != null) {
                try {
                    system.deleteUser(admin, u.getUsername());
                    users.getItems().setAll(system.listUsers(admin));
                } catch (RuntimeException ex) {
                    showError(ex.getMessage());
                }
            }
        });

        Label formatHint = new Label("Format: username (role) - first name last name | categories");
        formatHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");

        VBox root = new VBox(
                8,
                new Label("Users"),
                formatHint,
                users,
                new Separator(),
                new Label("Create user"),
                first, last, role,
                categoriesHint,
                categories,
                username, password,
                add,
                delete
        );
        root.setPadding(new Insets(10));
        return root;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Validation error");
        alert.setHeaderText(null);
        alert.setContentText(message == null || message.isBlank() ? "Unknown error" : message);
        alert.showAndWait();
    }
}
