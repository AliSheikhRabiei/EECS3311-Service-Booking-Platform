package com.platform.db;

import com.platform.domain.Consultant;
import com.platform.repository.ConsultantRepository;

import java.util.List;

/**
 * DB-backed replacement for the in-memory ConsultantRepository.
 * Delegates to UserRepository which owns the users + consultants tables.
 */
public class DbConsultantRepository extends ConsultantRepository {

    private final UserRepository userRepository;

    public DbConsultantRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void save(Consultant c) {
        // The user row must already exist. Just update the consultant profile.
        userRepository.saveConsultantProfile(
                c.getId(),
                c.getBio(),
                c.getRegistrationStatus().name());
    }

    @Override
    public Consultant findById(String id) {
        return userRepository.loadConsultant(id);
    }

    @Override
    public List<Consultant> findAll() {
        return userRepository.findAllConsultants();
    }
}
