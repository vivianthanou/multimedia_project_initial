package gr.ntua.multimedia.service;

import gr.ntua.multimedia.domain.Admin;
import gr.ntua.multimedia.domain.Author;
import gr.ntua.multimedia.domain.Category;
import gr.ntua.multimedia.domain.Document;
import gr.ntua.multimedia.domain.DocumentVersion;
import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.exception.PermissionDeniedException;
import gr.ntua.multimedia.exception.ValidationException;
import gr.ntua.multimedia.util.DateTimeUtil;
import gr.ntua.multimedia.util.IdUtil;
import gr.ntua.multimedia.util.ValidationUtil;
import gr.ntua.multimedia.service.FollowService;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class DocumentService {
    private final Map<String, User> usersByUsername;
    private final Map<String, Category> categoriesById;
    private final Map<String, Document> documentsById;
    private final FollowService followService;

    DocumentService(Map<String, User> usersByUsername,
                    Map<String, Category> categoriesById,
                    Map<String, Document> documentsById,
                    FollowService followService) {
        this.usersByUsername = usersByUsername;
        this.categoriesById = categoriesById;
        this.documentsById = documentsById;
        this.followService = followService;
    }

    Document createDocument(Author actor, String title, String categoryId, String initialContent) {
        AccessControl.requireAuthor(actor, usersByUsername);
        ValidationUtil.requireNonBlank(categoryId, "categoryId");
        String normalizedTitle = title.trim();

        boolean exists = documentsById.values().stream()
                .anyMatch(d -> d.getCategoryId().equals(categoryId) &&
                        d.getTitle().equalsIgnoreCase(normalizedTitle));

        if (exists) {
            throw new ValidationException("A document with the same title already exists in this category.");
        }

        if (!actor.canCreateInCategory(categoryId)) {
            throw new PermissionDeniedException("No access to category: " + categoryId);
        }
        AccessControl.findCategory(categoryId, categoriesById);
        Document doc = new Document(IdUtil.newId(), title, categoryId, actor.getUsername(), DateTimeUtil.now(), initialContent);
        documentsById.put(doc.getId(), doc);
        return doc;
    }

    void updateDocumentText(Author actor, String documentId, String newContent) {
        AccessControl.requireAuthor(actor, usersByUsername);
        Document doc = AccessControl.findDocument(documentId, documentsById);
        if (!(actor.canEditDocument(doc) || actor instanceof Admin)) {
            throw new PermissionDeniedException("Not allowed to edit document");
        }
        doc.addNewVersion(newContent, DateTimeUtil.now());
    }

    void deleteDocument(Author actor, String documentId) {
        AccessControl.requireAuthor(actor, usersByUsername);
        Document doc = AccessControl.findDocument(documentId, documentsById);
        boolean canDelete = actor.canDeleteDocument(doc) || actor instanceof Admin;
        if (!canDelete) {
            throw new PermissionDeniedException("Not allowed to delete document");
        }
        Category c = categoriesById.get(doc.getCategoryId());
        String catName = (c != null) ? c.getName() : "<deleted:" + doc.getCategoryId() + ">";

        followService.recordDocumentRemovalForFollowers(doc.getId(), doc.getTitle(), catName, usersByUsername);
        documentsById.remove(doc.getId());
        usersByUsername.values().forEach(u -> u.unfollowDocument(doc.getId()));
    }

    Document getDocumentForViewing(User actor, String documentId) {
        ValidationUtil.requireNonNull(actor, "actor");
        Document doc = AccessControl.findDocument(documentId, documentsById);
        AccessControl.requireCategoryAccess(actor, doc.getCategoryId());
        return doc;
    }

    List<DocumentVersion> getVisibleVersions(User actor, String documentId) {
        Document doc = getDocumentForViewing(actor, documentId);
        return doc.getLastVersions(actor.maxVisibleVersions());
    }

    List<Document> search(User actor, Optional<String> categoryId, Optional<String> titleContains, Optional<String> authorUsername) {
        ValidationUtil.requireNonNull(actor, "actor");
        List<Document> results = documentsById.values().stream()
                .filter(d -> actor.canAccessCategory(d.getCategoryId()))
                .filter(d -> categoryId.isEmpty() || d.getCategoryId().equals(categoryId.get()))
                .filter(d -> titleContains.isEmpty() || d.getTitle().toLowerCase().contains(titleContains.get().toLowerCase()))
                .filter(d -> authorUsername.isEmpty() || d.getAuthorUsername().equals(authorUsername.get()))
                .toList();
        return Collections.unmodifiableList(results);
    }
}