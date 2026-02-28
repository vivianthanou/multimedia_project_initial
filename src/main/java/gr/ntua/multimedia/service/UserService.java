package gr.ntua.multimedia.service;

import gr.ntua.multimedia.domain.Admin;
import gr.ntua.multimedia.domain.Author;
import gr.ntua.multimedia.domain.Category;
import gr.ntua.multimedia.domain.SimpleUser;
import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.exception.NotFoundException;
import gr.ntua.multimedia.exception.ValidationException;
import gr.ntua.multimedia.util.PasswordHasher;
import gr.ntua.multimedia.util.ValidationUtil;
import gr.ntua.multimedia.domain.Document;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class UserService {
    private final Map<String, User> usersByUsername;
    private final Map<String, Category> categoriesById;
    private final Map<String, Document> documentsById;
    private final FollowService followService;

    UserService(Map<String, User> usersByUsername,
                Map<String, Category> categoriesById,
                Map<String, Document> documentsById, FollowService followService) {
        this.usersByUsername = usersByUsername;
        this.categoriesById = categoriesById;
        this.documentsById = documentsById;
        this.followService = followService;
    }

    void addUser(Admin adminActor, String firstName, String lastName, String role,
                 Set<String> allowedCategoryIds, String username, String plainPassword) {
        AccessControl.requireAdmin(adminActor, usersByUsername);
        ValidationUtil.requireNonBlank(firstName, "firstName");
        ValidationUtil.requireNonBlank(lastName, "lastName");
        ValidationUtil.requireNonBlank(role, "role");
        ValidationUtil.requireNonBlank(username, "username");
        ValidationUtil.requireNonBlank(plainPassword, "plainPassword");

        if (usersByUsername.containsKey(username)) {
            throw new ValidationException("Username already exists: " + username);
        }

        Set<String> validatedAccess = new HashSet<>();
        for (String categoryId : Optional.ofNullable(allowedCategoryIds).orElseGet(Set::of)) {
            ValidationUtil.requireNonBlank(categoryId, "allowedCategoryId");
            if (!categoriesById.containsKey(categoryId)) {
                throw new NotFoundException("Category not found: " + categoryId);
            }
            validatedAccess.add(categoryId);
        }

        String normalizedRole = role.toUpperCase();
        if (!normalizedRole.equals("ADMIN") && validatedAccess.isEmpty()) {
            throw new ValidationException("Non-admin users must have access to at least one category");
        }

        String passwordHash = PasswordHasher.hash(plainPassword);
        User created = switch (normalizedRole) {
            case "SIMPLE" -> new SimpleUser(username, passwordHash, firstName, lastName, validatedAccess, Set.of(), Map.of());
            case "AUTHOR" -> new Author(username, passwordHash, firstName, lastName, validatedAccess, Set.of(), Map.of());
            case "ADMIN" -> new Admin(username, passwordHash, firstName, lastName, validatedAccess, Set.of(), Map.of());
            default -> throw new ValidationException("Unsupported role: " + role);
        };

        usersByUsername.put(username, created);
    }

    void deleteUser(Admin adminActor, String username) {
        AccessControl.requireAdmin(adminActor, usersByUsername);
        ValidationUtil.requireNonBlank(username, "username");
        if ("medialab".equals(username)) {
            throw new ValidationException("Default admin cannot be deleted");
        }
        if (usersByUsername.remove(username) == null) {
            throw new NotFoundException("User not found: " + username);
        }
        followService.clearNotificationsForUser(username);
    }

    List<User> listUsers(Admin adminActor) {
        AccessControl.requireAdmin(adminActor, usersByUsername);
        return Collections.unmodifiableList(new ArrayList<>(usersByUsername.values()));
    }
    void updateUser(Admin adminActor,
                    String targetUsername,
                    Optional<String> newUsernameOpt,
                    Optional<String> newRoleOpt,
                    Optional<Set<String>> newAllowedCategoryIdsOpt,
                    Optional<String> newPlainPasswordOpt) {

        AccessControl.requireAdmin(adminActor, usersByUsername);
        ValidationUtil.requireNonBlank(targetUsername, "targetUsername");

        User existing = usersByUsername.get(targetUsername);
        if (existing == null) {
            throw new NotFoundException("User not found: " + targetUsername);
        }

        if ("medialab".equals(targetUsername)) {
            throw new ValidationException("Default admin cannot be modified");
        }

        String finalUsername = existing.getUsername();
        if (newUsernameOpt != null && newUsernameOpt.isPresent()) {
            String candidate = newUsernameOpt.get().trim();
            if (candidate.isBlank()) {
                throw new ValidationException("username cannot be blank");
            }
            finalUsername = candidate;
        }

        if (!finalUsername.equals(existing.getUsername()) && usersByUsername.containsKey(finalUsername)) {
            throw new ValidationException("Username already exists: " + finalUsername);
        }

        String finalRole = existing.getRoleName();
        if (newRoleOpt != null && newRoleOpt.isPresent()) {
            String candidate = newRoleOpt.get().trim().toUpperCase();
            if (candidate.isBlank()) {
                throw new ValidationException("role cannot be blank");
            }
            if (!candidate.equals("SIMPLE") && !candidate.equals("AUTHOR") && !candidate.equals("ADMIN")) {
                throw new ValidationException("Unsupported role: " + candidate);
            }
            finalRole = candidate;
        }

        Set<String> finalAllowed = new HashSet<>(existing.getAllowedCategoryIds());
        if (newAllowedCategoryIdsOpt != null && newAllowedCategoryIdsOpt.isPresent()) {
            Set<String> incoming = newAllowedCategoryIdsOpt.get();
            Set<String> validated = new HashSet<>();
            for (String categoryId : Optional.ofNullable(incoming).orElseGet(Set::of)) {
                ValidationUtil.requireNonBlank(categoryId, "categoryId");
                if (!categoriesById.containsKey(categoryId)) {
                    throw new NotFoundException("Category not found: " + categoryId);
                }
                validated.add(categoryId);
            }
            finalAllowed = validated;
        }
        Set<String> finalFollowed = new HashSet<>(existing.getFollowedDocumentIds());
        Map<String, Integer> finalLastSeen = new HashMap<>(existing.getLastSeenVersionByDocId());

        if (newAllowedCategoryIdsOpt != null && newAllowedCategoryIdsOpt.isPresent()) {
            Set<String> oldAllowed = new HashSet<>(existing.getAllowedCategoryIds());

            Set<String> removedCategories = new HashSet<>(oldAllowed);
            removedCategories.removeAll(finalAllowed);

            if (!removedCategories.isEmpty()) {
                Set<String> toUnfollow = new HashSet<>();
                for (String docId : finalFollowed) {
                    Document d = documentsById.get(docId);
                    if (d == null) {
                        // document no longer exists -> cleanup follow as well
                        toUnfollow.add(docId);
                        continue;
                    }
                    if (removedCategories.contains(d.getCategoryId())) {
                        toUnfollow.add(docId);
                    }
                }

                for (String docId : toUnfollow) {
                    finalFollowed.remove(docId);
                    finalLastSeen.remove(docId);
                }
            }
        }

        if (!"ADMIN".equalsIgnoreCase(finalRole) && finalAllowed.isEmpty()) {
            throw new ValidationException("Non-admin users must have access to at least one category");
        }

        String finalPasswordHash = existing.getPasswordHash();
        if (newPlainPasswordOpt != null && newPlainPasswordOpt.isPresent()) {
            String p = newPlainPasswordOpt.get();
            if (p == null || p.isBlank()) {
                throw new ValidationException("password cannot be blank");
            }
            finalPasswordHash = PasswordHasher.hash(p);
        }

        User rebuilt = switch (finalRole) {
            case "ADMIN" -> new Admin(
                    finalUsername,
                    finalPasswordHash,
                    existing.getFirstName(),
                    existing.getLastName(),
                    finalAllowed,
                    finalFollowed,
                    finalLastSeen
            );
            case "AUTHOR" -> new Author(
                    finalUsername,
                    finalPasswordHash,
                    existing.getFirstName(),
                    existing.getLastName(),
                    finalAllowed,
                    finalFollowed,
                    finalLastSeen
            );
            case "SIMPLE" -> new SimpleUser(
                    finalUsername,
                    finalPasswordHash,
                    existing.getFirstName(),
                    existing.getLastName(),
                    finalAllowed,
                    finalFollowed,
                    finalLastSeen
            );
            default -> throw new ValidationException("Unsupported role: " + finalRole);
        };

        if (!finalUsername.equals(existing.getUsername())) {
            followService.renameNotificationsUserKey(existing.getUsername(), finalUsername);
            usersByUsername.remove(existing.getUsername());
        }

        usersByUsername.put(finalUsername, rebuilt);
    }


    void bootstrapDefaultAdmin() {
        String hash = PasswordHasher.hash("medialab_2025");
        usersByUsername.put("medialab", new Admin("medialab", hash, "Media", "Lab", Set.of(), Set.of(), Map.of()));
    }
}