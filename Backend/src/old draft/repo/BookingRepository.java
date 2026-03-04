package repo;

import model.Booking;
import model.Client;
import model.Consultant;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BookingRepository {
    private final List<Booking> bookings = new ArrayList<>();

    public void save(Booking b) {
        bookings.removeIf(existing -> existing.getBookingId().equals(b.getBookingId()));
        bookings.add(b);
    }

    public Booking findById(String id) {
        return bookings.stream()
                .filter(b -> b.getBookingId().equals(id))
                .findFirst().orElse(null);
    }

    public List<Booking> findByClient(Client c) {
        return bookings.stream()
                .filter(b -> b.getClient().getId().equals(c.getId()))
                .collect(Collectors.toList());
    }

    public List<Booking> findByConsultant(Consultant con) {
        return bookings.stream()
                .filter(b -> b.getService().getConsultant().getId().equals(con.getId()))
                .collect(Collectors.toList());
    }

    public List<Booking> findAll() {
        return new ArrayList<>(bookings);
    }
}
