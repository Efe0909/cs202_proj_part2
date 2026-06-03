package org.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Manages user phone numbers. */
@RestController
@RequestMapping("/api/users")
public class PhoneController {

    private static final Logger log = LoggerFactory.getLogger(PhoneController.class);

    private final org.example.service.UserService userService;

    public PhoneController(org.example.service.UserService userService) {
        this.userService = userService;
    }

    /** Returns all phone numbers for a user. */
    @GetMapping("/{userId}/phones")
    public List<org.example.model.UserPhone> list(@PathVariable int userId) {
        return userService.listPhones(userId);
    }

    /** Adds a phone number for a user. */
    @PostMapping("/{userId}/phones")
    public ResponseEntity<?> create(@PathVariable int userId,
                                    @RequestBody Map<String, Object> body) {
        String phone = ControllerInputs.requireString(body, "phone");
        org.example.model.UserPhone created = userService.addPhone(userId, phone);
        log.info("Added phone {} for user {}", created.getPhoneId(), userId);
        return ResponseEntity.ok(created);
    }

    /** Deletes a phone number. */
    @DeleteMapping("/phones/{phoneId}")
    public ResponseEntity<?> delete(@PathVariable int phoneId,
                                    @RequestParam int userId) {
        userService.deletePhone(userId, phoneId);
        log.info("Deleted phone {} for user {}", phoneId, userId);
        return ResponseEntity.ok().build();
    }
}
