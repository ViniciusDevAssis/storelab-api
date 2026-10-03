package com.viniciusdevassis.storelab.integration;

import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "notification-api")
@Path("/notifications")
public interface NotificationClient {
    @POST @Path("/stock-empty")
    void stockEmpty(@HeaderParam("Idempotency-Key") String movementId, StockEmptyNotice notice);
    record StockEmptyNotice(String storeId, String productId, String movementId) {}
}
