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

    private User currentUser;

    private ComboBox<Category> categoryFilterBox;
    private TextField titleFilter;
    private TextField authorFilter;

    private ListView<Document> searchResults;
    private ListView<Document> followedList;

    private ComboBox<Category> createCategoryBox; // only for Author/Admin
    private TextField createDocTitle;
    private TextArea createDocContent;

    private Runnable runSearchHolder = () -> {};
    private Runnable refreshFollowedListHolder = () -> {};

    public DocumentsController(MediaLabSystem system, Runnable onDataChanged) {
        this.system = system;
        this.onDataChanged = (onDataChanged != null) ? onDataChanged : () -> {};
    }

    public VBox createView(User user) {
        this.currentUser = user;

        Label formatHint = new Label("Format: Title | Author | Category | Created At | Version | Document ID");
        formatHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // =========================================================
        // 1) SEARCH DOCUMENTS PANE
        // =========================================================
        categoryFilterBox = new ComboBox<>();
        categoryFilterBox.setPromptText("All categories");
        categoryFilterBox.getItems().add(null); // null => All

        // Cell factories (once)
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

        titleFilter = new TextField();
        titleFilter.setPromptText("Title contains...");

        authorFilter = new TextField();
        authorFilter.setPromptText("Author name contains (first last)...");

        Button searchBtn = new Button("Search");

        searchResults = new ListView<>();
        VBox.setVgrow(searchResults, Priority.ALWAYS);
        searchResults.setCellFactory(lv -> docCellFactory(dtf));

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

                List<Document> base = system.search(currentUser, categoryIdOpt, titleOpt, Optional.empty());

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
        runSearchHolder = runSearch;
        searchBtn.setOnAction(e -> runSearch.run());

        Button followBtn = new Button("Follow selected");
        followBtn.setOnAction(e -> {
            Document d = searchResults.getSelectionModel().getSelectedItem();
            if (d == null) {
                showError("Please select a document first.");
                return;
            }
            try {
                system.followDocument(currentUser, d.getId());
                refreshFollowedListHolder.run();
                onDataChanged.run();
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });

        Button viewBtn = new Button("View document");
        viewBtn.setOnAction(e -> {
            Document d = searchResults.getSelectionModel().getSelectedItem();
            if (d == null) {
                showError("Please select a document first.");
                return;
            }
            showDocumentPopup(currentUser, d);
        });

        Button editBtn = new Button("Edit selected");
        Button deleteBtn = new Button("Delete selected");
        editBtn.setDisable(true);
        deleteBtn.setDisable(true);

        searchResults.getSelectionModel().selectedItemProperty().addListener((a, b, doc) -> {
            boolean canEdit = (doc != null) && canEdit(currentUser, doc);
            editBtn.setDisable(!canEdit);
            deleteBtn.setDisable(!canEdit);
        });

        editBtn.setOnAction(e -> {
            Document doc = searchResults.getSelectionModel().getSelectedItem();
            if (doc == null) return;

            if (!canEdit(currentUser, doc)) {
                showError("You are not allowed to edit this document.");
                return;
            }
            showEditPopup(currentUser, doc, () -> {
                runSearchHolder.run();
                refreshFollowedListHolder.run();
                onDataChanged.run();
            });
        });

        deleteBtn.setOnAction(e -> {
            Document doc = searchResults.getSelectionModel().getSelectedItem();
            if (doc == null) return;

            if (!(currentUser instanceof Author)) {
                showError("Only Author/Admin can delete documents.");
                return;
            }

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
                system.deleteDocument((Author) currentUser, doc.getId());
                runSearchHolder.run();
                refreshFollowedListHolder.run();
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

        followedList = new ListView<>();
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
                system.unfollowDocument(currentUser, d.getId());
                refreshFollowedListHolder.run();
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
            showDocumentPopup(currentUser, d);
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

        if (currentUser instanceof Author author) {
            createDocTitle = new TextField();
            createDocTitle.setPromptText("Title");

            createCategoryBox = new ComboBox<>();
            createCategoryBox.setPromptText("Select Category");
            createCategoryBox.setCellFactory(lv -> new ListCell<>() {
                @Override protected void updateItem(Category item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            });
            createCategoryBox.setButtonCell(new ListCell<>() {
                @Override protected void updateItem(Category item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            });

            createDocContent = new TextArea();
            createDocContent.setPromptText("Content");
            createDocContent.setWrapText(true);
            createDocContent.setPrefRowCount(8);

            Button createBtn = new Button("Create document");
            createBtn.setOnAction(e -> {
                Category selected = createCategoryBox.getValue();
                if (selected == null) {
                    showError("Please select a category.");
                    return;
                }
                try {
                    system.createDocument(author, createDocTitle.getText(), selected.getId(), createDocContent.getText());
                    createDocTitle.clear();
                    createDocContent.clear();
                    createCategoryBox.getSelectionModel().clearSelection();

                    runSearchHolder.run();
                    refreshFollowedListHolder.run();
                    onDataChanged.run();
                } catch (RuntimeException ex) {
                    showError(ex.getMessage());
                }
            });

            VBox createPaneContent = new VBox(
                    8,
                    new Label("Create New Document"),
                    new Label("Title"),
                    createDocTitle,
                    new Label("Category"),
                    createCategoryBox,
                    new Label("Content"),
                    createDocContent,
                    createBtn
            );
            createPaneContent.setPadding(new Insets(8));

            createPane = new TitledPane("Create New Document (Author/Admin)", createPaneContent);
            createPane.setCollapsible(false);
            createPane.setExpanded(true);
        }

        // =========================================================
        // Root layout
        // =========================================================
        VBox root = new VBox(12);
        root.setPadding(new Insets(10));

        root.getChildren().addAll(searchPane, followedPane);
        if (createPane != null) root.getChildren().add(createPane);

        // followed list refresh function
        refreshFollowedListHolder = () -> {
            List<Document> docs = new ArrayList<>();
            for (String docId : currentUser.getFollowedDocumentIds()) {
                Document d = system.getDocuments().get(docId);
                if (d == null) continue;
                if (currentUser instanceof Admin || currentUser.canAccessCategory(d.getCategoryId())) {
                    docs.add(d);
                }
            }
            followedList.getItems().setAll(docs);
        };

        // initial data load
        reloadCategoryChoices();
        runSearchHolder.run();
        refreshFollowedListHolder.run();

        return root;
    }

    // ===== LIVE refresh helpers =====

    private void reloadCategoryChoices() {
        if (currentUser == null || categoryFilterBox == null) return;

        // keep current selection if possible
        Category selected = categoryFilterBox.getValue();

        // rebuild filter categories
        categoryFilterBox.getItems().clear();
        categoryFilterBox.getItems().add(null);

        List<Category> filterCategories = system.getCategories().values().stream()
                .filter(c -> (currentUser instanceof Admin) || currentUser.canAccessCategory(c.getId()))
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        categoryFilterBox.getItems().addAll(filterCategories);

        if (selected == null) {
            categoryFilterBox.getSelectionModel().selectFirst();
        } else {
            Category match = filterCategories.stream()
                    .filter(c -> c.getId().equals(selected.getId()))
                    .findFirst()
                    .orElse(null);
            categoryFilterBox.getSelectionModel().select(match);
        }

        // rebuild create categories (author/admin only)
        if (createCategoryBox != null && currentUser instanceof Author author) {
            Category prev = createCategoryBox.getValue();

            List<Category> allowed = system.getCategories().values().stream()
                    .filter(c -> author.canAccessCategory(c.getId()))
                    .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();

            createCategoryBox.getItems().setAll(allowed);

            if (prev != null) {
                Category match = allowed.stream()
                        .filter(c -> c.getId().equals(prev.getId()))
                        .findFirst()
                        .orElse(null);
                createCategoryBox.getSelectionModel().select(match);
            }
        }
    }

    public void refresh() {
        reloadCategoryChoices();
        if (runSearchHolder != null) runSearchHolder.run();
        if (refreshFollowedListHolder != null) refreshFollowedListHolder.run();
        if (searchResults != null) searchResults.refresh();
        if (followedList != null) followedList.refresh();
    }

    // ---------- existing helpers ----------

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
            Document fresh = system.getDocumentForViewing(user, doc.getId());

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("View Document");
            dialog.setHeaderText(fresh.getTitle());
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

            VBox contentBox = new VBox(8);
            contentBox.setPadding(new Insets(10));

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