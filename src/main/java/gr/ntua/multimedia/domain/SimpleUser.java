package gr.ntua.multimedia.domain;

import java.util.Map;
import java.util.Set;

public class SimpleUser extends User {
    public SimpleUser(String username, String passwordHash, String firstName, String lastName) {
        super(username, passwordHash, firstName, lastName);
    }

    public SimpleUser(
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
        return 1;
    }

    @Override
    public String getRoleName() {
        return "SIMPLE";
    }
}