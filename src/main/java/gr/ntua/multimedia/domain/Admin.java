package gr.ntua.multimedia.domain;

import java.util.Map;
import java.util.Set;

public class Admin extends Author {
    public Admin(String username, String passwordHash, String firstName, String lastName) {
        super(username, passwordHash, firstName, lastName);
    }

    public Admin(
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
        return "ADMIN";
    }

    public boolean canManageUsers() {
        return true;
    }

    public boolean canManageCategories() {
        return true;
    }
}