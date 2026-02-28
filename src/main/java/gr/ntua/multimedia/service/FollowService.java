package gr.ntua.multimedia.service;

import gr.ntua.multimedia.domain.Document;
import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.util.ValidationUtil;
import java.util.ArrayList;
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
        actor.markSeen(doc.getId(), doc.getLatestVersionNumber());
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
        List<String> updatedLines = new ArrayList<>();
        for (String docId : actor.getFollowedDocumentIds()) {
            Document doc = documentsById.get(docId);
            if (doc == null) continue;
            if (!actor.canAccessCategory(doc.getCategoryId())) continue;
            if (actor.hasNewVersion(docId, doc.getLatestVersionNumber())) {
                Category c = categoriesById.get(doc.getCategoryId());
                String catName = (c != null) ? c.getName() : "<deleted:" + doc.getCategoryId() + ">";
                updatedLines.add(doc.getTitle() + " | " + catName);
            }
        }

        List<RemovedDocInfo> removed = pendingRemovedByUsername.getOrDefault(actor.getUsername(), List.of());
        List<String> removedLines = removed.stream().map(RemovedDocInfo::asLine).toList();
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

    Map<String, List<MediaLabSystem.RemovedDocInfo>> exportPendingRemovedForStorage() {
        Map<String, List<MediaLabSystem.RemovedDocInfo>> out = new HashMap<>();
        for (var e : pendingRemovedByUsername.entrySet()) {
            List<MediaLabSystem.RemovedDocInfo> list = new ArrayList<>();
            for (RemovedDocInfo info : e.getValue()) {
                list.add(new MediaLabSystem.RemovedDocInfo(info.title, info.categoryName));
            }
            out.put(e.getKey(), list);
        }
        return out;
    }

    void importPendingRemovedForStorage(Map<String, List<MediaLabSystem.RemovedDocInfo>> data) {
        pendingRemovedByUsername.clear();
        if (data == null) return;

        for (var e : data.entrySet()) {
            List<RemovedDocInfo> list = new ArrayList<>();
            for (MediaLabSystem.RemovedDocInfo info : e.getValue()) {
                list.add(new RemovedDocInfo(info.title(), info.categoryName()));
            }
            pendingRemovedByUsername.put(e.getKey(), list);
        }
    }
    void clearNotificationsForUser(String username) {
        pendingRemovedByUsername.remove(username);
    }
    void renameNotificationsUserKey(String oldUsername, String newUsername) {
        if (oldUsername.equals(newUsername)) return;
        List<RemovedDocInfo> items = pendingRemovedByUsername.remove(oldUsername);
        if (items == null || items.isEmpty()) return;

        pendingRemovedByUsername
                .computeIfAbsent(newUsername, k -> new ArrayList<>())
                .addAll(items);
    }
}