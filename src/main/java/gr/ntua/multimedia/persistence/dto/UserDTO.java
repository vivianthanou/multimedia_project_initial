package gr.ntua.multimedia.persistence.dto;

import java.util.List;
import java.util.Map;

public record UserDTO(
        String username,
        String passwordHash,
        String firstName,
        String lastName,
        String role,
        List<String> allowedCategoryIds,
        List<String> followedDocumentIds,
        Map<String, Integer> lastSeenVersionByDocId
) {}