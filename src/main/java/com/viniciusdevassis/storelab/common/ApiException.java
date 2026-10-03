package com.viniciusdevassis.storelab.common;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }

    public static ApiException badRequest(String message) { return new ApiException(400, "BAD_REQUEST", message); }
    public static ApiException forbidden() { return new ApiException(403, "FORBIDDEN", "You are not allowed to perform this action"); }
    public static ApiException notFound(String resource) { return new ApiException(404, "NOT_FOUND", resource + " was not found"); }
    public static ApiException conflict(String message) { return new ApiException(409, "CONFLICT", message); }
    public static ApiException internal() { return new ApiException(500, "INTERNAL_ERROR", "An unexpected error occurred"); }
}
