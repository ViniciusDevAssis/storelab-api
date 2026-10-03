package com.viniciusdevassis.storelab.category;

import com.google.cloud.firestore.Firestore;
import com.viniciusdevassis.storelab.common.ApiException;
import com.viniciusdevassis.storelab.common.FirestoreSupport;
import com.viniciusdevassis.storelab.domain.Models.Category;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CategoryRepository {
    @Inject Firestore firestore;
    public Category create(String storeId, String name, Instant now) {
        String id = UUID.randomUUID().toString();
        var item = new Category(id, storeId, name, true, now, now);
        try {
            firestore.collection("categories").document(id).set(FirestoreSupport.document("id", id, "storeId", storeId,
                    "name", name, "active", true, "createdAt", now, "updatedAt", now)).get();
            return item;
        } catch (Exception e) { throw ApiException.internal(); }
    }
    public Category find(String storeId, String id) {
        try {
            var d = firestore.collection("categories").document(id).get().get();
            if (!d.exists() || !storeId.equals(d.getString("storeId"))) throw ApiException.notFound("Category");
            return category(d);
        } catch (ApiException e) { throw e; } catch (Exception e) { throw ApiException.internal(); }
    }
    public List<Category> list(String storeId) {
        try {
            List<Category> result = new ArrayList<>();
            for (var d : firestore.collection("categories").whereEqualTo("storeId", storeId).get().get().getDocuments()) result.add(category(d));
            return result;
        } catch (Exception e) { throw ApiException.internal(); }
    }
    public Category update(String storeId, String id, String name, Boolean active, Instant now) {
        var ref = firestore.collection("categories").document(id);
        try {
            var d = ref.get().get();
            if (!d.exists() || !storeId.equals(d.getString("storeId"))) throw ApiException.notFound("Category");
            var updates = new java.util.HashMap<String, Object>();
            if (name != null) updates.put("name", name);
            if (active != null) updates.put("active", active);
            updates.put("updatedAt", java.util.Date.from(now));
            ref.update(updates).get();
            return new Category(id, storeId, name == null ? d.getString("name") : name,
                    active == null ? FirestoreSupport.bool(d, "active") : active,
                    FirestoreSupport.instant(d, "createdAt"), now);
        } catch (ApiException e) { throw e; } catch (Exception e) { throw ApiException.internal(); }
    }
    private Category category(com.google.cloud.firestore.DocumentSnapshot d) {
        return new Category(d.getString("id"), d.getString("storeId"), d.getString("name"), FirestoreSupport.bool(d, "active"),
                FirestoreSupport.instant(d, "createdAt"), FirestoreSupport.instant(d, "updatedAt"));
    }
}
