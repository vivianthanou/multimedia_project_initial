package gr.ntua.multimedia.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Document {
    private final String id;
    private final String title;
    private final String categoryId;
    private final String authorUsername;
    private final LocalDateTime createdAt;
    private final List<DocumentVersion> versions;

    public Document(
            String id,
            String title,
            String categoryId,
            String authorUsername,
            LocalDateTime createdAt,
            String initialContent
    ) {
        this.id = requireNonBlank(id, "id");
        this.title = requireNonBlank(title, "title");
        this.categoryId = requireNonBlank(categoryId, "categoryId");
        this.authorUsername = requireNonBlank(authorUsername, "authorUsername");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");

        this.versions = new ArrayList<>();
        this.versions.add(new DocumentVersion(1, createdAt, initialContent));
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getAuthorUsername() {
        return authorUsername;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public int getLatestVersionNumber() {
        return getLatestVersion().getVersionNumber();
    }

    public DocumentVersion getLatestVersion() {
        return versions.get(versions.size() - 1);
    }

    public String getLatestContent() {
        return getLatestVersion().getContent();
    }

    public List<DocumentVersion> getVersions() {
        return Collections.unmodifiableList(versions);
    }

    // Returns up to maxCount versions, ordered from newest to oldest.
    public List<DocumentVersion> getLastVersions(int maxCount) {
        if (maxCount < 1) {
            throw new IllegalArgumentException("maxCount must be >= 1");
        }
        int fromIndex = Math.max(versions.size() - maxCount, 0);
        List<DocumentVersion> sub = versions.subList(fromIndex, versions.size());
        List<DocumentVersion> result = new ArrayList<>(sub);
        Collections.reverse(result);
        return Collections.unmodifiableList(result);
    }

    public void addNewVersion(String newContent, LocalDateTime timestamp) {
        int nextVersionNumber = getLatestVersionNumber() + 1;
        versions.add(new DocumentVersion(nextVersionNumber, Objects.requireNonNull(timestamp, "timestamp cannot be null"), newContent));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Document)) {
            return false;
        }
        Document document = (Document) o;
        return id.equals(document.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Document{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", categoryId='" + categoryId + '\'' +
                ", authorUsername='" + authorUsername + '\'' +
                ", createdAt=" + createdAt +
                ", latestVersion=" + getLatestVersionNumber() +
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