package repo;

import model.Consultant;
import model.RegistrationStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ConsultantRepository {
    private final List<Consultant> consultants = new ArrayList<>();

    public void save(Consultant c) {
        consultants.removeIf(existing -> existing.getId().equals(c.getId()));
        consultants.add(c);
    }

    public Consultant findById(String id) {
        return consultants.stream()
                .filter(c -> c.getId().equals(id))
                .findFirst().orElse(null);
    }

    public List<Consultant> findAll() {
        return new ArrayList<>(consultants);
    }

    public List<Consultant> findByStatus(RegistrationStatus status) {
        return consultants.stream()
                .filter(c -> c.getRegistrationStatus() == status)
                .collect(Collectors.toList());
    }
}
