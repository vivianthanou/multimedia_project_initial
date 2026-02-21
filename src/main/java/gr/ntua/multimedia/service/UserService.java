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

    void bootstrapDefaultAdmin() {
        String hash = PasswordHasher.hash("medialab_2025");
        usersByUsername.put("medialab", new Admin("medialab", hash, "Media", "Lab", Set.of(), Set.of(), Map.of()));
    }
}