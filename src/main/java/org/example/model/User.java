package org.example.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
/** Represents a user with role CUSTOMER or MANAGER, and a selected delivery address. */
public class User {
    private int userId;
    private String username;
    private String password;
    private String email;
    private String fullName;
    private String role;
    private Integer selectedAddressId;
    private String selectedCity;

    public User() {
    }

    public User(int userId, String username, String password, String email,
            String fullName, String role) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
    }

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getRole() {
        return role;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public void setUsername(String u) {
        this.username = u;
    }

    public void setPassword(String p) {
        this.password = p;
    }

    public void setEmail(String e) {
        this.email = e;
    }

    public void setFullName(String fn) {
        this.fullName = fn;
    }

    public void setRole(String r) {
        this.role = r;
    }

    public Integer getSelectedAddressId() {
        return selectedAddressId;
    }

    public String getSelectedCity() {
        return selectedCity;
    }

    public void setSelectedAddressId(Integer id) {
        this.selectedAddressId = id;
    }

    public void setSelectedCity(String c) {
        this.selectedCity = c;
    }

    public boolean isManager() {
        return "MANAGER".equals(role);
    }

    public boolean isCustomer() {
        return "CUSTOMER".equals(role);
    }

    @Override
    public String toString() {
        return fullName + " (" + role + ")";
    }
}
