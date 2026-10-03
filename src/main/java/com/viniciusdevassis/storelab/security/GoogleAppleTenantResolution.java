package com.viniciusdevassis.storelab.security;

import io.quarkus.oidc.TenantResolver;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

/** Selects only a configured provider tenant. The selected OIDC tenant still validates the bearer token signature, issuer and audience. */
@ApplicationScoped
public class GoogleAppleTenantResolution implements TenantResolver {
    @Override
    public String resolve(RoutingContext context) {
        String issuer = context.request().getHeader("X-Auth-Provider");
        if (issuer == null) return null;
        return switch (issuer.toLowerCase(java.util.Locale.ROOT)) {
            case "google" -> "google";
            case "apple" -> "apple";
            default -> null;
        };
    }
}
