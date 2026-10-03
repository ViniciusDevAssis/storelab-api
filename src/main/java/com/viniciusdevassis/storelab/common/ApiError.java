package com.viniciusdevassis.storelab.common;

import java.time.Instant;
import java.util.List;

public record ApiError(int status, String code, String message, String path, Instant timestamp,
                       List<Violation> validationErrors) {
    public record Violation(String field, String message) {}
}
