package com.viniciusdevassis.storelab.api;

import com.viniciusdevassis.storelab.domain.Models.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {}

    public record MeResponse(String id, String name, String email, String provider) {}
    public record CreateStoreRequest(@NotBlank @Size(max = 120) String name) {}
    public record UpdateStoreRequest(@Size(max = 120) String name, Boolean active) {}
    public record StoreResponse(String id, String name, boolean active, String createdBy,
                                Instant createdAt, Instant updatedAt, Role role) {}
    public record AddMemberRequest(@NotBlank String personId, @NotNull Role role) {}
    public record UpdateMemberRequest(@NotNull Role role) {}
    public record MemberResponse(String personId, String storeId, Role role, Instant createdAt) {}
    public record CreateCategoryRequest(@NotBlank @Size(max = 100) String name) {}
    public record UpdateCategoryRequest(@Size(max = 100) String name, Boolean active) {}
    public record CategoryResponse(String id, String storeId, String name, boolean active,
                                   Instant createdAt, Instant updatedAt) {}
    public record CreateProductRequest(@NotBlank String categoryId, @NotBlank @Size(max = 160) String name,
                                       @Size(max = 2000) String description, @Positive long priceInCents,
                                       @PositiveOrZero long initialStock) {}
    public record UpdateProductRequest(String categoryId, @Size(max = 160) String name,
                                       @Size(max = 2000) String description, @Positive Long priceInCents,
                                       Boolean active) {}
    public record ProductResponse(String id, String storeId, String categoryId, String name, String description,
                                  long priceInCents, long stock, boolean active, Instant createdAt, Instant updatedAt) {}
    public record CreateMovementRequest(@NotNull MovementType type, @Positive long quantity,
                                        @Size(max = 300) String reason) {}
    public record MovementResponse(String id, String productId, String storeId, MovementType type,
                                   long quantity, long previousStock, long newStock, String reason,
                                   String createdBy, Instant createdAt) {}
    public record Page<T>(List<T> items, String nextCursor, boolean hasMore) {}
}
