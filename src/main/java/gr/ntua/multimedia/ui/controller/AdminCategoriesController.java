package gr.ntua.multimedia.ui.controller;

import gr.ntua.multimedia.domain.Admin;
import gr.ntua.multimedia.domain.Category;
import gr.ntua.multimedia.service.MediaLabSystem;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class AdminCategoriesController {
    private final MediaLabSystem system;

    public AdminCategoriesController(MediaLabSystem system) { this.system = system; }

    public VBox createView(Admin admin) {
        ListView<Category> categories = new ListView<>();
        categories.getItems().setAll(system.getCategories().values());

        TextField name = new TextField(); name.setPromptText("Category name");
        Button add = new Button("Add category");
        add.setOnAction(e -> {
            system.addCategory(admin, name.getText());
            categories.getItems().setAll(system.getCategories().values());
        });

        TextField rename = new TextField(); rename.setPromptText("New name");
        Button renameBtn = new Button("Rename selected");
        renameBtn.setOnAction(e -> {
            Category c = categories.getSelectionModel().getSelectedItem();
            if (c != null) {
                system.renameCategory(admin, c.getId(), rename.getText());
                categories.getItems().setAll(system.getCategories().values());
            }
        });

        Button delete = new Button("Delete selected");
        delete.setOnAction(e -> {
            Category c = categories.getSelectionModel().getSelectedItem();
            if (c != null) {
                system.deleteCategory(admin, c.getId());
                categories.getItems().setAll(system.getCategories().values());
            }
        });

        VBox root = new VBox(8, categories, name, add, rename, renameBtn, delete);
        root.setPadding(new Insets(10));
        return root;
    }
}