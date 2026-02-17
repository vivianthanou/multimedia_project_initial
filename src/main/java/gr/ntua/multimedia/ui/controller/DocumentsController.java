package gr.ntua.multimedia.ui.controller;

import gr.ntua.multimedia.domain.Author;
import gr.ntua.multimedia.domain.Document;
import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.service.MediaLabSystem;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.Optional;

public class DocumentsController {
    private final MediaLabSystem system;

    public DocumentsController(MediaLabSystem system) { this.system = system; }

    public VBox createView(User user) {
        ListView<Document> docs = new ListView<>();
        TextField titleFilter = new TextField();
        titleFilter.setPromptText("Search title contains...");
        Button searchBtn = new Button("Search");
        searchBtn.setOnAction(e -> docs.getItems().setAll(system.search(user, Optional.empty(), Optional.ofNullable(titleFilter.getText()).filter(s -> !s.isBlank()), Optional.empty())));

        TextArea viewer = new TextArea();
        viewer.setEditable(false);
        docs.getSelectionModel().selectedItemProperty().addListener((a,b,doc) -> {
            if (doc != null) {
                viewer.setText(system.getDocumentForViewing(user, doc.getId()).getLatestContent());
            }
        });

        Button follow = new Button("Follow selected");
        follow.setOnAction(e -> {
            Document d = docs.getSelectionModel().getSelectedItem();
            if (d != null) system.followDocument(user, d.getId());
        });
        Button unfollow = new Button("Unfollow selected");
        unfollow.setOnAction(e -> {
            Document d = docs.getSelectionModel().getSelectedItem();
            if (d != null) system.unfollowDocument(user, d.getId());
        });

        VBox box = new VBox(8, titleFilter, searchBtn, docs, new Label("Latest Content"), viewer, follow, unfollow);
        if (user instanceof Author author) {
            TextField docTitle = new TextField(); docTitle.setPromptText("Title");
            TextField categoryId = new TextField(); categoryId.setPromptText("Category ID");
            TextArea content = new TextArea(); content.setPromptText("Content");
            Button create = new Button("Create");
            create.setOnAction(e -> system.createDocument(author, docTitle.getText(), categoryId.getText(), content.getText()));
            Button update = new Button("Update Selected");
            update.setOnAction(e -> {
                Document d = docs.getSelectionModel().getSelectedItem();
                if (d != null) system.updateDocumentText(author, d.getId(), content.getText());
            });
            Button delete = new Button("Delete Selected");
            delete.setOnAction(e -> {
                Document d = docs.getSelectionModel().getSelectedItem();
                if (d != null) system.deleteDocument(author, d.getId());
            });
            box.getChildren().addAll(new Separator(), new Label("Author Actions"), docTitle, categoryId, content, create, update, delete);
        }
        box.setPadding(new Insets(10));
        return box;
    }
}