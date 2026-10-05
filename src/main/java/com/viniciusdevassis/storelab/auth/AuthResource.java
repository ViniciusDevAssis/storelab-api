package com.viniciusdevassis.storelab.auth;

import com.viniciusdevassis.storelab.api.ApiDtos.MeResponse;
import com.viniciusdevassis.storelab.domain.Models.Person;
import io.quarkus.oidc.OidcSession;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.net.URI;

@Path("/auth") @Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    @Inject CurrentPersonService people;
    @Inject SecurityIdentity identity;
    @Inject OidcSession oidcSession;
    @ConfigProperty(name = "storelab.frontend.url") String frontendUrl;

    @GET @Path("/me") @Blocking @Operation(summary = "Return the profile linked to the current OIDC identity")
    public MeResponse me() { Person p = people.current(); return new MeResponse(p.id(), p.name(), p.email(), people.providerName()); }

    @GET
    @Path("/google/login")
    @Authenticated
    @Blocking
    @Operation(summary = "Start Google login or redirect an authenticated user to the frontend")
    public Response googleLogin() {
        people.current();
        return redirectToDashboard();
    }

    @GET
    @Path("/google/callback")
    @Authenticated
    @Operation(summary = "OIDC-managed Google callback fallback")
    public Response googleCallback() {
        return redirectToDashboard();
    }

    @GET
    @Path("/apple/login")
    @Authenticated
    @Blocking
    @Operation(summary = "Start Apple login or redirect an authenticated user to the frontend")
    public Response appleLogin() {
        people.current();
        return redirectToDashboard();
    }

    @POST
    @Path("/apple/callback")
    @Authenticated
    @Operation(summary = "OIDC-managed Apple callback fallback")
    public Response appleCallback() {
        return redirectToDashboard();
    }

    @POST
    @Path("/logout")
    @Blocking
    @Operation(summary = "End the local StoreLab OIDC session")
    public Response logout() {
        if (!identity.isAnonymous() && oidcSession != null) {
            oidcSession.logout().await().indefinitely();
        }
        return Response.noContent().build();
    }

    private Response redirectToDashboard() {
        String baseUrl = frontendUrl.replaceAll("/+$", "");
        return Response.seeOther(URI.create(baseUrl + "/dashboard")).build();
    }
}
