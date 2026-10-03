package com.viniciusdevassis.storelab.store;

import com.viniciusdevassis.storelab.api.ApiDtos.*;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.List;

@Path("/stores") @Blocking @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Stores")
public class StoreResource {
    @Inject StoreService service;
    @POST @Operation(summary = "Create a store and make its creator the OWNER")
    public Response create(@Valid CreateStoreRequest request) {
        StoreResponse result = service.create(request);
        return Response.created(URI.create("/stores/" + result.id())).entity(result).build();
    }
    @GET public List<StoreResponse> list() { return service.list(); }
    @GET @Path("/{storeId}") public StoreResponse get(@PathParam("storeId") String id) { return service.get(id); }
    @PATCH @Path("/{storeId}") public StoreResponse update(@PathParam("storeId") String id, @Valid UpdateStoreRequest request) { return service.update(id, request); }
    @GET @Path("/{storeId}/members") public List<MemberResponse> members(@PathParam("storeId") String id) { return service.listMembers(id); }
    @POST @Path("/{storeId}/members") public Response addMember(@PathParam("storeId") String id, @Valid AddMemberRequest request) {
        return Response.status(201).entity(service.addMember(id, request)).build();
    }
    @PATCH @Path("/{storeId}/members/{personId}") public MemberResponse updateMember(@PathParam("storeId") String id,
            @PathParam("personId") String personId, @Valid UpdateMemberRequest request) { return service.updateMember(id, personId, request); }
    @DELETE @Path("/{storeId}/members/{personId}") public Response removeMember(@PathParam("storeId") String id, @PathParam("personId") String personId) {
        service.removeMember(id, personId); return Response.noContent().build();
    }
}
