package com.platform.db;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.platform.domain.Client;
import com.platform.payment.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DB access for the payment_methods table.
 * Type-specific fields are stored as JSON in the details column.
 */
public class PaymentMethodRepository {

    private static final Gson GSON = new Gson();

    public String save(String clientId, PaymentMethod method) {
        JsonObject details = buildDetails(method);
        String id = method.getMethodId() != null ? method.getMethodId()
                : UUID.randomUUID().toString();
        String sql = """
                INSERT INTO payment_methods (id, client_id, method_type, details)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE
                SET details = EXCLUDED.details
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, clientId);
            ps.setString(3, method.getMethodType());
            ps.setString(4, GSON.toJson(details));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PaymentMethodRepository.save failed: " + e.getMessage(), e);
        }
        return id;
    }

    public void delete(String clientId, String methodId) {
        String sql = "DELETE FROM payment_methods WHERE id = ? AND client_id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, methodId);
            ps.setString(2, clientId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("delete failed: " + e.getMessage(), e);
        }
    }

    public List<PaymentMethod> findByClientId(String clientId, Client owner) {
        String sql = "SELECT * FROM payment_methods WHERE client_id = ? ORDER BY id";
        List<PaymentMethod> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                PaymentMethod m = reconstruct(rs, owner);
                if (m != null) list.add(m);
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByClientId failed: " + e.getMessage(), e);
        }
        return list;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private JsonObject buildDetails(PaymentMethod m) {
        JsonObject obj = new JsonObject();
        switch (m.getMethodType()) {
            case "CREDIT_CARD" -> {
                CreditCardMethod cc = (CreditCardMethod) m;
                // Use reflection-free approach: store via toString parse or add getters
                // We add minimal getters in Phase 2 (see CreditCardMethod changes)
                obj.addProperty("cardNumber", cc.getCardNumber());
                obj.addProperty("expiry",     cc.getExpiry());
                obj.addProperty("cvv",        cc.getCvv());
            }
            case "DEBIT_CARD" -> {
                DebitCardMethod dc = (DebitCardMethod) m;
                obj.addProperty("cardNumber", dc.getCardNumber());
                obj.addProperty("expiry",     dc.getExpiry());
            }
            case "PAYPAL" -> {
                PayPalMethod pp = (PayPalMethod) m;
                obj.addProperty("email", pp.getEmail());
            }
            case "BANK_TRANSFER" -> {
                BankTransferMethod bt = (BankTransferMethod) m;
                obj.addProperty("accountNumber", bt.getAccountNumber());
                obj.addProperty("routingNumber", bt.getRoutingNumber());
            }
        }
        return obj;
    }

    private PaymentMethod reconstruct(ResultSet rs, Client owner) throws SQLException {
        String id   = rs.getString("id");
        String type = rs.getString("method_type");
        JsonObject d = GSON.fromJson(rs.getString("details"), JsonObject.class);

        return switch (type) {
            case "CREDIT_CARD" -> new CreditCardMethod(owner, id,
                    d.get("cardNumber").getAsString(),
                    d.get("expiry").getAsString(),
                    d.get("cvv").getAsString());
            case "DEBIT_CARD" -> new DebitCardMethod(owner, id,
                    d.get("cardNumber").getAsString(),
                    d.get("expiry").getAsString());
            case "PAYPAL" -> new PayPalMethod(owner, id,
                    d.get("email").getAsString());
            case "BANK_TRANSFER" -> new BankTransferMethod(owner, id,
                    d.get("accountNumber").getAsString(),
                    d.get("routingNumber").getAsString());
            default -> null;
        };
    }
}
