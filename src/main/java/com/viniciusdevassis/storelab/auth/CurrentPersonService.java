package com.viniciusdevassis.storelab.auth;

import com.viniciusdevassis.storelab.common.ApiException;
import com.viniciusdevassis.storelab.domain.Models.*;
import com.viniciusdevassis.storelab.person.PersonRepository;
import io.quarkus.oidc.IdToken;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class CurrentPersonService {
    @Inject SecurityIdentity identity;
    @Inject @IdToken JsonWebToken idToken;
    @Inject PersonRepository people;

    public Person current() {
        if (identity.isAnonymous()) throw ApiException.forbidden();
        String subject = idToken == null ? null : idToken.getSubject();
        if (subject == null || subject.isBlank()) throw ApiException.forbidden();
        String providerName = providerName();
        Provider provider = switch (providerName.toLowerCase(Locale.ROOT)) {
            case "apple" -> Provider.APPLE;
            case "google" -> Provider.GOOGLE;
            default -> throw ApiException.forbidden();
        };
        String email = idToken.getClaim("email");
        String name = idToken.getClaim("name");
        if (name == null || name.isBlank()) name = email == null ? "StoreLab user" : email;
        return people.findOrCreate(provider, subject, name, email, Instant.now(), UUID.randomUUID().toString());
    }

    public String providerName() {
        String providerName = identity.getAttribute("tenant-id");
        if (providerName == null || providerName.isBlank()) throw ApiException.forbidden();
        return providerName.toUpperCase(Locale.ROOT);
    }
}
