package com.viniciusdevassis.storelab.domain;

import java.time.Instant;

public final class Models {
    private Models() {}

    public enum Provider { GOOGLE, APPLE }
    public enum Role { OWNER, MANAGER, EMPLOYEE }
    public enum MovementType { IN, OUT }

    public record Person(String id, String name, String email, Instant createdAt, Instant updatedAt) {}
    public record ExternalIdentity(String id, String personId, Provider provider, String subject,
                                   String email, Instant createdAt) {}
    public record Store(String id, String name, boolean active, String createdBy,
                        Instant createdAt, Instant updatedAt) {}
    public record StoreMember(String personId, String storeId, Role role, Instant createdAt) {}
    public record Category(String id, String storeId, String name, boolean active,
                           Instant createdAt, Instant updatedAt) {}
    public record Product(String id, String storeId, String categoryId, String name, String description,
                          long priceInCents, long stock, boolean active, Instant createdAt, Instant updatedAt) {}
    public record InventoryMovement(String id, String productId, String storeId, MovementType type,
                                    long quantity, long previousStock, long newStock, String reason,
                                    String createdBy, Instant createdAt) {}
}
