package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TimeSlot {
    private LocalDateTime start;
    private LocalDateTime end;
    private boolean isAvailable;
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public TimeSlot(LocalDateTime start, LocalDateTime end) {
        this.start       = start;
        this.end         = end;
        this.isAvailable = true;
    }

    public LocalDateTime getStart()       { return start; }
    public LocalDateTime getEnd()         { return end; }
    public boolean       isAvailable()    { return isAvailable; }

    public String getSlotId() { return start.toString(); }

    public void reserve() {
        this.isAvailable = false;
    }

    public void release() {
        this.isAvailable = true;
    }

    @Override
    public String toString() {
        return String.format("TimeSlot[%s → %s, available=%b]",
                start.format(FMT), end.format(FMT), isAvailable);
    }
}
