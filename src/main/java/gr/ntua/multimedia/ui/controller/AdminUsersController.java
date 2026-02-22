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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import java.util.Optional;

import java.util.HashSet;
import java.util.Set;

public class AdminUsersController {
    private final MediaLabSystem system;

    private final Runnable onDataChanged;

    public AdminUsersController(MediaLabSystem system, Runnable onDataChanged) {
        this.system = system;
        this.onDataChanged = (onDataChanged != null) ? onDataChanged : () -> {};
    }

    public VBox createView(Admin admin) {
        // Users list
        ListView<User> users = new ListView<>();
        users.getItems().setAll(system.listUsers(admin));
        users.setCellFactory(lv -> new ListCell<>() {
            private final Label text = new Label();
            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final Region spacer = new Region();
            private final HBox row = new HBox(10, text, spacer, editBtn, deleteBtn);

            {
                HBox.setHgrow(spacer, Priority.ALWAYS);

                editBtn.setOnAction(e -> {
                    User target = getItem();
                    if (target == null) return;

                    // Popup: select categories (multi-select)
                    ListView<Category> categoryList = new ListView<>();
                    categoryList.getItems().setAll(system.getCategories().values());
                    categoryList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

                    // show category name
                    categoryList.setCellFactory(x -> new ListCell<>() {
                        @Override
                        protected void updateItem(Category item, boolean empty) {
                            super.updateItem(item, empty);
                            setText(empty || item == null ? null : item.getName());
                        }
                    });

                    // pre-select current categories
                    for (int i = 0; i < categoryList.getItems().size(); i++) {
                        Category c = categoryList.getItems().get(i);
                        if (target.getAllowedCategoryIds().contains(c.getId())) {
                            categoryList.getSelectionModel().select(i);
                        }
                    }

                    Dialog<Set<String>> dialog = new Dialog<>();
                    dialog.setTitle("Edit User Categories");
                    dialog.setHeaderText("User: " + target.getUsername() + " (" + target.getRoleName() + ")\nSelect allowed categories");

                    ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
                    dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

                    dialog.getDialogPane().setContent(categoryList);

                    dialog.setResultConverter(bt -> {
                        if (bt != saveType) return null;
                        Set<String> ids = new HashSet<>();
                        for (Category c : categoryList.getSelectionModel().getSelectedItems()) {
                            ids.add(c.getId());
                        }
                        return ids;
                    });

                    Optional<Set<String>> result = dialog.showAndWait();
                    if (result.isEmpty() || result.get() == null) return; // cancel

                    try {
                        system.updateUserCategories(admin, target.getUsername(), result.get());
                        users.getItems().setAll(system.listUsers(admin));
                        onDataChanged.run();
                    } catch (RuntimeException ex) {
                        showError(ex.getMessage());
                    }
                });

                deleteBtn.setOnAction(e -> {
                    User u = getItem();
                    if (u == null) return;
                    try {
                        system.deleteUser(admin, u.getUsername());
                        users.getItems().setAll(system.listUsers(admin));
                        onDataChanged.run();
                    } catch (RuntimeException ex) {
                        showError(ex.getMessage());
                    }
                });
            }

            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
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

                text.setText(item.getUsername() + " (" + item.getRoleName() + ") - " +
                        item.getFirstName() + " " + item.getLastName() +
                        " | categories: " + categoriesText);

                setGraphic(row);
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
                onDataChanged.run();

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
                    onDataChanged.run();

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
