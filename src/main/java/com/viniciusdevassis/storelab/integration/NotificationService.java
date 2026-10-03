package com.viniciusdevassis.storelab.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

@ApplicationScoped
public class NotificationService {
    private static final Logger LOG = Logger.getLogger(NotificationService.class);
    @Inject @RestClient NotificationClient client;
    // Retries are safe only because movementId is sent as the receiver's idempotency key.
    @Timeout(1000) @Retry(maxRetries = 2, delay = 100)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 10000)
    public void notifyStockEmpty(String storeId, String productId, String movementId) {
        client.stockEmpty(movementId, new NotificationClient.StockEmptyNotice(storeId, productId, movementId));
    }
    public void tryNotifyStockEmpty(String storeId, String productId, String movementId) {
        try { notifyStockEmpty(storeId, productId, movementId); }
        catch (RuntimeException e) { LOG.warn("Stock-empty notification failed; inventory already committed", e); }
    }
}
