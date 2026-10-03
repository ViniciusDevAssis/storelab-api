package com.viniciusdevassis.storelab.product;

import com.viniciusdevassis.storelab.api.ApiDtos.*;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;

@Path("/stores/{storeId}/products") @Blocking @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Products")
public class ProductResource {
    @Inject ProductService service;

    @POST @Operation(summary = "Create a product in a category belonging to this store")
    public Response create(@PathParam("storeId") String storeId, @Valid CreateProductRequest request) {
        ProductResponse result = service.create(storeId, request);
        return Response.created(URI.create("/stores/" + storeId + "/products/" + result.id())).entity(result).build();
    }
    @GET @Operation(summary = "List products using Firestore filters and an opaque cursor")
    public Page<ProductResponse> list(@PathParam("storeId") String storeId, @QueryParam("categoryId") String categoryId,
            @QueryParam("active") Boolean active, @DefaultValue("20") @Min(1) @Max(100) @QueryParam("limit") int limit,
            @QueryParam("cursor") String cursor) { return service.list(storeId, categoryId, active, limit, cursor); }
    @GET @Path("/{productId}") public ProductResponse get(@PathParam("storeId") String storeId, @PathParam("productId") String id) {
        return service.get(storeId, id);
    }
    @PATCH @Path("/{productId}") public ProductResponse update(@PathParam("storeId") String storeId, @PathParam("productId") String id,
            @Valid UpdateProductRequest request) { return service.update(storeId, id, request); }
}
