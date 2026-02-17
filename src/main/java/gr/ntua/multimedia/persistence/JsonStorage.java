package gr.ntua.multimedia.persistence;

import gr.ntua.multimedia.domain.Admin;
import gr.ntua.multimedia.domain.Author;
import gr.ntua.multimedia.domain.Category;
import gr.ntua.multimedia.domain.Document;
import gr.ntua.multimedia.domain.DocumentVersion;
import gr.ntua.multimedia.domain.SimpleUser;
import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.exception.StorageException;
import gr.ntua.multimedia.persistence.dto.CategoryDTO;
import gr.ntua.multimedia.persistence.dto.DocumentDTO;
import gr.ntua.multimedia.persistence.dto.DocumentVersionDTO;
import gr.ntua.multimedia.persistence.dto.UserDTO;
import gr.ntua.multimedia.service.MediaLabSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JsonStorage {
    private final Path filePath;

    public JsonStorage(Path filePath) {
        this.filePath = filePath;
    }

    public void save(MediaLabSystem system) {
        try {
            Map<String, Object> root = new HashMap<>();
            root.put("users", usersToJson(system));
            root.put("categories", categoriesToJson(system));
            root.put("documents", documentsToJson(system));
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, SimpleJson.stringify(root));
        } catch (IOException | RuntimeException e) {
            throw new StorageException("Failed to save data", e);
        }
    }

    public MediaLabSystem loadOrCreateDefault() {
        if (!Files.exists(filePath)) {
            MediaLabSystem system = new MediaLabSystem();
            save(system);
            return system;
        }
        try {
            Object parsed = SimpleJson.parse(Files.readString(filePath));
            Map<String, Object> root = castMap(parsed);
            List<UserDTO> users = parseUsers((List<Object>) root.getOrDefault("users", List.of()));
            List<CategoryDTO> categories = parseCategories((List<Object>) root.getOrDefault("categories", List.of()));
            List<DocumentDTO> documents = parseDocuments((List<Object>) root.getOrDefault("documents", List.of()));
            return rebuildSystem(users, categories, documents);
        } catch (Exception e) {
            throw new StorageException("Failed to load data", e);
        }
    }

    private MediaLabSystem rebuildSystem(List<UserDTO> users, List<CategoryDTO> categories, List<DocumentDTO> documents) {
        Map<String, User> userMap = new HashMap<>();
        for (UserDTO u : users) {
            Set<String> allowed = Set.copyOf(u.allowedCategoryIds());
            Set<String> followed = Set.copyOf(u.followedDocumentIds());
            Map<String, Integer> seen = new HashMap<>(u.lastSeenVersionByDocId());
            User user = switch (u.role()) {
                case "ADMIN" -> new Admin(u.username(), u.passwordHash(), u.firstName(), u.lastName(), allowed, followed, seen);
                case "AUTHOR" -> new Author(u.username(), u.passwordHash(), u.firstName(), u.lastName(), allowed, followed, seen);
                default -> new SimpleUser(u.username(), u.passwordHash(), u.firstName(), u.lastName(), allowed, followed, seen);
            };
            userMap.put(user.getUsername(), user);
        }

        Map<String, Category> categoryMap = new HashMap<>();
        for (CategoryDTO c : categories) {
            categoryMap.put(c.id(), new Category(c.id(), c.name()));
        }

        Map<String, Document> documentMap = new HashMap<>();
        for (DocumentDTO d : documents) {
            List<DocumentVersionDTO> versions = d.versions();
            versions.sort((a, b) -> Integer.compare(a.versionNumber(), b.versionNumber()));
            if (versions.isEmpty()) continue;
            DocumentVersionDTO first = versions.get(0);
            Document doc = new Document(d.id(), d.title(), d.categoryId(), d.authorUsername(), LocalDateTime.parse(d.createdAt()), first.content());
            for (int i = 1; i < versions.size(); i++) {
                DocumentVersionDTO v = versions.get(i);
                doc.addNewVersion(v.content(), LocalDateTime.parse(v.createdAt()));
            }
            documentMap.put(doc.getId(), doc);
        }
        return new MediaLabSystem(userMap, categoryMap, documentMap);
    }

    private List<Object> usersToJson(MediaLabSystem system) {
        List<Object> list = new ArrayList<>();
        for (User user : system.getUsers().values()) {
            Map<String, Object> m = new HashMap<>();
            m.put("username", user.getUsername());
            m.put("passwordHash", user.getPasswordHash());
            m.put("firstName", user.getFirstName());
            m.put("lastName", user.getLastName());
            m.put("role", user.getRoleName());
            m.put("allowedCategoryIds", new ArrayList<>(user.getAllowedCategoryIds()));
            m.put("followedDocumentIds", new ArrayList<>(user.getFollowedDocumentIds()));
            m.put("lastSeenVersionByDocId", new HashMap<>(user.getLastSeenVersionByDocId()));
            list.add(m);
        }
        return list;
    }

    private List<Object> categoriesToJson(MediaLabSystem system) {
        List<Object> list = new ArrayList<>();
        for (Category c : system.getCategories().values()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            list.add(m);
        }
        return list;
    }

    private List<Object> documentsToJson(MediaLabSystem system) {
        List<Object> list = new ArrayList<>();
        for (Document d : system.getDocuments().values()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", d.getId());
            m.put("title", d.getTitle());
            m.put("categoryId", d.getCategoryId());
            m.put("authorUsername", d.getAuthorUsername());
            m.put("createdAt", d.getCreatedAt().toString());
            List<Object> versions = new ArrayList<>();
            for (DocumentVersion v : d.getVersions()) {
                Map<String, Object> vm = new HashMap<>();
                vm.put("versionNumber", v.getVersionNumber());
                vm.put("createdAt", v.getCreatedAt().toString());
                vm.put("content", v.getContent());
                versions.add(vm);
            }
            m.put("versions", versions);
            list.add(m);
        }
        return list;
    }

    private List<UserDTO> parseUsers(List<Object> values) {
        List<UserDTO> out = new ArrayList<>();
        for (Object v : values) {
            Map<String, Object> m = castMap(v);
            Map<String, Integer> seen = new HashMap<>();
            Map<String, Object> seenRaw = castMap(m.getOrDefault("lastSeenVersionByDocId", Map.of()));
            for (Map.Entry<String, Object> e : seenRaw.entrySet()) {
                seen.put(e.getKey(), ((Number) e.getValue()).intValue());
            }
            out.add(new UserDTO(
                    (String) m.get("username"),
                    (String) m.get("passwordHash"),
                    (String) m.get("firstName"),
                    (String) m.get("lastName"),
                    (String) m.get("role"),
                    castStringList(m.getOrDefault("allowedCategoryIds", List.of())),
                    castStringList(m.getOrDefault("followedDocumentIds", List.of())),
                    seen));
        }
        return out;
    }

    private List<CategoryDTO> parseCategories(List<Object> values) {
        List<CategoryDTO> out = new ArrayList<>();
        for (Object v : values) {
            Map<String, Object> m = castMap(v);
            out.add(new CategoryDTO((String) m.get("id"), (String) m.get("name")));
        }
        return out;
    }

    private List<DocumentDTO> parseDocuments(List<Object> values) {
        List<DocumentDTO> out = new ArrayList<>();
        for (Object v : values) {
            Map<String, Object> m = castMap(v);
            List<DocumentVersionDTO> versions = new ArrayList<>();
            for (Object vv : (List<Object>) m.getOrDefault("versions", List.of())) {
                Map<String, Object> vm = castMap(vv);
                versions.add(new DocumentVersionDTO(((Number) vm.get("versionNumber")).intValue(), (String) vm.get("createdAt"), (String) vm.get("content")));
            }
            out.add(new DocumentDTO((String) m.get("id"), (String) m.get("title"), (String) m.get("categoryId"), (String) m.get("authorUsername"), (String) m.get("createdAt"), versions));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) { return (Map<String, Object>) value; }

    @SuppressWarnings("unchecked")
    private List<String> castStringList(Object value) {
        List<Object> raw = (List<Object>) value;
        List<String> out = new ArrayList<>();
        for (Object o : raw) out.add((String) o);
        return out;
    }
}