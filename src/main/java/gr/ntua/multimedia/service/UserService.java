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

    UserService(Map<String, User> usersByUsername, Map<String, Category> categoriesById) {
        this.usersByUsername = usersByUsername;
        this.categoriesById = categoriesById;
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
    }

    List<User> listUsers(Admin adminActor) {
        AccessControl.requireAdmin(adminActor, usersByUsername);
        return Collections.unmodifiableList(new ArrayList<>(usersByUsername.values()));
    }
    void updateUserCategories(Admin adminActor, String targetUsername, Set<String> newAllowedCategoryIds) {
        AccessControl.requireAdmin(adminActor, usersByUsername);
        ValidationUtil.requireNonBlank(targetUsername, "targetUsername");

        User existing = usersByUsername.get(targetUsername);
        if (existing == null) {
            throw new NotFoundException("User not found: " + targetUsername);
        }

        // Δεν επιτρέπουμε αλλαγές στον default admin (προαιρετικό αλλά καλό)
        if ("medialab".equals(targetUsername)) {
            throw new ValidationException("Default admin cannot be modified");
        }

        // Validate categories exist
        Set<String> validatedAccess = new HashSet<>();
        for (String categoryId : Optional.ofNullable(newAllowedCategoryIds).orElseGet(Set::of)) {
            ValidationUtil.requireNonBlank(categoryId, "categoryId");
            if (!categoriesById.containsKey(categoryId)) {
                throw new NotFoundException("Category not found: " + categoryId);
            }
            validatedAccess.add(categoryId);
        }

        // SIMPLE/AUTHOR must have at least 1 category (ADMIN can be empty)
        String role = existing.getRoleName();
        if (!"ADMIN".equalsIgnoreCase(role) && validatedAccess.isEmpty()) {
            throw new ValidationException("SIMPLE/AUTHOR users must have at least one allowed category.");
        }

        // Rebuild user object to avoid needing protected grant/revoke methods across packages
        // Keep: username, passwordHash, names, followed docs, lastSeen map
        User rebuilt = switch (role.toUpperCase()) {
            case "ADMIN" -> new Admin(
                    existing.getUsername(),
                    existing.getPasswordHash(),
                    existing.getFirstName(),
                    existing.getLastName(),
                    validatedAccess,
                    existing.getFollowedDocumentIds(),
                    existing.getLastSeenVersionByDocId()
            );
            case "AUTHOR" -> new Author(
                    existing.getUsername(),
                    existing.getPasswordHash(),
                    existing.getFirstName(),
                    existing.getLastName(),
                    validatedAccess,
                    existing.getFollowedDocumentIds(),
                    existing.getLastSeenVersionByDocId()
            );
            default -> new SimpleUser(
                    existing.getUsername(),
                    existing.getPasswordHash(),
                    existing.getFirstName(),
                    existing.getLastName(),
                    validatedAccess,
                    existing.getFollowedDocumentIds(),
                    existing.getLastSeenVersionByDocId()
            );
        };

        usersByUsername.put(targetUsername, rebuilt);
    }


    void bootstrapDefaultAdmin() {
        String hash = PasswordHasher.hash("medialab_2025");
        usersByUsername.put("medialab", new Admin("medialab", hash, "Media", "Lab", Set.of(), Set.of(), Map.of()));
    }
}