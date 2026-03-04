package com.platform.domain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a consultant's available time interval for a session.
 * Provides reserve/release operations so BookingService can lock slots.
 */
public class TimeSlot {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final LocalDateTime start;
    private final LocalDateTime end;
    private boolean isAvailable;

    public TimeSlot(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) throw new IllegalArgumentException("Start and end must not be null.");
        if (!end.isAfter(start))          throw new IllegalArgumentException("End must be after start.");
        this.start       = start;
        this.end         = end;
        this.isAvailable = true;
    }

    /** Marks this slot as reserved. Safe to call multiple times (idempotent). */
    public void reserve() {
        this.isAvailable = false;
    }

    /** Returns this slot to the available pool; called on cancel or reject. */
    public void release() {
        this.isAvailable = true;
    }

    public LocalDateTime getStart()    { return start; }
    public LocalDateTime getEnd()      { return end; }
    public boolean       isAvailable() { return isAvailable; }

    /** Stable string ID derived from start time (unique per consultant schedule). */
    public String getSlotId() { return start.toString(); }

    @Override
    public String toString() {
        return String.format("TimeSlot[%s → %s, available=%b]",
                start.format(FMT), end.format(FMT), isAvailable);
    }
}
