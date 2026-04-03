package com.platform.db;

import com.platform.domain.Consultant;
import com.platform.domain.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DB access for the services table.
 * Reconstructs Service domain objects (which need a Consultant reference).
 */
public class ServiceRepository {

    private final UserRepository userRepository;

    public ServiceRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void save(Service s) {
        String sql = """
                INSERT INTO services (id, title, description, duration_min, price, consultant_id)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE
                SET title = EXCLUDED.title, description = EXCLUDED.description,
                    duration_min = EXCLUDED.duration_min, price = EXCLUDED.price
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.getServiceId());
            ps.setString(2, s.getTitle());
            ps.setString(3, s.getDescription());
            ps.setInt(4, s.getDurationMin());
            ps.setDouble(5, s.getPrice());
            ps.setString(6, s.getConsultant().getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("ServiceRepository.save failed: " + e.getMessage(), e);
        }
    }

    public Service findById(String id) {
        String sql = "SELECT * FROM services WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("ServiceRepository.findById failed: " + e.getMessage(), e);
        }
    }

    public List<Service> findAll() {
        String sql = "SELECT * FROM services ORDER BY title";
        List<Service> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("ServiceRepository.findAll failed: " + e.getMessage(), e);
        }
        return list;
    }

    public List<Service> findByConsultantId(String consultantId) {
        String sql = "SELECT * FROM services WHERE consultant_id = ? ORDER BY title";
        List<Service> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, consultantId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findByConsultantId failed: " + e.getMessage(), e);
        }
        return list;
    }

    private Service mapRow(ResultSet rs) throws SQLException {
        String consultantId = rs.getString("consultant_id");
        Consultant consultant = userRepository.loadConsultant(consultantId);
        if (consultant == null) {
            throw new RuntimeException("Consultant not found for id: " + consultantId);
        }
        return new Service(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getInt("duration_min"),
                rs.getDouble("price"),
                consultant
        );
    }
}
