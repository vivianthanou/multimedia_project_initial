package gr.ntua.multimedia.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public class DocumentVersion {
    private final int versionNumber;
    private final LocalDateTime createdAt;
    private final String content;

    public DocumentVersion(int versionNumber, LocalDateTime createdAt, String content) {
        if (versionNumber < 1) {
            throw new IllegalArgumentException("versionNumber must be >= 1");
        }
        this.versionNumber = versionNumber;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.content = requireNonBlank(content, "content");
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "DocumentVersion{" +
                "versionNumber=" + versionNumber +
                ", createdAt=" + createdAt +
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