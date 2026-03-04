package com.platform.domain;

/**
 * A user who browses services and requests bookings (UC1–UC7).
 * Business behaviour is coordinated through the service layer.
 */
public class Client extends User {

    public Client(String id, String name, String email) {
        super(id, name, email);
    }
}
