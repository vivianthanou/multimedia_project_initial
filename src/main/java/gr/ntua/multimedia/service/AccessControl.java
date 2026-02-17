package gr.ntua.multimedia.service;

import gr.ntua.multimedia.domain.Admin;
import gr.ntua.multimedia.domain.Author;
import gr.ntua.multimedia.domain.Category;
import gr.ntua.multimedia.domain.Document;
import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.exception.NotFoundException;
import gr.ntua.multimedia.exception.PermissionDeniedException;
import gr.ntua.multimedia.util.ValidationUtil;

import java.util.Map;

final class AccessControl {
    private AccessControl() {
    }

    static void requireAdmin(Admin adminActor, Map<String, User> usersByUsername) {
        if (adminActor == null || !(usersByUsername.get(adminActor.getUsername()) instanceof Admin)) {
            throw new PermissionDeniedException("Admin privileges required");
        }
    }

    static void requireAuthor(Author actor, Map<String, User> usersByUsername) {
        if (actor == null || !(usersByUsername.get(actor.getUsername()) instanceof Author)) {
            throw new PermissionDeniedException("Author privileges required");
        }
    }

    static Category findCategory(String categoryId, Map<String, Category> categoriesById) {
        ValidationUtil.requireNonBlank(categoryId, "categoryId");
        Category category = categoriesById.get(categoryId);
        if (category == null) {
            throw new NotFoundException("Category not found: " + categoryId);
        }
        return category;
    }

    static Document findDocument(String documentId, Map<String, Document> documentsById) {
        ValidationUtil.requireNonBlank(documentId, "documentId");
        Document doc = documentsById.get(documentId);
        if (doc == null) {
            throw new NotFoundException("Document not found: " + documentId);
        }
        return doc;
    }

    static void requireCategoryAccess(User actor, String categoryId) {
        if (!actor.canAccessCategory(categoryId)) {
            throw new PermissionDeniedException("No access to category: " + categoryId);
        }
    }
}