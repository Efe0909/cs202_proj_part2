package org.example.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A (city, province) delivery address; one per user is flagged as selected and drives the order city-restriction. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserAddress {
    private int     addressId;
    private int     userId;
    private String  city;
    private String  province;
    /** True when this row is the owning user's selected delivery address. */
    private boolean selected;

    public UserAddress() {}

    public UserAddress(int addressId, int userId, String city, String province) {
        this.addressId = addressId;
        this.userId    = userId;
        this.city      = city;
        this.province  = province;
    }

    public int     getAddressId() { return addressId; }
    public int     getUserId()    { return userId; }
    public String  getCity()      { return city; }
    public String  getProvince()  { return province; }
    public boolean isSelected()   { return selected; }

    public void setAddressId(int addressId) { this.addressId = addressId; }
    public void setUserId(int userId)       { this.userId    = userId; }
    public void setCity(String city)        { this.city      = city; }
    public void setProvince(String prov)    { this.province  = prov; }
    public void setSelected(boolean sel)    { this.selected  = sel; }

    @Override
    public String toString() { return city + ", " + province; }
}
