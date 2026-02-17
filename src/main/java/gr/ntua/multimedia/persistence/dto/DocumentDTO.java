package gr.ntua.multimedia.persistence.dto;

import java.util.List;

public record DocumentDTO(
        String id,
        String title,
        String categoryId,
        String authorUsername,
        String createdAt,
        List<DocumentVersionDTO> versions
) {}