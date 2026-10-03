package com.viniciusdevassis.storelab.inventory;

import com.google.cloud.firestore.Firestore;
import com.viniciusdevassis.storelab.common.ApiException;
import com.viniciusdevassis.storelab.common.FirestoreSupport;
import com.viniciusdevassis.storelab.domain.Models.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class InventoryRepository {
    @Inject Firestore firestore;

    public InventoryMovement create(String storeId, String productId, MovementType type, long quantity,
                                    String reason, String actorId, Instant now) {
        if (quantity <= 0) throw ApiException.badRequest("quantity must be greater than zero");
        String id = UUID.randomUUID().toString();
        var productRef = firestore.collection("products").document(productId);
        var movementRef = productRef.collection("inventoryMovements").document(id);
        try {
            return firestore.runTransaction(tx -> {
                var product = tx.get(productRef).get();
                if (!product.exists() || !storeId.equals(product.getString("storeId"))) throw ApiException.notFound("Product");
                if (!FirestoreSupport.bool(product, "active")) throw ApiException.conflict("Inactive products cannot receive stock movements");
                long previous = FirestoreSupport.number(product, "stock");
                long next;
                try { next = type == MovementType.IN ? Math.addExact(previous, quantity) : Math.subtractExact(previous, quantity); }
                catch (ArithmeticException e) { throw ApiException.conflict("Stock quantity is outside the supported range"); }
                if (next < 0) throw ApiException.conflict("Insufficient stock");
                tx.update(productRef, "stock", next, "updatedAt", java.util.Date.from(now));
                tx.create(movementRef, FirestoreSupport.document("id", id, "productId", productId, "storeId", storeId,
                        "type", type.name(), "quantity", quantity, "previousStock", previous, "newStock", next,
                        "reason", reason, "createdBy", actorId, "createdAt", now));
                return new InventoryMovement(id, productId, storeId, type, quantity, previous, next, reason, actorId, now);
            }).get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ApiException api) throw api;
            throw ApiException.internal();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.internal();
        }
    }

    public List<InventoryMovement> list(String storeId, String productId) {
        try {
            var docs = firestore.collection("products").document(productId).collection("inventoryMovements")
                    .whereEqualTo("storeId", storeId).orderBy("createdAt", com.google.cloud.firestore.Query.Direction.DESCENDING)
                    .limit(100).get().get().getDocuments();
            List<InventoryMovement> result = new ArrayList<>();
            for (var d : docs) result.add(new InventoryMovement(d.getString("id"), d.getString("productId"),
                    d.getString("storeId"), MovementType.valueOf(d.getString("type")), FirestoreSupport.number(d, "quantity"),
                    FirestoreSupport.number(d, "previousStock"), FirestoreSupport.number(d, "newStock"), d.getString("reason"),
                    d.getString("createdBy"), FirestoreSupport.instant(d, "createdAt")));
            return result;
        } catch (Exception e) { throw ApiException.internal(); }
    }
}
