package org.example.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A phone number belonging to a user; a user may have multiple phones. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserPhone {
    private int    phoneId;
    private int    userId;
    private String phone;

    public UserPhone() {}

    public UserPhone(int phoneId, int userId, String phone) {
        this.phoneId = phoneId;
        this.userId  = userId;
        this.phone   = phone;
    }

    public int    getPhoneId() { return phoneId; }
    public int    getUserId()  { return userId; }
    public String getPhone()   { return phone; }

    public void setPhoneId(int phoneId) { this.phoneId = phoneId; }
    public void setUserId(int userId)   { this.userId  = userId; }
    public void setPhone(String phone)  { this.phone   = phone; }

    @Override
    public String toString() { return phone; }
}
