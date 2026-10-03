package com.viniciusdevassis.storelab.product;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.viniciusdevassis.storelab.api.ApiDtos.Page;
import com.viniciusdevassis.storelab.common.ApiException;
import com.viniciusdevassis.storelab.common.FirestoreSupport;
import com.viniciusdevassis.storelab.domain.Models.Product;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ProductRepository {
    @Inject Firestore firestore;

    public Product create(String storeId, String categoryId, String name, String description, long price, long stock, Instant now) {
        String id = UUID.randomUUID().toString();
        Product product = new Product(id, storeId, categoryId, name, description, price, stock, true, now, now);
        try {
            firestore.collection("products").document(id).set(FirestoreSupport.document("id", id, "storeId", storeId,
                    "categoryId", categoryId, "name", name, "description", description, "priceInCents", price,
                    "stock", stock, "active", true, "createdAt", now, "updatedAt", now)).get();
            return product;
        } catch (Exception e) { throw ApiException.internal(); }
    }

    public Product find(String storeId, String id) {
        try {
            var d = firestore.collection("products").document(id).get().get();
            if (!d.exists() || !storeId.equals(d.getString("storeId"))) throw ApiException.notFound("Product");
            return product(d);
        } catch (ApiException e) { throw e; } catch (Exception e) { throw ApiException.internal(); }
    }

    public List<Product> list(String storeId) { return page(storeId, null, null, 100, null).items(); }

    public Page<Product> page(String storeId, String categoryId, Boolean active, int limit, String cursor) {
        if (limit < 1 || limit > 100) throw ApiException.badRequest("limit must be between 1 and 100");
        try {
            Query query = firestore.collection("products").whereEqualTo("storeId", storeId);
            if (categoryId != null) query = query.whereEqualTo("categoryId", categoryId);
            if (active != null) query = query.whereEqualTo("active", active);
            if (cursor != null && !cursor.isBlank()) {
                String id = decode(cursor);
                DocumentSnapshot snapshot = firestore.collection("products").document(id).get().get();
                if (!snapshot.exists() || !storeId.equals(snapshot.getString("storeId"))
                        || (categoryId != null && !categoryId.equals(snapshot.getString("categoryId")))
                        || (active != null && active != FirestoreSupport.bool(snapshot, "active")))
                    throw ApiException.badRequest("cursor does not match this product query");
                query = query.startAfter(snapshot);
            }
            var docs = query.limit(limit + 1).get().get().getDocuments();
            boolean more = docs.size() > limit;
            List<Product> items = new ArrayList<>();
            for (int i = 0; i < Math.min(limit, docs.size()); i++) items.add(product(docs.get(i)));
            String next = more && !items.isEmpty() ? encode(items.getLast().id()) : null;
            return new Page<>(items, next, more);
        } catch (ApiException e) { throw e; } catch (Exception e) { throw ApiException.internal(); }
    }

    public Product update(String storeId, String id, String categoryId, String name, String description, Long price, Boolean active, Instant now) {
        var ref = firestore.collection("products").document(id);
        try {
            var d = ref.get().get();
            if (!d.exists() || !storeId.equals(d.getString("storeId"))) throw ApiException.notFound("Product");
            var changes = new HashMap<String, Object>();
            if (categoryId != null) changes.put("categoryId", categoryId);
            if (name != null) changes.put("name", name);
            if (description != null) changes.put("description", description);
            if (price != null) changes.put("priceInCents", price);
            if (active != null) changes.put("active", active);
            changes.put("updatedAt", java.util.Date.from(now));
            ref.update(changes).get();
            return new Product(id, storeId, categoryId == null ? d.getString("categoryId") : categoryId,
                    name == null ? d.getString("name") : name, description == null ? d.getString("description") : description,
                    price == null ? FirestoreSupport.number(d, "priceInCents") : price,
                    FirestoreSupport.number(d, "stock"), active == null ? FirestoreSupport.bool(d, "active") : active,
                    FirestoreSupport.instant(d, "createdAt"), now);
        } catch (ApiException e) { throw e; } catch (Exception e) { throw ApiException.internal(); }
    }

    private Product product(DocumentSnapshot d) {
        return new Product(d.getString("id"), d.getString("storeId"), d.getString("categoryId"), d.getString("name"),
                d.getString("description"), FirestoreSupport.number(d, "priceInCents"), FirestoreSupport.number(d, "stock"),
                FirestoreSupport.bool(d, "active"), FirestoreSupport.instant(d, "createdAt"), FirestoreSupport.instant(d, "updatedAt"));
    }
    private static String encode(String id) { return Base64.getUrlEncoder().withoutPadding().encodeToString(id.getBytes(StandardCharsets.UTF_8)); }
    private static String decode(String cursor) {
        try { return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException e) { throw ApiException.badRequest("cursor is invalid"); }
    }
}
