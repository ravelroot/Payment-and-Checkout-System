package dev.velo.user;

public enum UserRole {
    ADMIN, STAFF, CUSTOMER;

    public boolean isBackoffice() {
        return this == ADMIN || this == STAFF;
    }
}
