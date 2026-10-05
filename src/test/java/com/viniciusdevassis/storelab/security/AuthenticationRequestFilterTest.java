package com.viniciusdevassis.storelab.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthenticationRequestFilterTest {
    @Test
    void normalizePathAddsExactlyOneLeadingSlash() {
        assertEquals("/auth/google/login", AuthenticationRequestFilter.normalizePath("auth/google/login"));
        assertEquals("/auth/google/login", AuthenticationRequestFilter.normalizePath("/auth/google/login"));
        assertEquals("/auth/google/login", AuthenticationRequestFilter.normalizePath("///auth/google/login"));
    }
}
