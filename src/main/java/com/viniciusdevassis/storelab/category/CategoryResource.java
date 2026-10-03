package com.viniciusdevassis.storelab.category;

import com.viniciusdevassis.storelab.api.ApiDtos.*;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.List;

@Path("/stores/{storeId}/categories") @Blocking @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Categories")
public class CategoryResource {
    @Inject CategoryService service;
    @POST public Response create(@PathParam("storeId") String storeId, @Valid CreateCategoryRequest request) {
        var result = service.create(storeId, request);
        return Response.created(URI.create("/stores/" + storeId + "/categories/" + result.id())).entity(result).build();
    }
    @GET public List<CategoryResponse> list(@PathParam("storeId") String storeId) { return service.list(storeId); }
    @PATCH @Path("/{categoryId}") public CategoryResponse update(@PathParam("storeId") String storeId, @PathParam("categoryId") String id,
            @Valid UpdateCategoryRequest request) { return service.update(storeId, id, request); }
}
