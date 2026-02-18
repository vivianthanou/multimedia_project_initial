package gr.ntua.multimedia.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

public abstract class User {
    private final String username;
    private final String passwordHash;
    private final String firstName;
    private final String lastName;
    private final Set<String> allowedCategoryIds;
    private final Set<String> followedDocumentIds;
    private final Map<String, Integer> lastSeenVersionByDocId;

    protected User(
            String username,
            String passwordHash,
            String firstName,
            String lastName,
            Set<String> allowedCategoryIds,
            Set<String> followedDocumentIds,
            Map<String, Integer> lastSeenVersionByDocId
    ) {
        this.username = requireNonBlank(username, "username");
        this.passwordHash = requireNonBlank(passwordHash, "passwordHash");
        this.firstName = requireNonBlank(firstName, "firstName");
        this.lastName = requireNonBlank(lastName, "lastName");

        this.allowedCategoryIds = new HashSet<>();
        for (String categoryId : Objects.requireNonNull(allowedCategoryIds, "allowedCategoryIds cannot be null")) {
            this.allowedCategoryIds.add(requireNonBlank(categoryId, "allowedCategoryIds element"));
        }

        this.followedDocumentIds = new HashSet<>();
        for (String docId : Objects.requireNonNull(followedDocumentIds, "followedDocumentIds cannot be null")) {
            this.followedDocumentIds.add(requireNonBlank(docId, "followedDocumentIds element"));
        }

        this.lastSeenVersionByDocId = new HashMap<>();
        for (Map.Entry<String, Integer> entry : Objects.requireNonNull(lastSeenVersionByDocId, "lastSeenVersionByDocId cannot be null").entrySet()) {
            String docId = requireNonBlank(entry.getKey(), "lastSeenVersionByDocId key");
            Integer version = Objects.requireNonNull(entry.getValue(), "lastSeenVersionByDocId value cannot be null");
            if (version < 1) {
                throw new IllegalArgumentException("lastSeenVersion must be >= 1");
            }
            this.lastSeenVersionByDocId.put(docId, version);
        }
    }

    protected User(String username, String passwordHash, String firstName, String lastName) {
        this(username, passwordHash, firstName, lastName, Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());
    }

    public String getUsername() {
        return username;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Set<String> getFollowedDocumentIds() {
        return Collections.unmodifiableSet(followedDocumentIds);
    }

    public Set<String> getAllowedCategoryIds() {
        return Collections.unmodifiableSet(allowedCategoryIds);
    }

    public boolean canAccessCategory(String categoryId) {
        return allowedCategoryIds.contains(requireNonBlank(categoryId, "categoryId"));
    }

    protected void grantCategoryAccess(String categoryId) {
        allowedCategoryIds.add(requireNonBlank(categoryId, "categoryId"));
    }

    protected void revokeCategoryAccess(String categoryId) {
        allowedCategoryIds.remove(requireNonBlank(categoryId, "categoryId"));
    }

    public void followDocument(String documentId) {
        followedDocumentIds.add(requireNonBlank(documentId, "documentId"));
    }

    public void unfollowDocument(String documentId) {
        String docId = requireNonBlank(documentId, "documentId");
        followedDocumentIds.remove(docId);
        lastSeenVersionByDocId.remove(docId);
    }

    public boolean isFollowing(String documentId) {
        return followedDocumentIds.contains(requireNonBlank(documentId, "documentId"));
    }

    public void markSeen(String documentId, int versionNumber) {
        String docId = requireNonBlank(documentId, "documentId");
        if (versionNumber < 1) {
            throw new IllegalArgumentException("versionNumber must be >= 1");
        }
        lastSeenVersionByDocId.put(docId, versionNumber);
    }

    public OptionalInt lastSeenVersion(String documentId) {
        Integer version = lastSeenVersionByDocId.get(requireNonBlank(documentId, "documentId"));
        return version == null ? OptionalInt.empty() : OptionalInt.of(version);
    }

    public boolean hasNewVersion(String documentId, int currentVersionNumber) {
        String docId = requireNonBlank(documentId, "documentId");
        if (currentVersionNumber < 1) {
            throw new IllegalArgumentException("currentVersionNumber must be >= 1");
        }
        if (!isFollowing(docId)) {
            return false;
        }
        int lastSeen = lastSeenVersionByDocId.getOrDefault(docId, 0);
        return currentVersionNumber > lastSeen;
    }

    public abstract int maxVisibleVersions();

    public Map<String, Integer> getLastSeenVersionByDocId() {
        return Collections.unmodifiableMap(lastSeenVersionByDocId);
    }

    public abstract String getRoleName();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User)) {
            return false;
        }
        User user = (User) o;
        return username.equals(user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", role='" + getRoleName() + '\'' +
                ", allowedCategoryIds=" + allowedCategoryIds +
                '}';
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value;
    }
}