package gr.ntua.multimedia.service;

import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.exception.AuthException;
import gr.ntua.multimedia.util.PasswordHasher;
import gr.ntua.multimedia.util.ValidationUtil;

import java.util.Map;

final class AuthService {
    private final Map<String, User> usersByUsername;

    AuthService(Map<String, User> usersByUsername) {
        this.usersByUsername = usersByUsername;
    }

    User login(String username, String plainPassword) {
        ValidationUtil.requireNonBlank(username, "username");
        ValidationUtil.requireNonBlank(plainPassword, "plainPassword");
        User user = usersByUsername.get(username);
        if (user == null || !PasswordHasher.matches(plainPassword, user.getPasswordHash())) {
            throw new AuthException("Invalid username or password");
        }
        return user;
    }
}