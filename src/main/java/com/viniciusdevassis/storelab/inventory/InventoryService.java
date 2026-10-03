package com.viniciusdevassis.storelab.inventory;

import com.viniciusdevassis.storelab.api.ApiDtos.*;
import com.viniciusdevassis.storelab.auth.CurrentPersonService;
import com.viniciusdevassis.storelab.domain.Models.InventoryMovement;
import com.viniciusdevassis.storelab.integration.NotificationService;
import com.viniciusdevassis.storelab.product.ProductRepository;
import com.viniciusdevassis.storelab.security.StoreAuthorizationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class InventoryService {
    @Inject InventoryRepository movements;
    @Inject ProductRepository products;
    @Inject StoreAuthorizationService authorization;
    @Inject CurrentPersonService currentPerson;
    @Inject NotificationService notifications;
    public MovementResponse create(String storeId, String productId, CreateMovementRequest request) {
        var actor = authorization.requireStockAccess(storeId);
        InventoryMovement movement = movements.create(storeId, productId, request.type(), request.quantity(), request.reason(), actor.id(), Instant.now());
        if (movement.newStock() == 0) notifications.tryNotifyStockEmpty(storeId, productId, movement.id());
        return response(movement);
    }
    public List<MovementResponse> list(String storeId, String productId) {
        authorization.requireStockAccess(storeId);
        products.find(storeId, productId);
        return movements.list(storeId, productId).stream().map(InventoryService::response).toList();
    }
    private static MovementResponse response(InventoryMovement m) {
        return new MovementResponse(m.id(), m.productId(), m.storeId(), m.type(), m.quantity(), m.previousStock(), m.newStock(), m.reason(), m.createdBy(), m.createdAt());
    }
}
