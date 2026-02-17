package gr.ntua.multimedia.service;

import gr.ntua.multimedia.domain.Admin;
import gr.ntua.multimedia.domain.Author;
import gr.ntua.multimedia.domain.Category;
import gr.ntua.multimedia.domain.Document;
import gr.ntua.multimedia.domain.DocumentVersion;
import gr.ntua.multimedia.domain.User;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Core application service that implements business use-cases for the Document Management System.
 * <p>
 * This class is the public facade that owns the in-memory state and delegates operations
 * to smaller internal services.
 */
public class MediaLabSystem {
    private final Map<String, User> usersByUsername;
    private final Map<String, Category> categoriesById;
    private final Map<String, Document> documentsById;

    private final AuthService authService;
    private final UserService userService;
    private final CategoryService categoryService;
    private final DocumentService documentService;
    private final FollowService followService;

    /**
     * Creates a new system with empty state and a default administrator account.
     */
    public MediaLabSystem() {
        this.usersByUsername = new HashMap<>();
        this.categoriesById = new HashMap<>();
        this.documentsById = new HashMap<>();
        this.authService = new AuthService(this.usersByUsername);
        this.userService = new UserService(this.usersByUsername, this.categoriesById);
        this.categoryService = new CategoryService(this.usersByUsername, this.categoriesById, this.documentsById);
        this.documentService = new DocumentService(this.usersByUsername, this.categoriesById, this.documentsById);
        this.followService = new FollowService(this.documentsById);
        userService.bootstrapDefaultAdmin();
    }

    /**
     * Creates a new system with preloaded state.
     *
     * @param usersByUsername existing users mapped by username
     * @param categoriesById existing categories mapped by id
     * @param documentsById existing documents mapped by id
     */
    public MediaLabSystem(Map<String, User> usersByUsername,
                          Map<String, Category> categoriesById,
                          Map<String, Document> documentsById) {
        this.usersByUsername = new HashMap<>(usersByUsername);
        this.categoriesById = new HashMap<>(categoriesById);
        this.documentsById = new HashMap<>(documentsById);
        this.authService = new AuthService(this.usersByUsername);
        this.userService = new UserService(this.usersByUsername, this.categoriesById);
        this.categoryService = new CategoryService(this.usersByUsername, this.categoriesById, this.documentsById);
        this.documentService = new DocumentService(this.usersByUsername, this.categoriesById, this.documentsById);
        this.followService = new FollowService(this.documentsById);
        if (!this.usersByUsername.containsKey("medialab")) {
            userService.bootstrapDefaultAdmin();
        }
    }

    public void addUser(Admin adminActor, String firstName, String lastName, String role,
                        Set<String> allowedCategoryIds, String username, String plainPassword) {
        userService.addUser(adminActor, firstName, lastName, role, allowedCategoryIds, username, plainPassword);
    }

    public void deleteUser(Admin adminActor, String username) {
        userService.deleteUser(adminActor, username);
    }

    public List<User> listUsers(Admin adminActor) {
        return userService.listUsers(adminActor);
    }

    public Category addCategory(Admin adminActor, String categoryName) {
        return categoryService.addCategory(adminActor, categoryName);
    }

    public void renameCategory(Admin adminActor, String categoryId, String newName) {
        categoryService.renameCategory(adminActor, categoryId, newName);
    }

    public void deleteCategory(Admin adminActor, String categoryId) {
        categoryService.deleteCategory(adminActor, categoryId);
    }

    public Document createDocument(Author actor, String title, String categoryId, String initialContent) {
        return documentService.createDocument(actor, title, categoryId, initialContent);
    }

    public void updateDocumentText(Author actor, String documentId, String newContent) {
        documentService.updateDocumentText(actor, documentId, newContent);
    }

    public void deleteDocument(Author actor, String documentId) {
        documentService.deleteDocument(actor, documentId);
    }

    public Document getDocumentForViewing(User actor, String documentId) {
        return documentService.getDocumentForViewing(actor, documentId);
    }

    public List<DocumentVersion> getVisibleVersions(User actor, String documentId) {
        return documentService.getVisibleVersions(actor, documentId);
    }

    public List<Document> search(User actor, Optional<String> categoryId, Optional<String> titleContains, Optional<String> authorUsername) {
        return documentService.search(actor, categoryId, titleContains, authorUsername);
    }

    public void followDocument(User actor, String documentId) {
        followService.followDocument(actor, documentId, documentService);
    }

    public void unfollowDocument(User actor, String documentId) {
        followService.unfollowDocument(actor, documentId);
    }

    public List<String> getNotificationsOnLogin(User actor) {
        return followService.getNotificationsOnLogin(actor);
    }

    public void markDocumentSeen(User actor, String documentId) {
        followService.markDocumentSeen(actor, documentId, documentService);
    }

    public User login(String username, String plainPassword) {
        return authService.login(username, plainPassword);
    }

    public Map<String, User> getUsers() {
        return Collections.unmodifiableMap(new HashMap<>(usersByUsername));
    }

    public Map<String, Category> getCategories() {
        return Collections.unmodifiableMap(new HashMap<>(categoriesById));
    }

    public Map<String, Document> getDocuments() {
        return Collections.unmodifiableMap(new HashMap<>(documentsById));
    }
}