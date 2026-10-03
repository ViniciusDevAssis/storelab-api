package com.viniciusdevassis.storelab.migration;

import com.google.cloud.firestore.Firestore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Date;

@ApplicationScoped
public class V001AddActiveToExistingProducts {
    public static final String VERSION = "v001-add-active-to-products";
    @Inject Firestore firestore;
    public void run() throws Exception {
        var marker = firestore.collection("schemaMigrations").document(VERSION);
        if (marker.get().get().exists()) return;
        for (var product : firestore.collection("products").get().get().getDocuments())
            if (!product.contains("active")) product.getReference().update("active", true).get();
        marker.create(java.util.Map.of("version", VERSION, "executedAt", Date.from(Instant.now()))).get();
    }
}
