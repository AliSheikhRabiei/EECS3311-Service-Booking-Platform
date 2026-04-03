package com.platform.db;

import com.platform.state.*;

/**
 * Recreates the correct BookingState instance from the string stored in the DB.
 * The status strings match the booking lifecycle state names.
 */
public class BookingStateFactory {

    public static BookingState fromString(String status) {
        if (status == null) return new RequestedState();
        return switch (status.toUpperCase()) {
            case "REQUESTED"        -> new RequestedState();
            case "CONFIRMED"        -> new ConfirmedState();
            case "PENDING_PAYMENT",
                 "PENDINGPAYMENT"   -> new PendingPaymentState();
            case "PAID"             -> new PaidState();
            case "REJECTED"         -> new RejectedState();
            case "CANCELLED"        -> new CancelledState();
            case "COMPLETED"        -> new CompletedState();
            default                 -> new RequestedState();
        };
    }

    /** Maps a state class to the canonical DB string. */
    public static String toDbString(com.platform.state.BookingState state) {
        String name = state.getClass().getSimpleName();
        return switch (name) {
            case "RequestedState"       -> "REQUESTED";
            case "ConfirmedState"       -> "CONFIRMED";
            case "PendingPaymentState"  -> "PENDING_PAYMENT";
            case "PaidState"            -> "PAID";
            case "RejectedState"        -> "REJECTED";
            case "CancelledState"       -> "CANCELLED";
            case "CompletedState"       -> "COMPLETED";
            default                     -> "REQUESTED";
        };
    }
}
