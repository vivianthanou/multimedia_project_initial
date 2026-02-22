package gr.ntua.multimedia.ui.controller;

import gr.ntua.multimedia.domain.Admin;
import gr.ntua.multimedia.domain.Author;
import gr.ntua.multimedia.domain.Category;
import gr.ntua.multimedia.domain.Document;
import gr.ntua.multimedia.domain.DocumentVersion;
import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.service.MediaLabSystem;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class DocumentsController {
    private final MediaLabSystem system;
    private final Runnable onDataChanged;

    public DocumentsController(MediaLabSystem system, Runnable onDataChanged) {
        this.system = system;
        this.onDataChanged = (onDataChanged != null) ? onDataChanged : () -> {};
    }

    public VBox createView(User user) {

        // Shared format hint
        Label formatHint = new Label("Format: Title | Author | Category | Created At | Version | Document ID");
        formatHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // =========================================================
        // 1) SEARCH DOCUMENTS PANE
        // =========================================================
        ComboBox<Category> categoryFilterBox = new ComboBox<>();
        categoryFilterBox.setPromptText("All categories");
        categoryFilterBox.getItems().add(null); // null => All

        List<Category> filterCategories = system.getCategories().values().stream()
                .filter(c -> (user instanceof Admin) || user.canAccessCategory(c.getId()))
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        categoryFilterBox.getItems().addAll(filterCategories);
        categoryFilterBox.getSelectionModel().selectFirst();

        categoryFilterBox.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setText(null);
                else if (item == null) setText("All categories");
                else setText(item.getName());
            }
        });
        categoryFilterBox.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Category item, boolean empty) {
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

        ListView<Document> searchResults = new ListView<>();
        VBox.setVgrow(searchResults, Priority.ALWAYS);
        searchResults.setCellFactory(lv -> docCellFactory(dtf));

        Runnable runSearch = () -> {
            try {
                Category selectedCategory = categoryFilterBox.getValue(); // null => all
                Optional<String> categoryIdOpt = (selectedCategory == null) ? Optional.empty() : Optional.of(selectedCategory.getId());

                Optional<String> titleOpt = Optional.ofNullable(titleFilter.getText())
                        .map(String::trim)
                        .filter(s -> !s.isBlank());

                Optional<String> authorNameOpt = Optional.ofNullable(authorFilter.getText())
                        .map(String::trim)
                        .filter(s -> !s.isBlank());

                // Service search already ANDs category + title
                List<Document> base = system.search(user, categoryIdOpt, titleOpt, Optional.empty());

                // AND author full-name filter on top
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

                searchResults.getItems().setAll(base);

            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        };
        searchBtn.setOnAction(e -> runSearch.run());

        Button followBtn = new Button("Follow selected");
        followBtn.setOnAction(e -> {
            Document d = searchResults.getSelectionModel().getSelectedItem();
            if (d == null) {
                showError("Please select a document first.");
                return;
            }
            try {
                system.followDocument(user, d.getId());
                refreshFollowedList(user); // defined below via array wrapper
                onDataChanged.run();
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });

        Button viewBtn = new Button("View document"); // (better than "See document")
        viewBtn.setOnAction(e -> {
            Document d = searchResults.getSelectionModel().getSelectedItem();
            if (d == null) {
                showError("Please select a document first.");
                return;
            }
            showDocumentPopup(user, d);
        });

        Button editBtn = new Button("Edit selected (content)");
        Button deleteBtn = new Button("Delete selected");
        editBtn.setDisable(true);
        deleteBtn.setDisable(true);

        // Enable edit/delete only for Admin or owning Author
        searchResults.getSelectionModel().selectedItemProperty().addListener((a, b, doc) -> {
            boolean canEdit = (doc != null) && canEdit(user, doc);
            editBtn.setDisable(!canEdit);
            deleteBtn.setDisable(!canEdit); // delete uses same rule as before (owner author or admin)
        });

        editBtn.setOnAction(e -> {
            Document doc = searchResults.getSelectionModel().getSelectedItem();
            if (doc == null) return;

            if (!canEdit(user, doc)) {
                showError("You are not allowed to edit this document.");
                return;
            }
            showEditPopup(user, doc, () -> {
                runSearch.run();
                refreshFollowedList(user);
                onDataChanged.run();
            });
        });

        deleteBtn.setOnAction(e -> {
            Document doc = searchResults.getSelectionModel().getSelectedItem();
            if (doc == null) return;

            if (!(user instanceof Author)) {
                showError("Only Author/Admin can delete documents.");
                return;
            }

            // Confirm delete
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete Document");
            confirm.setHeaderText("Delete document '" + doc.getTitle() + "'?");
            confirm.setContentText("This will permanently delete the document and its versions.");
            ButtonType deleteType = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            confirm.getButtonTypes().setAll(deleteType, cancelType);

            Optional<ButtonType> choice = confirm.showAndWait();
            if (choice.isEmpty() || choice.get() != deleteType) return;

            try {
                system.deleteDocument((Author) user, doc.getId());
                runSearch.run();
                refreshFollowedList(user);
                onDataChanged.run();
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });

        VBox searchPaneContent = new VBox(
                8,
                new Label("Search Documents"),
                categoryFilterBox,
                titleFilter,
                authorFilter,
                searchBtn,
                formatHint,
                searchResults,
                new Separator(),
                new Label("Actions"),
                new VBox(6, viewBtn, followBtn, editBtn, deleteBtn)
        );
        searchPaneContent.setPadding(new Insets(8));

        TitledPane searchPane = new TitledPane("Search Documents", searchPaneContent);
        searchPane.setCollapsible(false);
        searchPane.setExpanded(true);

        // =========================================================
        // 2) FOLLOWING DOCUMENTS PANE
        // =========================================================
        Label followingHint = new Label("Documents you Follow");
        followingHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");

        ListView<Document> followedList = new ListView<>();
        VBox.setVgrow(followedList, Priority.ALWAYS);
        followedList.setCellFactory(lv -> docCellFactory(dtf));

        Button unfollowBtn = new Button("Unfollow selected");
        unfollowBtn.setOnAction(e -> {
            Document d = followedList.getSelectionModel().getSelectedItem();
            if (d == null) {
                showError("Please select a followed document first.");
                return;
            }
            try {
                system.unfollowDocument(user, d.getId());
                refreshFollowedList(user);
                onDataChanged.run();
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });

        Button viewFollowedBtn = new Button("View document");
        viewFollowedBtn.setOnAction(e -> {
            Document d = followedList.getSelectionModel().getSelectedItem();
            if (d == null) {
                showError("Please select a document first.");
                return;
            }
            showDocumentPopup(user, d);
        });

        VBox followedPaneContent = new VBox(
                8,
                new Label("Following Documents"),
                followingHint,
                formatHintCopy(),
                followedList,
                new VBox(6, viewFollowedBtn, unfollowBtn)
        );
        followedPaneContent.setPadding(new Insets(8));

        TitledPane followedPane = new TitledPane("Following Documents", followedPaneContent);
        followedPane.setCollapsible(false);
        followedPane.setExpanded(true);

        // =========================================================
        // 3) CREATE NEW DOCUMENT PANE (Author/Admin only)
        // =========================================================
        TitledPane createPane = null;

        if (user instanceof Author author) {
            TextField docTitle = new TextField();
            docTitle.setPromptText("Title");

            ComboBox<Category> categoryBox = new ComboBox<>();
            categoryBox.setPromptText("Select Category");
            categoryBox.getItems().setAll(
                    system.getCategories().values().stream()
                            .filter(c -> author.canAccessCategory(c.getId()))
                            .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                            .toList()
            );
            categoryBox.setCellFactory(lv -> new ListCell<>() {
                @Override protected void updateItem(Category item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            });
            categoryBox.setButtonCell(new ListCell<>() {
                @Override protected void updateItem(Category item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            });

            TextArea content = new TextArea();
            content.setPromptText("Content");
            content.setWrapText(true);
            content.setPrefRowCount(8);

            Button createBtn = new Button("Create document");
            createBtn.setOnAction(e -> {
                Category selected = categoryBox.getValue();
                if (selected == null) {
                    showError("Please select a category.");
                    return;
                }
                try {
                    system.createDocument(author, docTitle.getText(), selected.getId(), content.getText());
                    docTitle.clear();
                    content.clear();
                    categoryBox.getSelectionModel().clearSelection();

                    runSearch.run();
                    refreshFollowedList(user);
                    onDataChanged.run();
                } catch (RuntimeException ex) {
                    showError(ex.getMessage());
                }
            });

            VBox createPaneContent = new VBox(
                    8,
                    new Label("Create New Document"),
                    new Label("Title"),
                    docTitle,
                    new Label("Category"),
                    categoryBox,
                    new Label("Content"),
                    content,
                    createBtn
            );
            createPaneContent.setPadding(new Insets(8));

            createPane = new TitledPane("Create New Document (Author/Admin)", createPaneContent);
            createPane.setCollapsible(false);
            createPane.setExpanded(true);
        }

        // =========================================================
        // Root layout: 3 non-closable panes (always visible)
        // =========================================================
        VBox root = new VBox(12);
        root.setPadding(new Insets(10));

        root.getChildren().addAll(searchPane, followedPane);
        if (createPane != null) root.getChildren().add(createPane);

        // initial load
        runSearch.run();

        // refresh followed list function (needs access to followedList)
        Runnable refreshFollowed = () -> {
            List<Document> docs = new ArrayList<>();
            for (String docId : user.getFollowedDocumentIds()) {
                Document d = system.getDocuments().get(docId);
                if (d == null) continue;
                // only show if user can access category (admin can)
                if (user instanceof Admin || user.canAccessCategory(d.getCategoryId())) {
                    docs.add(d);
                }
            }
            followedList.getItems().setAll(docs);
        };

        // store into array so lambdas above can call it before it's assigned (simple trick)
        this.refreshFollowedListHolder = refreshFollowed;
        refreshFollowed.run();

        return root;
    }

    // ---------- Helpers ----------

    // Holder to call refresh from earlier lambdas (initialized later)
    private Runnable refreshFollowedListHolder = () -> {};
    private void refreshFollowedList(User user) { refreshFollowedListHolder.run(); }

    private Label formatHintCopy() {
        Label l = new Label("Format: Title | Author | Category | Created At | Version | Document ID");
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
        return l;
    }

    private ListCell<Document> docCellFactory(DateTimeFormatter dtf) {
        return new ListCell<>() {
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
        };
    }

    private boolean canEdit(User user, Document doc) {
        if (user instanceof Admin) return true;
        return user instanceof Author && user.getUsername().equals(doc.getAuthorUsername());
    }

    private void showDocumentPopup(User user, Document doc) {
        try {
            // Ensure access check
            Document fresh = system.getDocumentForViewing(user, doc.getId());

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("View Document");
            dialog.setHeaderText(fresh.getTitle());

            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

            VBox contentBox = new VBox(8);
            contentBox.setPadding(new Insets(10));

            // Version selector for Author/Admin (up to 3 visible), Simple sees only latest
            List<DocumentVersion> visible = system.getVisibleVersions(user, fresh.getId());

            Label versionLabel = new Label();
            TextArea textArea = new TextArea();
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setPrefRowCount(16);

            if (user instanceof Admin || user instanceof Author) {
                ComboBox<DocumentVersion> versionBox = new ComboBox<>();
                versionBox.getItems().setAll(visible);

                versionBox.setCellFactory(lv -> new ListCell<>() {
                    @Override protected void updateItem(DocumentVersion item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : "v" + item.getVersionNumber() + " (" + item.getCreatedAt() + ")");
                    }
                });
                versionBox.setButtonCell(new ListCell<>() {
                    @Override protected void updateItem(DocumentVersion item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : "v" + item.getVersionNumber() + " (" + item.getCreatedAt() + ")");
                    }
                });

                versionBox.getSelectionModel().selectFirst();
                DocumentVersion selected = versionBox.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    versionLabel.setText("Showing version v" + selected.getVersionNumber());
                    textArea.setText(selected.getContent());
                }

                versionBox.setOnAction(e -> {
                    DocumentVersion v = versionBox.getSelectionModel().getSelectedItem();
                    if (v != null) {
                        versionLabel.setText("Showing version v" + v.getVersionNumber());
                        textArea.setText(v.getContent());
                    }
                });

                contentBox.getChildren().addAll(new Label("Select version:"), versionBox, versionLabel, textArea);

            } else {
                // Simple user: only latest (visible will contain 1 element)
                DocumentVersion latest = visible.isEmpty() ? null : visible.get(0);
                if (latest != null) {
                    versionLabel.setText("Showing latest version v" + latest.getVersionNumber());
                    textArea.setText(latest.getContent());
                } else {
                    versionLabel.setText("No content available.");
                }
                contentBox.getChildren().addAll(versionLabel, textArea);
            }

            dialog.getDialogPane().setContent(contentBox);

            // Mark seen when user views
            system.markDocumentSeen(user, fresh.getId());
            onDataChanged.run();

            dialog.showAndWait();

        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void showEditPopup(User user, Document doc, Runnable afterSave) {
        if (!(user instanceof Admin || user instanceof Author)) {
            showError("Only Author/Admin can edit documents.");
            return;
        }

        try {
            Document fresh = system.getDocumentForViewing(user, doc.getId());
            String oldText = fresh.getLatestContent();

            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("Edit Document");
            dialog.setHeaderText("Editing: " + fresh.getTitle() + " (only content can change)");

            ButtonType saveBtnType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

            TextArea editor = new TextArea(oldText);
            editor.setWrapText(true);
            editor.setPrefRowCount(16);
            dialog.getDialogPane().setContent(editor);

            dialog.setResultConverter(btn -> btn == saveBtnType ? editor.getText() : null);

            Optional<String> result = dialog.showAndWait();
            if (result.isEmpty() || result.get() == null) return;

            String newText = result.get();
            if (newText.equals(oldText)) {
                showInfo("No changes detected. No new version created.");
                return;
            }

            system.updateDocumentText((Author) user, fresh.getId(), newText);
            afterSave.run();

        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
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