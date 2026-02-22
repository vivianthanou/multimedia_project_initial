package gr.ntua.multimedia.service;

import gr.ntua.multimedia.domain.Document;
import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.util.ValidationUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import gr.ntua.multimedia.domain.Category;

final class FollowService {
    private final Map<String, Document> documentsById;
    private final Map<String, List<RemovedDocInfo>> pendingRemovedByUsername = new HashMap<>();

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
    void recordDocumentRemovalForFollowers(String docId, String title, String categoryName, Map<String, User> usersByUsername) {
        for (User u : usersByUsername.values()) {
            if (u.isFollowing(docId)) {
                pendingRemovedByUsername
                        .computeIfAbsent(u.getUsername(), k -> new ArrayList<>())
                        .add(new RemovedDocInfo(title, categoryName));
            }
        }
    }

    String buildPopupMessageAndConsume(User actor, Map<String, Document> documentsById, Map<String, Category> categoriesById) {

        // 1) Updated docs (new versions)
        List<String> updatedLines = new ArrayList<>();
        for (String docId : actor.getFollowedDocumentIds()) {
            Document doc = documentsById.get(docId);
            if (doc == null) continue;

            // αν ο actor δεν έχει πρόσβαση σε κατηγορία, το αγνοούμε
            if (!actor.canAccessCategory(doc.getCategoryId())) continue;

            if (actor.hasNewVersion(docId, doc.getLatestVersionNumber())) {
                Category c = categoriesById.get(doc.getCategoryId());
                String catName = (c != null) ? c.getName() : "<deleted:" + doc.getCategoryId() + ">";
                updatedLines.add(doc.getTitle() + " | " + catName);
            }
        }

        // 2) Removed docs (pending)
        List<RemovedDocInfo> removed = pendingRemovedByUsername.getOrDefault(actor.getUsername(), List.of());
        List<String> removedLines = removed.stream().map(RemovedDocInfo::asLine).toList();

        // consume removed so it won't show again
        if (!removed.isEmpty()) {
            pendingRemovedByUsername.remove(actor.getUsername());
        }

        if (updatedLines.isEmpty() && removedLines.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Notification!\n\n");

        if (!updatedLines.isEmpty()) {
            sb.append("There is a new version in ").append(updatedLines.size())
                    .append(" documents you follow. Specifically:\n");
            for (String line : updatedLines) sb.append("- ").append(line).append("\n");
            sb.append("\n");
        }

        if (!removedLines.isEmpty()) {
            sb.append("There are no longer available ").append(removedLines.size())
                    .append(" documents you follow. Specifically:\n");
            for (String line : removedLines) sb.append("- ").append(line).append("\n");
        }

        return sb.toString();
    }

    void markDocumentSeen(User actor, String documentId, DocumentService documentService) {
        Document doc = documentService.getDocumentForViewing(actor, documentId);
        actor.markSeen(doc.getId(), doc.getLatestVersionNumber());
    }
}