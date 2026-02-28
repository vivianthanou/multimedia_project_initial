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
        this.userService = new UserService(this.usersByUsername, this.categoriesById, this.documentsById);
        this.followService = new FollowService(this.documentsById);
        this.categoryService = new CategoryService(this.usersByUsername, this.categoriesById, this.documentsById, this.followService);
        this.documentService = new DocumentService(this.usersByUsername, this.categoriesById, this.documentsById, this.followService);

        userService.bootstrapDefaultAdmin();
    }

    /**
     * Creates a new system with preloaded state.
     *
     * @param usersByUsername existing users mapped by username
     * @param categoriesById  existing categories mapped by id
     * @param documentsById   existing documents mapped by id
     */
    public MediaLabSystem(Map<String, User> usersByUsername,
                          Map<String, Category> categoriesById,
                          Map<String, Document> documentsById) {
        this.usersByUsername = new HashMap<>(usersByUsername);
        this.categoriesById = new HashMap<>(categoriesById);
        this.documentsById = new HashMap<>(documentsById);
        this.authService = new AuthService(this.usersByUsername);
        this.userService = new UserService(this.usersByUsername, this.categoriesById, this.documentsById);
        this.followService = new FollowService(this.documentsById);
        this.categoryService = new CategoryService(this.usersByUsername, this.categoriesById, this.documentsById, this.followService);
        this.documentService = new DocumentService(this.usersByUsername, this.categoriesById, this.documentsById, this.followService);
        if (!this.usersByUsername.containsKey("medialab")) {
            userService.bootstrapDefaultAdmin();
        }
    }

    /**
     * Adds a new user to the system.
     *
     * @param adminActor         the administrator performing the action
     * @param firstName          user's first name
     * @param lastName           user's last name
     * @param role               role name (SIMPLE, AUTHOR, ADMIN)
     * @param allowedCategoryIds category ids the user can access (non-admin must have at least one)
     * @param username           unique username
     * @param plainPassword      plain password to hash and store
     */
    public void addUser(Admin adminActor, String firstName, String lastName, String role,
                        Set<String> allowedCategoryIds, String username, String plainPassword) {
        userService.addUser(adminActor, firstName, lastName, role, allowedCategoryIds, username, plainPassword);
    }

    /**
     * Deletes an existing user from the system.
     *
     * @param adminActor the administrator performing the action
     * @param username   username to delete
     */
    public void deleteUser(Admin adminActor, String username) {
        userService.deleteUser(adminActor, username);
    }

    /**
     * Updates a user. Only the fields provided as {@link Optional#isPresent()} will be updated.
     * <p>
     * This operation may also cleanup followed documents if category access is reduced.
     *
     * @param adminActor              the administrator performing the action
     * @param targetUsername          the username of the user to update
     * @param newUsernameOpt          optional new username (must be unique)
     * @param newRoleOpt              optional new role (SIMPLE, AUTHOR, ADMIN)
     * @param newAllowedCategoryIdsOpt optional new set of allowed categories (validated to exist)
     * @param newPlainPasswordOpt     optional new plain password (will be hashed)
     */
    public void updateUser(Admin adminActor,
                           String targetUsername,
                           Optional<String> newUsernameOpt,
                           Optional<String> newRoleOpt,
                           Optional<Set<String>> newAllowedCategoryIdsOpt,
                           Optional<String> newPlainPasswordOpt) {
        userService.updateUser(adminActor, targetUsername, newUsernameOpt, newRoleOpt, newAllowedCategoryIdsOpt, newPlainPasswordOpt);
    }

    /**
     * Lists all users in the system.
     *
     * @param adminActor the administrator performing the action
     * @return immutable list of users
     */
    public List<User> listUsers(Admin adminActor) {
        return userService.listUsers(adminActor);
    }

    /**
     * Creates a new category.
     *
     * @param adminActor   the administrator performing the action
     * @param categoryName the category name
     */
    public void addCategory(Admin adminActor, String categoryName) {
        categoryService.addCategory(adminActor, categoryName);
    }

    /**
     * Renames an existing category.
     *
     * @param adminActor the administrator performing the action
     * @param categoryId the category id to rename
     * @param newName    the new category name
     */
    public void renameCategory(Admin adminActor, String categoryId, String newName) {
        categoryService.renameCategory(adminActor, categoryId, newName);
    }

    /**
     * Deletes a category and all documents within it.
     *
     * @param adminActor the administrator performing the action
     * @param categoryId the category id to delete
     */
    public void deleteCategory(Admin adminActor, String categoryId) {
        categoryService.deleteCategory(adminActor, categoryId);
    }

    /**
     * Creates a document with version 1.
     *
     * @param actor          author/admin performing the action
     * @param title          document title
     * @param categoryId     category id where the document belongs
     * @param initialContent initial document content
     */
    public void createDocument(Author actor, String title, String categoryId, String initialContent) {
        documentService.createDocument(actor, title, categoryId, initialContent);
    }

    /**
     * Updates document text by creating a new version.
     *
     * @param actor      author/admin performing the action
     * @param documentId document id
     * @param newContent new content for the next version
     */
    public void updateDocumentText(Author actor, String documentId, String newContent) {
        documentService.updateDocumentText(actor, documentId, newContent);
    }

    /**
     * Deletes a document and all its versions.
     *
     * @param actor      author/admin performing the action
     * @param documentId document id
     */
    public void deleteDocument(Author actor, String documentId) {
        documentService.deleteDocument(actor, documentId);
    }

    /**
     * Returns a document for viewing if the actor has access to its category.
     *
     * @param actor      user requesting the document
     * @param documentId document id
     * @return the document
     */
    public Document getDocumentForViewing(User actor, String documentId) {
        return documentService.getDocumentForViewing(actor, documentId);
    }

    /**
     * Returns the visible versions for an actor based on role limits.
     *
     * @param actor      user requesting versions
     * @param documentId document id
     * @return immutable list of visible versions (newest first)
     */
    public List<DocumentVersion> getVisibleVersions(User actor, String documentId) {
        return documentService.getVisibleVersions(actor, documentId);
    }

    /**
     * Searches documents by optional criteria (AND semantics) in categories accessible by actor.
     *
     * @param actor          user performing the search
     * @param categoryId     optional exact category id filter
     * @param titleContains  optional title fragment filter
     * @param authorUsername optional author username filter (may be unused by UI if it filters differently)
     * @return immutable list of matching documents
     */
    public List<Document> search(User actor, Optional<String> categoryId, Optional<String> titleContains, Optional<String> authorUsername) {
        return documentService.search(actor, categoryId, titleContains, authorUsername);
    }

    /**
     * Marks a document as followed by the given actor.
     *
     * @param actor      user who will follow the document
     * @param documentId document id
     */
    public void followDocument(User actor, String documentId) {
        followService.followDocument(actor, documentId, documentService);
    }

    /**
     * Removes a document from the actor's followed list.
     *
     * @param actor      user who will unfollow the document
     * @param documentId document id
     */
    public void unfollowDocument(User actor, String documentId) {
        followService.unfollowDocument(actor, documentId);
    }

    /**
     * Builds the login popup notification message (new versions and removals) and consumes
     * any pending "removed document" notifications for this user so they do not show again.
     *
     * @param actor logged-in user
     * @return popup message text, or {@code null} if there is nothing to notify
     */
    public String buildLoginPopupMessage(User actor) {
        return followService.buildPopupMessageAndConsume(actor, documentsById, categoriesById);
    }

    /**
     * Marks the latest version of the given document as seen by the user.
     *
     * @param actor      user marking the document as seen
     * @param documentId document id
     */
    public void markDocumentSeen(User actor, String documentId) {
        followService.markDocumentSeen(actor, documentId, documentService);
    }

    /**
     * Authenticates a user by username and plain password.
     *
     * @param username      username
     * @param plainPassword plain password
     * @return authenticated user
     */
    public User login(String username, String plainPassword) {
        return authService.login(username, plainPassword);
    }

    /**
     * Returns a snapshot of users mapped by username.
     *
     * @return unmodifiable map copy of users
     */
    public Map<String, User> getUsers() {
        return Collections.unmodifiableMap(new HashMap<>(usersByUsername));
    }

    /**
     * Returns a snapshot of categories mapped by id.
     *
     * @return unmodifiable map copy of categories
     */
    public Map<String, Category> getCategories() {
        return Collections.unmodifiableMap(new HashMap<>(categoriesById));
    }

    /**
     * Returns a snapshot of documents mapped by id.
     *
     * @return unmodifiable map copy of documents
     */
    public Map<String, Document> getDocuments() {
        return Collections.unmodifiableMap(new HashMap<>(documentsById));
    }

    /**
     * Storage record for a removed document notification entry.
     *
     * @param title        document title
     * @param categoryName category name at the time of removal
     */
    public record RemovedDocInfo(String title, String categoryName) {}

    /**
     * Exports pending "removed document" notifications for persistence.
     *
     * @return map keyed by username containing pending removal entries
     */
    public Map<String, List<RemovedDocInfo>> exportPendingRemoved() {
        return followService.exportPendingRemovedForStorage();
    }

    /**
     * Imports pending "removed document" notifications from persistence.
     *
     * @param data map keyed by username containing pending removal entries
     */
    public void importPendingRemoved(Map<String, List<RemovedDocInfo>> data) {
        followService.importPendingRemovedForStorage(data);
    }
}