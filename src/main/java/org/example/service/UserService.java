package org.example.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** User registration, authentication, and multi-valued address and phone management. */
@Service
public class UserService {

    private final org.example.dao.UserDAO userDAO;

    public UserService(org.example.dao.UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /** Authenticates a user by username/password; returns empty if credentials are invalid. */
    public Optional<org.example.model.User> login(String username, String password) {
        Optional<String> saltOpt = userDAO.findSaltByUsername(username);
        if (saltOpt.isEmpty())
            return Optional.empty();

        Optional<org.example.model.User> userOpt = userDAO.findByUsername(username);
        if (userOpt.isEmpty())
            return Optional.empty();

        org.example.model.User user = userOpt.get();
        if (org.example.util.PasswordUtil.verify(password, saltOpt.get(), user.getPassword())) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** Registers a new user, atomically creating the initial delivery address and selecting it. */
    @Transactional
    public org.example.model.User register(String username, String password, String email,
            String fullName, String role,
            String city, String province) {
        if (userDAO.usernameExists(username)) {
            throw new IllegalArgumentException("Username already taken: " + username);
        }
        if (userDAO.emailExists(email)) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("Delivery city must not be blank.");
        }
        if (province == null || province.isBlank()) {
            throw new IllegalArgumentException("Province must not be blank.");
        }
        String c = city.trim();
        String p = province.trim();

        String salt = org.example.util.PasswordUtil.generateSalt();
        String hashedPassword = org.example.util.PasswordUtil.hash(password, salt);

        org.example.model.User user = new org.example.model.User();
        user.setUsername(username);
        user.setPassword(hashedPassword);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(role);

        int userId = userDAO.insert(user, salt);
        user.setUserId(userId);

        int addressId = userDAO.addAddress(userId, c, p);
        userDAO.setSelectedAddress(userId, addressId);
        user.setSelectedAddressId(addressId);
        user.setSelectedCity(c);
        return user;
    }

    /** Looks up a user by primary key. */
    public Optional<org.example.model.User> findById(int userId) {
        return userDAO.findById(userId);
    }

    /** Returns all addresses for a user. */
    public List<org.example.model.UserAddress> listAddresses(int userId) {
        if (userDAO.findById(userId).isEmpty())
            throw new IllegalArgumentException("Unknown user id: " + userId);
        return userDAO.findAddressesByUserId(userId);
    }

    /** Adds an address for a user; auto-selects it if the user has no current selection. */
    public org.example.model.UserAddress addAddress(int userId, String city, String province) {
        if (userDAO.findById(userId).isEmpty())
            throw new IllegalArgumentException("Unknown user id: " + userId);
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("City must not be blank.");
        }
        if (province == null || province.isBlank()) {
            throw new IllegalArgumentException("Province must not be blank.");
        }
        String c = city.trim();
        String p = province.trim();
        int id = userDAO.addAddress(userId, c, p);
        boolean nowSelected = false;
        if (userDAO.findSelectedAddressId(userId).isEmpty()) {
            userDAO.setSelectedAddress(userId, id);
            nowSelected = true;
        }
        org.example.model.UserAddress a = new org.example.model.UserAddress(id, userId, c, p);
        a.setSelected(nowSelected);
        return a;
    }

    /** Deletes an address; if it was the selected one, repoints selection to another address. */
    public void deleteAddress(int userId, int addressId) {
        if (userDAO.findById(userId).isEmpty())
            throw new IllegalArgumentException("Unknown user id: " + userId);
        if (!userDAO.addressBelongsToUser(addressId, userId)) {
            throw new IllegalArgumentException(
                    "Address " + addressId + " does not belong to user " + userId + ".");
        }
        
        Optional<Integer> selectedId = userDAO.findSelectedAddressId(userId);
        boolean wasSelected = selectedId.isPresent() && selectedId.get() == addressId;
        userDAO.deleteAddress(addressId);
        // FK ON DELETE SET NULL already cleared the selection; repoint to keep a sane default.
        if (wasSelected) {
            List<org.example.model.UserAddress> remaining = userDAO.findAddressesByUserId(userId);
            if (!remaining.isEmpty()) {
                userDAO.setSelectedAddress(userId, remaining.get(0).getAddressId());
            }
        }
    }

    /** Sets the selected delivery address for a user. */
    public void setSelectedAddress(int userId, int addressId) {
        if (userDAO.findById(userId).isEmpty())
            throw new IllegalArgumentException("Unknown user id: " + userId);
        if (!userDAO.addressBelongsToUser(addressId, userId)) {
            throw new IllegalArgumentException(
                    "Address " + addressId + " does not belong to user " + userId + ".");
        }
        userDAO.setSelectedAddress(userId, addressId);
    }

    /** Returns all phone numbers for a user. */
    public List<org.example.model.UserPhone> listPhones(int userId) {
        if (userDAO.findById(userId).isEmpty())
            throw new IllegalArgumentException("Unknown user id: " + userId);
        return userDAO.findPhonesByUserId(userId);
    }

    /** Adds a phone number for a user. */
    public org.example.model.UserPhone addPhone(int userId, String phone) {
        if (userDAO.findById(userId).isEmpty())
            throw new IllegalArgumentException("Unknown user id: " + userId);
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone must not be blank.");
        }
        String p = phone.trim();
        if (p.length() > 20) {
            throw new IllegalArgumentException("Phone must be 20 characters or fewer.");
        }
        int id = userDAO.addPhone(userId, p);
        return new org.example.model.UserPhone(id, userId, p);
    }

    /** Deletes a phone number after verifying it belongs to the user. */
    public void deletePhone(int userId, int phoneId) {
        if (userDAO.findById(userId).isEmpty())
            throw new IllegalArgumentException("Unknown user id: " + userId);
        if (!userDAO.phoneBelongsToUser(phoneId, userId)) {
            throw new IllegalArgumentException(
                    "Phone " + phoneId + " does not belong to user " + userId + ".");
        }
        userDAO.deletePhone(phoneId);
    }

}
