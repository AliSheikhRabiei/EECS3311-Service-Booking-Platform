package payment;

import model.Client;

import java.util.*;

public class PaymentMethodService {
    private final Map<String, List<PaymentMethod>> methods = new HashMap<>();

    public void addMethod(Client client, PaymentMethod method) {
        methods.computeIfAbsent(client.getId(), k -> new ArrayList<>()).add(method);
        System.out.println("[PaymentMethodService] Added " + method.getMethodType()
                + " for " + client.getName());
    }

    public void removeMethod(Client client, String methodId) {
        List<PaymentMethod> list = methods.getOrDefault(client.getId(), new ArrayList<>());
        boolean removed = list.removeIf(m -> m.getMethodId().equals(methodId));
        System.out.println(removed
                ? "[PaymentMethodService] Removed method " + methodId
                : "[PaymentMethodService] Method not found: " + methodId);
    }

    public List<PaymentMethod> listMethods(Client client) {
        return new ArrayList<>(methods.getOrDefault(client.getId(), new ArrayList<>()));
    }

    public void updateMethod(Client client, PaymentMethod updated) {
        removeMethod(client, updated.getMethodId());
        addMethod(client, updated);
    }

    public PaymentMethod findMethod(Client client, String methodId) {
        return methods.getOrDefault(client.getId(), new ArrayList<>())
                .stream()
                .filter(m -> m.getMethodId().equals(methodId))
                .findFirst().orElse(null);
    }
}
