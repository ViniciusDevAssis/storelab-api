package com.viniciusdevassis.storelab.security;

import com.viniciusdevassis.storelab.common.ApiError;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.time.Instant;
import java.util.List;

/** Produces the same JSON error contract for anonymous requests as the API's other exception mappers. */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationRequestFilter implements ContainerRequestFilter {
    @Inject SecurityIdentity identity;

    @Override
    public void filter(ContainerRequestContext request) {
        if (identity.isAnonymous()) {
            var error = new ApiError(401, "UNAUTHENTICATED", "Authentication is required",
                    request.getUriInfo().getPath(), Instant.now(), List.of());
            request.abortWith(Response.status(401).type(MediaType.APPLICATION_JSON).entity(error).build());
        }
    }
}
