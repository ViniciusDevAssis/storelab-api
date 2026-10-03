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
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class StoreMemberRepository {
    @Inject Firestore firestore;

    public StoreMember find(String storeId, String personId) {
        try {
            var d = firestore.collection("stores").document(storeId).collection("members").document(personId).get().get();
            return d.exists() ? member(d) : null;
        } catch (Exception e) { throw ApiException.internal(); }
    }

    public List<StoreMember> list(String storeId) {
        try {
            var docs = firestore.collection("stores").document(storeId).collection("members").get().get().getDocuments();
            List<StoreMember> result = new ArrayList<>();
            docs.forEach(d -> result.add(member(d)));
            return result;
        } catch (Exception e) { throw ApiException.internal(); }
    }

    public List<StoreMember> listForPerson(String personId) {
        try {
            var docs = firestore.collectionGroup("members").whereEqualTo("personId", personId).get().get().getDocuments();
            List<StoreMember> result = new ArrayList<>();
            docs.forEach(d -> result.add(member(d)));
            return result;
        } catch (Exception e) { throw ApiException.internal(); }
    }

    public StoreMember add(String storeId, String personId, Role role, Instant now) {
        var ref = firestore.collection("stores").document(storeId).collection("members").document(personId);
        try {
            var existing = ref.get().get();
            if (existing.exists()) throw ApiException.conflict("Person is already a member of this store");
            StoreMember member = new StoreMember(personId, storeId, role, now);
            ref.set(FirestoreSupport.document("personId", personId, "storeId", storeId, "role", role.name(), "createdAt", now)).get();
            return member;
        } catch (ApiException e) { throw e; }
        catch (Exception e) { throw ApiException.internal(); }
    }

    public StoreMember updateRole(String storeId, String personId, Role role) {
        var store = firestore.collection("stores").document(storeId);
        var target = store.collection("members").document(personId);
        try {
            return firestore.runTransaction(tx -> {
                var d = tx.get(target).get();
                if (!d.exists()) throw ApiException.notFound("Store member");
                if (Role.OWNER.name().equals(d.getString("role")) && role != Role.OWNER) {
                    var docs = tx.get(store.collection("members").whereEqualTo("role", Role.OWNER.name())).get().getDocuments();
                    if (docs.size() <= 1) throw ApiException.conflict("A store must retain at least one OWNER");
                }
                tx.update(target, "role", role.name());
                return new StoreMember(personId, storeId, role, FirestoreSupport.instant(d, "createdAt"));
            }).get();
        } catch (ExecutionException e) { throw unwrap(e); }
        catch (Exception e) { if (e instanceof ApiException a) throw a; throw ApiException.internal(); }
    }

    public void remove(String storeId, String personId) {
        var store = firestore.collection("stores").document(storeId);
        var target = store.collection("members").document(personId);
        try {
            firestore.runTransaction(tx -> {
                var d = tx.get(target).get();
                if (!d.exists()) throw ApiException.notFound("Store member");
                if (Role.OWNER.name().equals(d.getString("role"))) {
                    var owners = tx.get(store.collection("members").whereEqualTo("role", Role.OWNER.name())).get().getDocuments();
                    if (owners.size() <= 1) throw ApiException.conflict("Transfer ownership before removing the last OWNER");
                }
                tx.delete(target);
                return null;
            }).get();
        } catch (ExecutionException e) { throw unwrap(e); }
        catch (Exception e) { if (e instanceof ApiException a) throw a; throw ApiException.internal(); }
    }

    private static RuntimeException unwrap(ExecutionException e) {
        return e.getCause() instanceof ApiException a ? a : ApiException.internal();
    }

    private StoreMember member(com.google.cloud.firestore.DocumentSnapshot d) {
        return new StoreMember(d.getString("personId"), d.getString("storeId"),
                Role.valueOf(d.getString("role")), FirestoreSupport.instant(d, "createdAt"));
    }
}
