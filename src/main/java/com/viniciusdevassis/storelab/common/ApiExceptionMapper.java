package com.viniciusdevassis.storelab.common;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.Instant;
import java.util.List;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Exception> {
    @Context UriInfo uriInfo;

    @Override
    public Response toResponse(Exception failure) {
        if (failure instanceof ConstraintViolationException validation) {
            var errors = validation.getConstraintViolations().stream()
                    .map(v -> new ApiError.Violation(v.getPropertyPath().toString(), v.getMessage())).toList();
            return Response.status(400).type(MediaType.APPLICATION_JSON)
                    .entity(new ApiError(400, "VALIDATION_ERROR", "Request validation failed", path(), Instant.now(), errors)).build();
        }
        if (failure instanceof WebApplicationException web) {
            int status = web.getResponse().getStatus();
            String code = switch (status) {
                case 400 -> "BAD_REQUEST";
                case 401 -> "UNAUTHENTICATED";
                case 403 -> "FORBIDDEN";
                case 404 -> "NOT_FOUND";
                case 409 -> "CONFLICT";
                default -> "HTTP_ERROR";
            };
            String message = switch (status) {
                case 400 -> "Request is invalid";
                case 401 -> "Authentication is required";
                case 403 -> "You are not allowed to perform this action";
                case 404 -> "The requested resource was not found";
                case 409 -> "The request conflicts with the current resource state";
                default -> "The request could not be completed";
            };
            return Response.status(status).type(MediaType.APPLICATION_JSON)
                    .entity(new ApiError(status, code, message, path(), Instant.now(), List.of())).build();
        }
        ApiException api = failure instanceof ApiException known ? known : ApiException.internal();
        return Response.status(api.status()).type(MediaType.APPLICATION_JSON)
                .entity(new ApiError(api.status(), api.code(), api.getMessage(), path(), Instant.now(), List.of())).build();
    }

    private String path() { return uriInfo == null ? "" : uriInfo.getPath(); }
}
