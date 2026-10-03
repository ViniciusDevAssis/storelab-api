package com.viniciusdevassis.storelab.common;

import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.Instant;
import java.util.List;

/** REST endpoint validation has a Quarkus-specific mapper, so this more-specific mapper preserves the API error contract. */
@Provider
public class EndpointValidationExceptionMapper implements ExceptionMapper<ResteasyReactiveViolationException> {
    @Context UriInfo uriInfo;
    @Override
    public Response toResponse(ResteasyReactiveViolationException failure) {
        List<ApiError.Violation> errors = failure.getConstraintViolations().stream()
                .map(v -> new ApiError.Violation(v.getPropertyPath().toString(), v.getMessage())).toList();
        return Response.status(400).type(MediaType.APPLICATION_JSON)
                .entity(new ApiError(400, "VALIDATION_ERROR", "Request validation failed",
                        uriInfo == null ? "" : uriInfo.getPath(), Instant.now(), errors)).build();
    }
}
