package com.viniciusdevassis.storelab.inventory;

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

@Path("/stores/{storeId}/products/{productId}/inventory-movements") @Blocking
@Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON) @Tag(name = "Inventory")
public class InventoryResource {
    @Inject InventoryService service;
    @POST @Operation(summary = "Atomically update stock and append an immutable inventory movement")
    public Response create(@PathParam("storeId") String storeId, @PathParam("productId") String productId,
            @Valid CreateMovementRequest request) {
        MovementResponse result = service.create(storeId, productId, request);
        return Response.created(URI.create("/stores/" + storeId + "/products/" + productId + "/inventory-movements/" + result.id()))
                .entity(result).build();
    }
    @GET public List<MovementResponse> list(@PathParam("storeId") String storeId, @PathParam("productId") String productId) {
        return service.list(storeId, productId);
    }
}
