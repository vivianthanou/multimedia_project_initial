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
import java.util.*;

public class MediaLabStorage {

    private final Path folder;
    private final Path usersPath;
    private final Path categoriesPath;
    private final Path documentsPath;
    private final Path notificationsPath;

    public MediaLabStorage(Path folder) {
        this.folder = folder;
        this.usersPath = folder.resolve("users.json");
        this.categoriesPath = folder.resolve("categories.json");
        this.documentsPath = folder.resolve("documents.json");
        this.notificationsPath = folder.resolve("notifications.json");
    }

    public void save(MediaLabSystem system) {
        try {
            Files.createDirectories(folder);

            Files.writeString(usersPath, SimpleJson.stringifyPretty(usersToJson(system)));
            Files.writeString(categoriesPath, SimpleJson.stringifyPretty(categoriesToJson(system)));
            Files.writeString(documentsPath, SimpleJson.stringifyPretty(documentsToJson(system)));

            Map<String, Object> notifRoot = new HashMap<>();
            notifRoot.put("pendingRemovedByUsername", exportPendingRemoved(system));
            Files.writeString(notificationsPath, SimpleJson.stringifyPretty(notifRoot));

        } catch (IOException | RuntimeException e) {
            throw new StorageException("Failed to save data", e);
        }
    }

    public MediaLabSystem loadOrCreateDefault() {
        try {
            Files.createDirectories(folder);

            boolean hasCore =
                    Files.exists(usersPath) &&
                            Files.exists(categoriesPath) &&
                            Files.exists(documentsPath);

            if (!hasCore) {
                MediaLabSystem system = new MediaLabSystem();
                save(system);
                return system;
            }

            Object usersParsed = SimpleJson.parse(Files.readString(usersPath));
            Object categoriesParsed = SimpleJson.parse(Files.readString(categoriesPath));
            Object documentsParsed = SimpleJson.parse(Files.readString(documentsPath));

            List<UserDTO> users = parseUsers(castList(usersParsed));
            List<CategoryDTO> categories = parseCategories(castList(categoriesParsed));
            List<DocumentDTO> documents = parseDocuments(castList(documentsParsed));

            MediaLabSystem system = rebuildSystem(users, categories, documents);

            if (Files.exists(notificationsPath)) {
                Object notifParsed = SimpleJson.parse(Files.readString(notificationsPath));
                Map<String, Object> notifRoot = castMap(notifParsed);
                Map<String, Object> pendingRaw =
                        castMap(notifRoot.getOrDefault("pendingRemovedByUsername", Map.of()));
                system.importPendingRemoved(parsePendingRemoved(pendingRaw));
            } else {
                system.importPendingRemoved(Map.of());
            }

            return system;

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
            List<DocumentVersionDTO> versions = new ArrayList<>(d.versions());
            versions.sort((a, b) -> Integer.compare(a.versionNumber(), b.versionNumber()));
            if (versions.isEmpty()) continue;

            DocumentVersionDTO first = versions.get(0);
            Document doc = new Document(
                    d.id(), d.title(), d.categoryId(), d.authorUsername(),
                    LocalDateTime.parse(d.createdAt()),
                    first.content()
            );
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
        Map<String, Document> docs = system.getDocuments();
        Map<String, Category> cats = system.getCategories();

        for (User user : system.getUsers().values()) {
            Map<String, Object> m = new HashMap<>();
            m.put("username", user.getUsername());
            m.put("passwordHash", user.getPasswordHash());
            m.put("firstName", user.getFirstName());
            m.put("lastName", user.getLastName());
            m.put("role", user.getRoleName());
            List<String> allowedIds = new ArrayList<>(user.getAllowedCategoryIds());
            List<String> followedIds = new ArrayList<>(user.getFollowedDocumentIds());
            // Readability-only: allowedCategories = [{id,name}]
            List<Object> allowedReadable = new ArrayList<>();
            for (String catId : allowedIds) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", catId);
                Category c = cats.get(catId);
                item.put("name", (c != null) ? c.getName() : "<deleted>");
                allowedReadable.add(item);
            }
            m.put("allowedCategories", allowedReadable);
           // Readability-only: followedDocuments = [{id,title}]
            List<Object> followedReadable = new ArrayList<>();
            for (String docId : followedIds) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", docId);
                Document d = docs.get(docId);
                item.put("title", (d != null) ? d.getTitle() : "<deleted>");
                followedReadable.add(item);
            }
            m.put("followedDocuments", followedReadable);

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
            List<String> allowedIds = new ArrayList<>();
            for (Object o : (List<Object>) m.getOrDefault("allowedCategories", List.of())) {
                Map<String, Object> mm = castMap(o);
                Object id = mm.get("id");
                if (id != null) allowedIds.add((String) id);
            }

            List<String> followedIds = new ArrayList<>();
            for (Object o : (List<Object>) m.getOrDefault("followedDocuments", List.of())) {
                Map<String, Object> mm = castMap(o);
                Object id = mm.get("id");
                if (id != null) followedIds.add((String) id);
            }
            out.add(new UserDTO(
                    (String) m.get("username"),
                    (String) m.get("passwordHash"),
                    (String) m.get("firstName"),
                    (String) m.get("lastName"),
                    (String) m.get("role"),
                    allowedIds,
                    followedIds,
                    seen
            ));
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
                versions.add(new DocumentVersionDTO(
                        ((Number) vm.get("versionNumber")).intValue(),
                        (String) vm.get("createdAt"),
                        (String) vm.get("content")
                ));
            }
            out.add(new DocumentDTO(
                    (String) m.get("id"),
                    (String) m.get("title"),
                    (String) m.get("categoryId"),
                    (String) m.get("authorUsername"),
                    (String) m.get("createdAt"),
                    versions
            ));
        }
        return out;
    }

    private Map<String, Object> exportPendingRemoved(MediaLabSystem system) {
        Map<String, List<MediaLabSystem.RemovedDocInfo>> pending = system.exportPendingRemoved();

        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, List<MediaLabSystem.RemovedDocInfo>> e : pending.entrySet()) {
            List<Object> arr = new ArrayList<>();
            for (MediaLabSystem.RemovedDocInfo info : e.getValue()) {
                Map<String, Object> m = new HashMap<>();
                m.put("title", info.title());
                m.put("categoryName", info.categoryName());
                arr.add(m);
            }
            out.put(e.getKey(), arr);
        }
        return out;
    }

    private Map<String, List<MediaLabSystem.RemovedDocInfo>> parsePendingRemoved(Map<String, Object> pendingRaw) {
        Map<String, List<MediaLabSystem.RemovedDocInfo>> out = new HashMap<>();
        for (Map.Entry<String, Object> e : pendingRaw.entrySet()) {
            String username = e.getKey();
            List<Object> arr = (List<Object>) e.getValue();
            List<MediaLabSystem.RemovedDocInfo> infos = new ArrayList<>();
            for (Object o : arr) {
                Map<String, Object> m = castMap(o);
                infos.add(new MediaLabSystem.RemovedDocInfo(
                        (String) m.get("title"),
                        (String) m.get("categoryName")
                ));
            }
            out.put(username, infos);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) { return (Map<String, Object>) value; }

    @SuppressWarnings("unchecked")
    private List<Object> castList(Object value) { return (List<Object>) value; }
}