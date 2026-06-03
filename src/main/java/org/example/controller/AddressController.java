package org.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Manages user delivery addresses and the single selected address used for order placement. */
@RestController
@RequestMapping("/api/users")
public class AddressController {

    private static final Logger log = LoggerFactory.getLogger(AddressController.class);

    private final org.example.service.UserService userService;

    public AddressController(org.example.service.UserService userService) {
        this.userService = userService;
    }

    /** List a user's addresses (id, city, province, whether selected). */
    @GetMapping("/{userId}/addresses")
    public List<org.example.model.UserAddress> list(@PathVariable int userId) {
        return userService.listAddresses(userId);
    }

    /** Create a {city, province} address. Non-blank validated -> 400 on bad. */
    @PostMapping("/{userId}/addresses")
    public ResponseEntity<?> create(@PathVariable int userId,
                                    @RequestBody Map<String, Object> body) {
        String city     = ControllerInputs.requireString(body, "city");
        String province = ControllerInputs.requireString(body, "province");
        org.example.model.UserAddress created = userService.addAddress(userId, city, province);
        log.info("Added address {} for user {}", created.getAddressId(), userId);
        return ResponseEntity.ok(created);
    }

    /** Delete an owned address. Ownership-checked: must belong to userId. */
    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<?> delete(@PathVariable int addressId,
                                    @RequestParam int userId) {
        userService.deleteAddress(userId, addressId);
        log.info("Deleted address {} for user {}", addressId, userId);
        return ResponseEntity.ok().build();
    }

    /** Set the user's selected delivery address (must be one of their own). */
    @PutMapping("/{userId}/selected-address")
    public ResponseEntity<?> setSelected(@PathVariable int userId,
                                         @RequestBody Map<String, Object> body) {
        int addressId = ControllerInputs.requireInt(body, "addressId");
        userService.setSelectedAddress(userId, addressId);
        log.info("User {} selected address {}", userId, addressId);
        return ResponseEntity.ok().build();
    }
}
