package gr.ntua.multimedia.ui.controller;

import gr.ntua.multimedia.domain.Admin;
import gr.ntua.multimedia.domain.Author;
import gr.ntua.multimedia.domain.Category;
import gr.ntua.multimedia.domain.Document;
import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.service.MediaLabSystem;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DocumentsController {
    private final MediaLabSystem system;

    public DocumentsController(MediaLabSystem system) {
        this.system = system;
    }

    public VBox createView(User user) {

        // ---- Explanatory line (header)
        Label formatHint = new Label("Format: Title | Author | Category | Created At | Version | Document ID");
        formatHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");

        // ---- Filters
        ComboBox<Category> categoryFilterBox = new ComboBox<>();
        categoryFilterBox.setPromptText("All categories");
        categoryFilterBox.getItems().add(null); // null => All categories

        List<Category> filterCategories = system.getCategories().values().stream()
                .filter(c -> (user instanceof Admin) || user.canAccessCategory(c.getId()))
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();

        categoryFilterBox.getItems().addAll(filterCategories);
        categoryFilterBox.getSelectionModel().selectFirst();

        categoryFilterBox.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setText(null);
                else if (item == null) setText("All categories");
                else setText(item.getName());
            }
        });
        categoryFilterBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setText(null);
                else if (item == null) setText("All categories");
                else setText(item.getName());
            }
        });

        TextField titleFilter = new TextField();
        titleFilter.setPromptText("Title contains...");

        TextField authorFilter = new TextField();
        authorFilter.setPromptText("Author name contains (first last)...");

        Button searchBtn = new Button("Search");

        // ---- Documents list
        ListView<Document> docs = new ListView<>();

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        docs.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Document item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }

                Category cat = system.getCategories().get(item.getCategoryId());
                String categoryName = (cat != null) ? cat.getName() : ("<deleted:" + item.getCategoryId() + ">");

                String authorDisplay = item.getAuthorUsername();
                User authorUser = system.getUsers().get(item.getAuthorUsername());
                if (authorUser != null) {
                    authorDisplay = authorUser.getFirstName() + " " + authorUser.getLastName();
                }

                String createdAt = item.getCreatedAt() != null ? item.getCreatedAt().format(dtf) : "-";

                setText(
                        item.getTitle() + " | " +
                                authorDisplay + " | " +
                                categoryName + " | " +
                                createdAt + " | " +
                                "v" + item.getLatestVersionNumber() + " | " +
                                item.getId()
                );
            }
        });

        // ---- Viewer
        TextArea viewer = new TextArea();
        viewer.setEditable(false);
        viewer.setWrapText(true);

        // ---- Follow / Unfollow
        Button follow = new Button("Follow selected");
        Button unfollow = new Button("Unfollow selected");

        follow.setOnAction(e -> {
            Document d = docs.getSelectionModel().getSelectedItem();
            if (d != null) system.followDocument(user, d.getId());
        });
        unfollow.setOnAction(e -> {
            Document d = docs.getSelectionModel().getSelectedItem();
            if (d != null) system.unfollowDocument(user, d.getId());
        });

        // ---- Search logic (AND, all optional)
        Runnable runSearch = () -> {
            try {
                Category selectedCategory = categoryFilterBox.getValue(); // null => all
                Optional<String> categoryIdOpt = (selectedCategory == null)
                        ? Optional.empty()
                        : Optional.of(selectedCategory.getId());

                Optional<String> titleOpt = Optional.ofNullable(titleFilter.getText())
                        .map(String::trim)
                        .filter(s -> !s.isBlank());

                Optional<String> authorNameOpt = Optional.ofNullable(authorFilter.getText())
                        .map(String::trim)
                        .filter(s -> !s.isBlank());

                List<Document> base = system.search(user, categoryIdOpt, titleOpt, Optional.empty());

                if (authorNameOpt.isPresent()) {
                    String q = authorNameOpt.get().toLowerCase();
                    base = base.stream()
                            .filter(d -> {
                                User au = system.getUsers().get(d.getAuthorUsername());
                                String full = (au != null)
                                        ? (au.getFirstName() + " " + au.getLastName())
                                        : d.getAuthorUsername();
                                return full.toLowerCase().contains(q);
                            })
                            .collect(Collectors.toList());
                }

                docs.getItems().setAll(base);

            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        };

        searchBtn.setOnAction(e -> runSearch.run());

        // ---- Selection behavior: update viewer + enable/disable Edit
        Button editBtn = new Button("Edit selected");
        editBtn.setDisable(true); // disabled until selection and permission ok

        docs.getSelectionModel().selectedItemProperty().addListener((a, b, doc) -> {
            if (doc != null) {
                viewer.setText(system.getDocumentForViewing(user, doc.getId()).getLatestContent());
                editBtn.setDisable(!canEdit(user, doc));
            } else {
                viewer.clear();
                editBtn.setDisable(true);
            }
        });

        // ---- Edit popup
        editBtn.setOnAction(e -> {
            Document selected = docs.getSelectionModel().getSelectedItem();
            if (selected == null) return;

            if (!canEdit(user, selected)) {
                showError("You are not allowed to edit this document.");
                return;
            }

            Document fresh = system.getDocumentForViewing(user, selected.getId());
            String oldText = fresh.getLatestContent();

            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("Edit Document");
            dialog.setHeaderText("Editing: " + fresh.getTitle() + " (only content can change)");

            ButtonType saveBtnType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

            TextArea editor = new TextArea(oldText);
            editor.setWrapText(true);
            editor.setPrefRowCount(15);

            dialog.getDialogPane().setContent(editor);

            dialog.setResultConverter(btn -> {
                if (btn == saveBtnType) return editor.getText();
                return null;
            });

            Optional<String> result = dialog.showAndWait();
            if (result.isEmpty() || result.get() == null) {
                return; // Cancel
            }

            String newText = result.get();

            // ✅ Do not create new version if content is the same
            if (newText.equals(oldText)) {
                showInfo("No changes detected. No new version created.");
                return;
            }

            try {
                // user is either Admin or Author (owner). Both can be cast to Author in your hierarchy.
                system.updateDocumentText((Author) user, fresh.getId(), newText);
                runSearch.run();

                // refresh viewer if still selected
                Document stillSelected = docs.getSelectionModel().getSelectedItem();
                if (stillSelected != null && stillSelected.getId().equals(fresh.getId())) {
                    viewer.setText(system.getDocumentForViewing(user, fresh.getId()).getLatestContent());
                }

            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });

        // ---- Layout
        VBox box = new VBox(
                8,
                new Label("Documents"),
                categoryFilterBox,
                titleFilter,
                authorFilter,
                searchBtn,
                formatHint,
                docs,
                editBtn,
                new Label("Latest Content"),
                viewer,
                follow,
                unfollow
        );

        VBox.setVgrow(docs, Priority.ALWAYS);
        VBox.setVgrow(viewer, Priority.ALWAYS);

        // ---- Author actions (create + delete) (Edit is common via popup)
        if (user instanceof Author author) {
            TextField docTitle = new TextField();
            docTitle.setPromptText("Title");

            ComboBox<Category> categoryBox = new ComboBox<>();
            categoryBox.setPromptText("Select category (allowed only)");
            categoryBox.getItems().setAll(
                    system.getCategories().values().stream()
                            .filter(c -> author.canAccessCategory(c.getId()))
                            .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                            .toList()
            );
            categoryBox.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(Category item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            });
            categoryBox.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(Category item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            });

            TextArea content = new TextArea();
            content.setPromptText("Content");
            content.setWrapText(true);
            content.setPrefRowCount(6);

            Button create = new Button("Create");
            create.setOnAction(e -> {
                Category selected = categoryBox.getValue();
                if (selected == null) {
                    showError("Please select a category.");
                    return;
                }
                try {
                    system.createDocument(author, docTitle.getText(), selected.getId(), content.getText());
                    runSearch.run();
                } catch (RuntimeException ex) {
                    showError(ex.getMessage());
                }
            });

            Button delete = new Button("Delete Selected");
            delete.setOnAction(e -> {
                Document d = docs.getSelectionModel().getSelectedItem();
                if (d == null) {
                    showError("Please select a document first.");
                    return;
                }
                try {
                    system.deleteDocument(author, d.getId());
                    viewer.clear();
                    runSearch.run();
                } catch (RuntimeException ex) {
                    showError(ex.getMessage());
                }
            });

            box.getChildren().addAll(
                    new Separator(),
                    new Label("Author Actions"),
                    docTitle,
                    categoryBox,
                    content,
                    create,
                    delete
            );
        }

        box.setPadding(new Insets(10));

        runSearch.run();
        return box;
    }

    // ✅ Permission rule for edit:
    // - Admin can edit any document
    // - Author can edit only own documents
    private boolean canEdit(User user, Document doc) {
        if (user instanceof Admin) return true;
        return user instanceof Author && user.getUsername().equals(doc.getAuthorUsername());
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message == null || message.isBlank() ? "Unknown error" : message);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Info");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
