package org.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/** Handles user login and registration. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final org.example.service.UserService userService;

    public AuthController(org.example.service.UserService userService) {
        this.userService = userService;
    }

    /** Authenticates a user and returns the user object on success. */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = ControllerInputs.requireString(body, "username");
        String password = ControllerInputs.requireString(body, "password");
        Optional<org.example.model.User> user = userService.login(username, password);
        if (user.isPresent()) {
            log.info("Login success: {}", username);
            return ResponseEntity.ok(user.get());
        }
        log.warn("Login failed for username: {}", username);
        return ResponseEntity.status(401).body("Invalid credentials");
    }

    /** Registers a new user with an initial delivery address. */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        org.example.model.User user = userService.register(
                ControllerInputs.requireString(body, "username"),
                ControllerInputs.requireString(body, "password"),
                ControllerInputs.requireString(body, "email"),
                ControllerInputs.requireString(body, "fullName"),
                ControllerInputs.optString(body, "role", "CUSTOMER"),
                ControllerInputs.requireString(body, "city"),
                ControllerInputs.requireString(body, "province")
        );
        log.info("Registered new user: {}", user.getUsername());
        return ResponseEntity.ok(user);
    }
}
