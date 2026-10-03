package com.viniciusdevassis.storelab.store;

import com.google.cloud.firestore.Firestore;
import com.viniciusdevassis.storelab.common.ApiException;
import com.viniciusdevassis.storelab.common.FirestoreSupport;
import com.viniciusdevassis.storelab.domain.Models.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class StoreRepository {
    @Inject Firestore firestore;

    public Store create(String name, String ownerId, Instant now) {
        String id = java.util.UUID.randomUUID().toString();
        var storeRef = firestore.collection("stores").document(id);
        var memberRef = storeRef.collection("members").document(ownerId);
        Store store = new Store(id, name, true, ownerId, now, now);
        try {
            firestore.runTransaction(tx -> {
                tx.set(storeRef, FirestoreSupport.document("id", id, "name", name, "active", true,
                        "createdBy", ownerId, "createdAt", now, "updatedAt", now));
                tx.set(memberRef, FirestoreSupport.document("personId", ownerId, "storeId", id,
                        "role", Role.OWNER.name(), "createdAt", now));
                return null;
            }).get();
            return store;
        } catch (Exception e) { throw ApiException.internal(); }
    }

    public Store find(String id) {
        try {
            var d = firestore.collection("stores").document(id).get().get();
            if (!d.exists()) throw ApiException.notFound("Store");
            return store(d);
        } catch (ApiException e) { throw e; }
        catch (Exception e) { throw ApiException.internal(); }
    }

    public Store update(String id, String name, Boolean active, Instant now) {
        var ref = firestore.collection("stores").document(id);
        try {
            var d = ref.get().get();
            if (!d.exists()) throw ApiException.notFound("Store");
            var updates = new java.util.HashMap<String, Object>();
            if (name != null) updates.put("name", name);
            if (active != null) updates.put("active", active);
            updates.put("updatedAt", java.util.Date.from(now));
            ref.update(updates).get();
            return new Store(id, name == null ? d.getString("name") : name,
                    active == null ? FirestoreSupport.bool(d, "active") : active,
                    d.getString("createdBy"), FirestoreSupport.instant(d, "createdAt"), now);
        } catch (ApiException e) { throw e; }
        catch (Exception e) { throw ApiException.internal(); }
    }

    private Store store(com.google.cloud.firestore.DocumentSnapshot d) {
        return new Store(d.getString("id"), d.getString("name"), FirestoreSupport.bool(d, "active"),
                d.getString("createdBy"), FirestoreSupport.instant(d, "createdAt"), FirestoreSupport.instant(d, "updatedAt"));
    }
}
