package gr.ntua.multimedia.ui.controller;

import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.exception.AuthException;
import gr.ntua.multimedia.service.MediaLabSystem;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class LoginController {
    private final MediaLabSystem system;

    public LoginController(MediaLabSystem system) { this.system = system; }

    public VBox createView(Consumer<User> onSuccess) {
        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        Button loginButton = new Button("Login");
        loginButton.setOnAction(e -> {
            try {
                User user = system.login(usernameField.getText(), passwordField.getText());
                onSuccess.accept(user);
            } catch (AuthException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage());
                alert.showAndWait();
            }
        });
        VBox v = new VBox(10, usernameField, passwordField, loginButton);
        v.setPadding(new Insets(16));
        return v;
    }
}