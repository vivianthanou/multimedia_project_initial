package gr.ntua.multimedia.service;

import gr.ntua.multimedia.domain.Document;
import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.util.ValidationUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class FollowService {
    private final Map<String, Document> documentsById;

    FollowService(Map<String, Document> documentsById) {
        this.documentsById = documentsById;
    }

    void followDocument(User actor, String documentId, DocumentService documentService) {
        Document doc = documentService.getDocumentForViewing(actor, documentId);
        actor.followDocument(doc.getId());
    }

    void unfollowDocument(User actor, String documentId) {
        ValidationUtil.requireNonNull(actor, "actor");
        actor.unfollowDocument(documentId);
    }

    List<String> getNotificationsOnLogin(User actor) {
        ValidationUtil.requireNonNull(actor, "actor");
        List<String> notifications = new ArrayList<>();
        for (String docId : actor.getFollowedDocumentIds()) {
            Document doc = documentsById.get(docId);
            if (doc == null) {
                continue;
            }
            if (!actor.canAccessCategory(doc.getCategoryId())) {
                continue;
            }
            if (actor.hasNewVersion(docId, doc.getLatestVersionNumber())) {
                notifications.add("Document '" + doc.getTitle() + "' has new version: " + doc.getLatestVersionNumber());
            }
        }
        return Collections.unmodifiableList(notifications);
    }

    void markDocumentSeen(User actor, String documentId, DocumentService documentService) {
        Document doc = documentService.getDocumentForViewing(actor, documentId);
        actor.markSeen(doc.getId(), doc.getLatestVersionNumber());
    }
}