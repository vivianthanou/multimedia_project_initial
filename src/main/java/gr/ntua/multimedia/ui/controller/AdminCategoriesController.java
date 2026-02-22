package gr.ntua.multimedia.ui.controller;

import gr.ntua.multimedia.domain.Admin;
import gr.ntua.multimedia.domain.Category;
import gr.ntua.multimedia.service.MediaLabSystem;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Optional;

public class AdminCategoriesController {
    private final MediaLabSystem system;
    private final Runnable onDataChanged;

    public AdminCategoriesController(MediaLabSystem system, Runnable onDataChanged) {
        this.system = system;
        this.onDataChanged = (onDataChanged != null) ? onDataChanged : () -> {};
    }

    public VBox createView(Admin admin) {
        Label formatHint = new Label("Format: Category Name | Category ID ");
        formatHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");

        ListView<Category> categories = new ListView<>();
        categories.getItems().setAll(system.getCategories().values());
        VBox.setVgrow(categories, Priority.ALWAYS);

        // ✅ Per-row actions (Rename / Delete)
        categories.setCellFactory(lv -> new ListCell<>() {
            private final Label text = new Label();
            private final Button renameBtn = new Button("Rename");
            private final Button deleteBtn = new Button("Delete");
            private final Region spacer = new Region();
            private final HBox row = new HBox(10, text, spacer, renameBtn, deleteBtn);

            {
                HBox.setHgrow(spacer, Priority.ALWAYS);

                renameBtn.setOnAction(e -> {
                    Category c = getItem();
                    if (c == null) return;

                    // Rename dialog: Save / Cancel
                    TextField input = new TextField(c.getName());
                    input.setPrefColumnCount(25);

                    Dialog<String> dialog = new Dialog<>();
                    dialog.setTitle("Rename Category");
                    dialog.setHeaderText("Rename category (ID: " + c.getId() + ")");
                    ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
                    dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
                    dialog.getDialogPane().setContent(input);

                    dialog.setResultConverter(bt -> bt == saveType ? input.getText() : null);

                    Optional<String> result = dialog.showAndWait();
                    if (result.isEmpty() || result.get() == null) return; // Cancel

                    String newName = result.get().trim();
                    if (newName.isBlank()) {
                        showError("Category name cannot be blank.");
                        return;
                    }
                    if (newName.equals(c.getName())) {
                        // no changes
                        return;
                    }

                    try {
                        system.renameCategory(admin, c.getId(), newName);
                        categories.getItems().setAll(system.getCategories().values());
                        onDataChanged.run();
                    } catch (RuntimeException ex) {
                        showError(ex.getMessage());
                    }
                });

                deleteBtn.setOnAction(e -> {
                    Category c = getItem();
                    if (c == null) return;

                    // Confirm delete: Delete / Cancel
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("Delete Category");
                    confirm.setHeaderText("Delete category '" + c.getName() + "'?");
                    confirm.setContentText("This will delete the category and all documents in it.\nContinue?");

                    ButtonType deleteType = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
                    ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                    confirm.getButtonTypes().setAll(deleteType, cancelType);

                    Optional<ButtonType> choice = confirm.showAndWait();
                    if (choice.isEmpty() || choice.get() != deleteType) {
                        return; // Cancel
                    }

                    try {
                        system.deleteCategory(admin, c.getId());
                        categories.getItems().setAll(system.getCategories().values());
                        onDataChanged.run();
                    } catch (RuntimeException ex) {
                        showError(ex.getMessage());
                    }
                });
            }

            @Override
            protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    text.setText(item.getName() + " | " + item.getId());
                    setGraphic(row);
                }
            }
        });


        TextField name = new TextField();
        name.setPromptText("Category name");

        Button add = new Button("Add category");
        add.setOnAction(e -> {
            try {
                system.addCategory(admin, name.getText());
                categories.getItems().setAll(system.getCategories().values());
                name.clear();
                onDataChanged.run();
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });

        VBox root = new VBox(8, new Label("Categories"), formatHint, categories,new Label("Create New Category"), name, add);
        root.setPadding(new Insets(10));
        return root;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message == null || message.isBlank() ? "Unknown error" : message);
        alert.showAndWait();
    }
}
