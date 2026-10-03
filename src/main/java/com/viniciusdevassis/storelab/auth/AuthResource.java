package com.viniciusdevassis.storelab.auth;

import com.viniciusdevassis.storelab.api.ApiDtos.MeResponse;
import com.viniciusdevassis.storelab.domain.Models.Person;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/auth") @Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    @Inject CurrentPersonService people;
    @GET @Path("/me") @Blocking @Operation(summary = "Return the profile linked to the current OIDC identity")
    public MeResponse me() { Person p = people.current(); return new MeResponse(p.id(), p.name(), p.email(), people.providerName()); }
}
