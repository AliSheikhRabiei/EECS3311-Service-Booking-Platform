package com.platform.payment;
import java.util.Scanner;

import com.platform.domain.Client;

import javax.crypto.spec.PSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages saved payment methods per client (UC6).
 * Storage is in-memory: Map<clientId, List<PaymentMethod>>.
 */
public class PaymentMethodService {

    private final Map<String, List<PaymentMethod>> store = new HashMap<>();

    /** UC6 – Adds a new payment method for a client. */
    public void addMethod(Client client, PaymentMethod method) {
        if (client == null || method == null) return;
        store.computeIfAbsent(client.getId(), k -> new ArrayList<>()).add(method);
        System.out.println("[PaymentMethodService] Added " + method.getMethodType()
                + " (id=" + method.getMethodId() + ") for " + client.getName());
    }

    /** UC6 – Removes a payment method by id. */
    public void removeMethod(Client client, String methodId) {
        if (client == null || methodId == null) return;
        List<PaymentMethod> list = store.getOrDefault(client.getId(), new ArrayList<>());
        boolean removed = list.removeIf(m -> m.getMethodId().equals(methodId));
        System.out.println(removed
                ? "[PaymentMethodService] Removed method " + methodId
                : "[PaymentMethodService] Method not found: " + methodId);
    }


    /** UC6 – Lists all saved payment methods for a client. */
    public List<PaymentMethod> listMethods(Client client) {
        if (client == null) return new ArrayList<>();
        return Collections.unmodifiableList(
                store.getOrDefault(client.getId(), new ArrayList<>()));
    }

    /**
     * UC6 – Updates an existing payment method (replace by methodId).
     * TODO: extend with partial-update semantics if required.
     */
    public void updateMethod(Client client, PaymentMethod updated) {
        if (client == null || updated == null) return;
        removeMethod(client, updated.getMethodId());
        addMethod(client, updated);
    }

    /** Convenience: finds a method by id for a specific client. */
    public PaymentMethod findMethod(Client client, String methodId) {
        return listMethods(client).stream()
                .filter(m -> m.getMethodId().equals(methodId))
                .findFirst().orElse(null);
    }
}
