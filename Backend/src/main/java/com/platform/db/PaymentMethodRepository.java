package com.platform.db;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.platform.domain.Client;
import com.platform.payment.BankTransferMethod;
import com.platform.payment.CreditCardMethod;
import com.platform.payment.DebitCardMethod;
import com.platform.payment.PayPalMethod;
import com.platform.payment.PaymentMethod;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DB access for the payment_methods table.
 * Type-specific fields are stored as masked JSON in the details column.
 */
public class PaymentMethodRepository {

    private static final Gson GSON = new Gson();

    public String save(String clientId, PaymentMethod method) {
        JsonObject details = buildDetails(method);
        String id = method.getMethodId() != null ? method.getMethodId() : UUID.randomUUID().toString();
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

    private JsonObject buildDetails(PaymentMethod method) {
        JsonObject obj = new JsonObject();
        obj.addProperty("storedMasked", true);

        switch (method.getMethodType()) {
            case "CREDIT_CARD" -> {
                CreditCardMethod cc = (CreditCardMethod) method;
                obj.addProperty("maskedCardNumber", maskDigits(cc.getCardNumber(), 12));
                obj.addProperty("expiry", cc.getExpiry());
            }
            case "DEBIT_CARD" -> {
                DebitCardMethod dc = (DebitCardMethod) method;
                obj.addProperty("maskedCardNumber", maskDigits(dc.getCardNumber(), 12));
                obj.addProperty("expiry", dc.getExpiry());
            }
            case "PAYPAL" -> {
                PayPalMethod pp = (PayPalMethod) method;
                obj.addProperty("maskedEmail", maskEmail(pp.getEmail()));
            }
            case "BANK_TRANSFER" -> {
                BankTransferMethod bt = (BankTransferMethod) method;
                obj.addProperty("maskedAccountNumber", maskDigits(bt.getAccountNumber(), 4));
                obj.addProperty("maskedRoutingNumber", maskDigits(bt.getRoutingNumber(), 5));
            }
            default -> throw new IllegalArgumentException("Unsupported payment method type: " + method.getMethodType());
        }

        return obj;
    }

    private PaymentMethod reconstruct(ResultSet rs, Client owner) throws SQLException {
        String id = rs.getString("id");
        String type = rs.getString("method_type");
        JsonObject details = GSON.fromJson(rs.getString("details"), JsonObject.class);
        boolean storedMasked = details != null && (
                getBoolean(details, "storedMasked")
                || details.has("maskedCardNumber")
                || details.has("maskedEmail")
                || details.has("maskedAccountNumber"));

        return switch (type) {
            case "CREDIT_CARD" -> storedMasked
                    ? CreditCardMethod.fromStored(owner, id,
                        getMaskedOrFallbackDigits(details, "maskedCardNumber", "cardNumber", 12),
                        getRequired(details, "expiry"))
                    : new CreditCardMethod(owner, id,
                        getRequired(details, "cardNumber"),
                        getRequired(details, "expiry"),
                        getRequired(details, "cvv"));
            case "DEBIT_CARD" -> storedMasked
                    ? DebitCardMethod.fromStored(owner, id,
                        getMaskedOrFallbackDigits(details, "maskedCardNumber", "cardNumber", 12),
                        getRequired(details, "expiry"))
                    : new DebitCardMethod(owner, id,
                        getRequired(details, "cardNumber"),
                        getRequired(details, "expiry"));
            case "PAYPAL" -> storedMasked
                    ? PayPalMethod.fromStored(owner, id,
                        getMaskedOrFallbackEmail(details, "maskedEmail", "email"))
                    : new PayPalMethod(owner, id, getRequired(details, "email"));
            case "BANK_TRANSFER" -> storedMasked
                    ? BankTransferMethod.fromStored(owner, id,
                        getMaskedOrFallbackDigits(details, "maskedAccountNumber", "accountNumber", 4),
                        getMaskedOrFallbackDigits(details, "maskedRoutingNumber", "routingNumber", 5))
                    : new BankTransferMethod(owner, id,
                        getRequired(details, "accountNumber"),
                        getRequired(details, "routingNumber"));
            default -> null;
        };
    }

    private String getRequired(JsonObject details, String key) {
        if (details == null || !details.has(key) || details.get(key).isJsonNull()) {
            throw new IllegalStateException("Missing payment detail: " + key);
        }
        return details.get(key).getAsString();
    }

    private String getMaskedOrFallbackDigits(JsonObject details, String maskedKey, String rawKey, int maskCount) {
        if (details != null && details.has(maskedKey) && !details.get(maskedKey).isJsonNull()) {
            return details.get(maskedKey).getAsString();
        }
        return maskDigits(getRequired(details, rawKey), maskCount);
    }

    private String getMaskedOrFallbackEmail(JsonObject details, String maskedKey, String rawKey) {
        if (details != null && details.has(maskedKey) && !details.get(maskedKey).isJsonNull()) {
            return details.get(maskedKey).getAsString();
        }
        return maskEmail(getRequired(details, rawKey));
    }

    private boolean getBoolean(JsonObject details, String key) {
        return details != null && details.has(key) && !details.get(key).isJsonNull() && details.get(key).getAsBoolean();
    }

    private String maskDigits(String value, int maskCount) {
        if (value == null || value.isBlank()) return "****";
        if (value.contains("*")) return value;
        if (value.length() < 4) return "****";
        return "*".repeat(Math.max(4, maskCount)) + value.substring(value.length() - 4);
    }

    private String maskEmail(String value) {
        if (value == null || value.isBlank()) return "***@***";
        if (value.contains("*")) return value;

        int at = value.indexOf('@');
        if (at <= 0) return "***@***";

        String local = value.substring(0, at);
        String domain = value.substring(at + 1);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***@" + domain;
    }
}
