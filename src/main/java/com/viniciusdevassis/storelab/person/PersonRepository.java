package com.viniciusdevassis.storelab.person;

import com.google.cloud.firestore.Firestore;
import com.viniciusdevassis.storelab.common.ApiException;
import com.viniciusdevassis.storelab.common.FirestoreSupport;
import com.viniciusdevassis.storelab.domain.Models.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class PersonRepository {
    @Inject Firestore firestore;

    public boolean exists(String id) {
        try { return firestore.collection("people").document(id).get().get().exists(); }
        catch (Exception e) { throw ApiException.internal(); }
    }

    public Person findOrCreate(Provider provider, String subject, String name, String email, Instant now, String newId) {
        String externalId = hash(provider.name() + ":" + subject);
        var people = firestore.collection("people");
        var identities = firestore.collection("externalIdentities");
        try {
            return firestore.runTransaction(tx -> {
                var externalRef = identities.document(externalId);
                var external = tx.get(externalRef).get();
                if (external.exists()) {
                    String personId = external.getString("personId");
                    var person = tx.get(people.document(personId)).get();
                    if (!person.exists()) throw ApiException.notFound("Person");
                    return person(person);
                }
                var personRef = people.document(newId);
                var identityRef = externalRef;
                tx.set(personRef, FirestoreSupport.document("id", newId, "name", name, "email", email,
                        "createdAt", now, "updatedAt", now));
                tx.set(identityRef, FirestoreSupport.document("id", externalId, "personId", newId,
                        "provider", provider.name(), "subject", subject, "email", email, "createdAt", now));
                return new Person(newId, name, email, now, now);
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.internal();
        } catch (ExecutionException e) {
            throw ApiException.internal();
        }
    }

    private Person person(com.google.cloud.firestore.DocumentSnapshot d) {
        return new Person(d.getString("id"), d.getString("name"), d.getString("email"),
                FirestoreSupport.instant(d, "createdAt"), FirestoreSupport.instant(d, "updatedAt"));
    }

    private static String hash(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
