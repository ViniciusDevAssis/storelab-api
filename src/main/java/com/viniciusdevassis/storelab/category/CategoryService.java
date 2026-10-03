package com.viniciusdevassis.storelab.category;

import com.viniciusdevassis.storelab.api.ApiDtos.*;
import com.viniciusdevassis.storelab.domain.Models.Category;
import com.viniciusdevassis.storelab.security.StoreAuthorizationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class CategoryService {
    @Inject CategoryRepository categories;
    @Inject StoreAuthorizationService authorization;
    public CategoryResponse create(String storeId, CreateCategoryRequest request) {
        authorization.requireManagerOrOwner(storeId);
        return response(categories.create(storeId, request.name(), Instant.now()));
    }
    public List<CategoryResponse> list(String storeId) {
        authorization.requireMember(storeId);
        return categories.list(storeId).stream().map(CategoryService::response).toList();
    }
    public CategoryResponse update(String storeId, String id, UpdateCategoryRequest request) {
        authorization.requireManagerOrOwner(storeId);
        if (request.name() == null && request.active() == null) throw com.viniciusdevassis.storelab.common.ApiException.badRequest("Provide at least one category field to update");
        if (request.name() != null && request.name().isBlank()) throw com.viniciusdevassis.storelab.common.ApiException.badRequest("name must not be blank");
        return response(categories.update(storeId, id, request.name(), request.active(), Instant.now()));
    }
    private static CategoryResponse response(Category c) { return new CategoryResponse(c.id(), c.storeId(), c.name(), c.active(), c.createdAt(), c.updatedAt()); }
}
