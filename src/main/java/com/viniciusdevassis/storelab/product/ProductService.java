package com.viniciusdevassis.storelab.product;

import com.viniciusdevassis.storelab.api.ApiDtos.*;
import com.viniciusdevassis.storelab.category.CategoryRepository;
import com.viniciusdevassis.storelab.common.ApiException;
import com.viniciusdevassis.storelab.domain.Models.*;
import com.viniciusdevassis.storelab.security.StoreAuthorizationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class ProductService {
    @Inject ProductRepository products;
    @Inject CategoryRepository categories;
    @Inject StoreAuthorizationService authorization;
    public ProductResponse create(String storeId, CreateProductRequest request) {
        authorization.requireManagerOrOwner(storeId);
        Category category = categories.find(storeId, request.categoryId());
        if (!category.active()) throw ApiException.conflict("Products must use an active category");
        return response(products.create(storeId, category.id(), request.name(), request.description(), request.priceInCents(), request.initialStock(), Instant.now()));
    }
    public Page<ProductResponse> list(String storeId, String categoryId, Boolean active, int limit, String cursor) {
        authorization.requireMember(storeId);
        Page<Product> page = products.page(storeId, categoryId, active, limit, cursor);
        return new Page<>(page.items().stream().map(ProductService::response).toList(), page.nextCursor(), page.hasMore());
    }
    public ProductResponse get(String storeId, String id) { authorization.requireMember(storeId); return response(products.find(storeId, id)); }
    public ProductResponse update(String storeId, String id, UpdateProductRequest request) {
        authorization.requireManagerOrOwner(storeId);
        if (request.categoryId() == null && request.name() == null && request.description() == null
                && request.priceInCents() == null && request.active() == null) throw ApiException.badRequest("Provide at least one product field to update");
        if (request.name() != null && request.name().isBlank()) throw ApiException.badRequest("name must not be blank");
        if (request.priceInCents() != null && request.priceInCents() <= 0) throw ApiException.badRequest("priceInCents must be positive");
        if (request.categoryId() != null) {
            Category category = categories.find(storeId, request.categoryId());
            if (!category.active()) throw ApiException.conflict("Products must use an active category");
        }
        return response(products.update(storeId, id, request.categoryId(), request.name(), request.description(), request.priceInCents(), request.active(), Instant.now()));
    }
    private static ProductResponse response(Product p) {
        return new ProductResponse(p.id(), p.storeId(), p.categoryId(), p.name(), p.description(), p.priceInCents(), p.stock(), p.active(), p.createdAt(), p.updatedAt());
    }
}
