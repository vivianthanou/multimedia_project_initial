package gr.ntua.multimedia.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Author extends SimpleUser {
    public Author(String username, String passwordHash, String firstName, String lastName) {
        super(username, passwordHash, firstName, lastName);
    }

    public Author(
            String username,
            String passwordHash,
            String firstName,
            String lastName,
            Set<String> allowedCategoryIds,
            Set<String> followedDocumentIds,
            Map<String, Integer> lastSeenVersionByDocId
    ) {
        super(username, passwordHash, firstName, lastName, allowedCategoryIds, followedDocumentIds, lastSeenVersionByDocId);
    }

    @Override
    public int maxVisibleVersions() {
        return 3;
    }

    @Override
    public String getRoleName() {
        return "AUTHOR";
    }

    public boolean canCreateInCategory(String categoryId) {
        return canAccessCategory(categoryId);
    }

    public boolean canEditDocument(Document doc) {
        Objects.requireNonNull(doc, "doc cannot be null");
        return getUsername().equals(doc.getAuthorUsername()) && canAccessCategory(doc.getCategoryId());
    }

    public boolean canDeleteDocument(Document doc) {
        return canEditDocument(doc);
    }
}