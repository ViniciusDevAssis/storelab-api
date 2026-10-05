package com.viniciusdevassis.storelab;

import com.google.cloud.firestore.Firestore;
import com.viniciusdevassis.storelab.category.CategoryRepository;
import com.viniciusdevassis.storelab.domain.Models.Provider;
import com.viniciusdevassis.storelab.domain.Models.Role;
import com.viniciusdevassis.storelab.person.PersonRepository;
import com.viniciusdevassis.storelab.product.ProductRepository;
import com.viniciusdevassis.storelab.store.StoreMemberRepository;
import com.viniciusdevassis.storelab.store.StoreRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.SecurityAttribute;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class StoreLabApiTest {
    @Inject Firestore firestore;
    @Inject PersonRepository people;
    @Inject StoreRepository stores;
    @Inject StoreMemberRepository members;
    @Inject CategoryRepository categories;
    @Inject ProductRepository products;

    @Test
    void protectedEndpointsRequireAnIdentity() {
        given().get("/auth/me").then().statusCode(401).contentType(containsString("application/json"))
                .body("code", equalTo("UNAUTHENTICATED"));
        given().get("/stores").then().statusCode(401).contentType(containsString("application/json"))
                .header("Location", nullValue());
        given().get("/route-that-does-not-exist").then().statusCode(404);
    }

    @Test
    void corsPreflightAllowsOnlyConfiguredFrontendWithCredentials() {
        given().header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .options("/stores")
                .then().statusCode(200)
                .header("Access-Control-Allow-Origin", equalTo("http://localhost:3000"))
                .header("Access-Control-Allow-Credentials", equalTo("true"));
    }

    @Test
    void logoutIsIdempotentWithoutSession() {
        given().post("/auth/logout").then().statusCode(204);
        given().get("/auth/me").then().statusCode(401).body("code", equalTo("UNAUTHENTICATED"));
    }

    @Test
    @TestSecurity(user = "logout-principal", attributes = @SecurityAttribute(key = "tenant-id", value = "google"))
    @OidcSecurity(claims = {
            @Claim(key = "sub", value = "logout-oidc-subject"),
            @Claim(key = "email", value = "logout@example.test")
    })
    void authenticatedSessionCanBeLoggedOutLocally() {
        given().contentType("application/json").post("/auth/logout").then().statusCode(204);
    }

    @Test
    @TestSecurity(user = "identity-without-tenant")
    @OidcSecurity(claims = {
            @Claim(key = "sub", value = "identity-without-tenant-subject"),
            @Claim(key = "email", value = "unknown-provider@example.test")
    })
    void authenticatedIdentityWithoutTenantIsDenied() {
        given().get("/auth/me").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "google-callback-user", attributes = @SecurityAttribute(key = "tenant-id", value = "google"))
    @OidcSecurity(claims = @Claim(key = "sub", value = "google-callback-subject"))
    void googleOidcCallbackRouteExistsForAuthenticatedIdentity() {
        given().redirects().follow(false).get("/auth/google/callback")
                .then().statusCode(303)
                .header("Location", equalTo("http://localhost:3000/dashboard"));
    }

    @Test
    @TestSecurity(user = "apple-callback-user", attributes = @SecurityAttribute(key = "tenant-id", value = "apple"))
    @OidcSecurity(claims = @Claim(key = "sub", value = "apple-callback-subject"))
    void appleOidcCallbackRouteExistsForAuthenticatedIdentity() {
        given().redirects().follow(false).post("/auth/apple/callback")
                .then().statusCode(303)
                .header("Location", equalTo("http://localhost:3000/dashboard"));
    }

    @Test
    @TestSecurity(user = "principal-name-is-not-the-subject", attributes = @SecurityAttribute(key = "tenant-id", value = "google"))
    @OidcSecurity(claims = {
            @Claim(key = "sub", value = "google-subject-stable-test"),
            @Claim(key = "email", value = "google-user@example.test"),
            @Claim(key = "name", value = "Google Study User")
    })
    void currentPersonUsesOidcSubAndReusesTheSamePerson() throws Exception {
        var first = given().get("/auth/me").then().statusCode(200).extract().response().jsonPath();
        var second = given().get("/auth/me").then().statusCode(200).extract().response().jsonPath();

        assertEquals(first.getString("id"), second.getString("id"));
        assertEquals("Google Study User", first.getString("name"));
        assertEquals("google-user@example.test", first.getString("email"));
        var identity = firestore.collection("externalIdentities").whereEqualTo("subject", "google-subject-stable-test")
                .get().get().getDocuments().getFirst();
        assertEquals("GOOGLE", identity.getString("provider"));
        assertEquals(first.getString("id"), identity.getString("personId"));
    }

    @Test
    @TestSecurity(user = "apple-principal", attributes = @SecurityAttribute(key = "tenant-id", value = "apple"))
    @OidcSecurity(claims = {
            @Claim(key = "sub", value = "apple-subject-stable-test"),
            @Claim(key = "email", value = "apple-user@example.test"),
            @Claim(key = "name", value = "Apple Study User")
    })
    void currentPersonUsesAppleTenantWithOidcSubject() throws Exception {
        var profile = given().get("/auth/me").then().statusCode(200).extract().response().jsonPath();
        assertEquals("APPLE", profile.getString("provider"));
        var identity = firestore.collection("externalIdentities").whereEqualTo("subject", "apple-subject-stable-test")
                .get().get().getDocuments().getFirst();
        assertEquals("APPLE", identity.getString("provider"));
    }

    @Test
    void sameSubjectIsScopedByProviderAndRepeatedIdentityFindsSamePerson() {
        Instant now = Instant.now();
        var google = people.findOrCreate(Provider.GOOGLE, "shared-subject-test", "Google", "same@example.test", now, UUID.randomUUID().toString());
        var googleAgain = people.findOrCreate(Provider.GOOGLE, "shared-subject-test", "Ignored", "same@example.test", now, UUID.randomUUID().toString());
        var apple = people.findOrCreate(Provider.APPLE, "shared-subject-test", "Apple", "same@example.test", now, UUID.randomUUID().toString());

        assertEquals(google.id(), googleAgain.id());
        assertNotEquals(google.id(), apple.id());
    }

    @Test
    @TestSecurity(user = "owner-principal", attributes = @SecurityAttribute(key = "tenant-id", value = "google"))
    @OidcSecurity(claims = {
            @Claim(key = "sub", value = "owner-oidc-subject"),
            @Claim(key = "email", value = "owner@example.test"),
            @Claim(key = "name", value = "Store Owner")
    })
    void ownerManagesStoreCatalogInventoryAndMembership() {
        String storeId = createStore("Owner Study Store");
        String categoryId = createCategory(storeId, "Clothing");
        String productId = createProduct(storeId, categoryId, "Shirt", 3);
        String inactiveProductId = createProduct(storeId, categoryId, "Old shirt", 2);

        String movementPath = movementsPath(storeId, productId);
        given().contentType("application/json").body("{\"type\":\"OUT\",\"quantity\":4}")
                .post(movementPath).then().statusCode(409);
        given().contentType("application/json").body("{\"type\":\"IN\",\"quantity\":2,\"reason\":\"restock\"}")
                .post(movementPath).then().statusCode(201).body("previousStock", equalTo(3)).body("newStock", equalTo(5));
        given().contentType("application/json").body("{\"type\":\"OUT\",\"quantity\":1,\"reason\":\"sale\"}")
                .post(movementPath).then().statusCode(201).body("previousStock", equalTo(5)).body("newStock", equalTo(4));
        given().get("/stores/{storeId}/products/{productId}", storeId, productId)
                .then().statusCode(200).body("stock", equalTo(4));
        given().get(movementPath).then().statusCode(200).body("size()", equalTo(2));

        String cursor = given().queryParam("limit", 1).get("/stores/{storeId}/products", storeId)
                .then().statusCode(200).body("items.size()", equalTo(1)).body("hasMore", is(true)).extract().path("nextCursor");
        given().queryParam("limit", 1).queryParam("cursor", cursor).get("/stores/{storeId}/products", storeId)
                .then().statusCode(200).body("items.size()", equalTo(1)).body("hasMore", is(false));

        given().contentType("application/json").body("{\"active\":false}")
                .patch("/stores/{storeId}/products/{productId}", storeId, inactiveProductId).then().statusCode(200);
        given().contentType("application/json").body("{\"type\":\"IN\",\"quantity\":1}")
                .post(movementsPath(storeId, inactiveProductId)).then().statusCode(409);

        String ownerId = given().get("/auth/me").then().statusCode(200).extract().path("id");
        given().delete("/stores/{storeId}/members/{personId}", storeId, ownerId).then().statusCode(409);
        var otherPerson = people.findOrCreate(Provider.GOOGLE, "membership-person-sub", "Member", "member@example.test",
                Instant.now(), UUID.randomUUID().toString());
        given().contentType("application/json").body("{\"personId\":\"" + otherPerson.id() + "\",\"role\":\"EMPLOYEE\"}")
                .post("/stores/{storeId}/members", storeId).then().statusCode(201).body("role", equalTo("EMPLOYEE"));
        given().contentType("application/json").body("{\"role\":\"MANAGER\"}")
                .patch("/stores/{storeId}/members/{personId}", storeId, otherPerson.id()).then().statusCode(200);
        given().delete("/stores/{storeId}/members/{personId}", storeId, otherPerson.id()).then().statusCode(204);
    }

    @Test
    @TestSecurity(user = "outsider-principal", attributes = @SecurityAttribute(key = "tenant-id", value = "google"))
    @OidcSecurity(claims = {
            @Claim(key = "sub", value = "outsider-oidc-subject"),
            @Claim(key = "email", value = "outsider@example.test"),
            @Claim(key = "name", value = "Outside Person")
    })
    void nonMemberCannotAccessAnotherStoresProduct() {
        String outsiderId = given().get("/auth/me").then().statusCode(200).extract().path("id");
        var store = stores.create("Private fixture", "another-owner-" + UUID.randomUUID(), Instant.now());
        String categoryId = categories.create(store.id(), "Private category", Instant.now()).id();
        String productId = products.create(store.id(), categoryId, "Private product", "", 100, 1, Instant.now()).id();
        given().get("/stores/{storeId}/products/{productId}", store.id(), productId).then().statusCode(403);
        given().contentType("application/json").body("{\"name\":\"stolen\",\"priceInCents\":100}")
                .patch("/stores/{storeId}/products/{productId}", store.id(), productId).then().statusCode(403);
        given().get("/stores").then().statusCode(200).body("size()", equalTo(0));
    }

    @Test
    @TestSecurity(user = "manager-principal", attributes = @SecurityAttribute(key = "tenant-id", value = "google"))
    @OidcSecurity(claims = {
            @Claim(key = "sub", value = "manager-oidc-subject"),
            @Claim(key = "email", value = "manager@example.test"),
            @Claim(key = "name", value = "Store Manager")
    })
    void managerPermissionsAreScopedToEachStore() {
        String personId = given().get("/auth/me").then().statusCode(200).extract().path("id");
        var storeA = stores.create("Manager store", "owner-a-" + UUID.randomUUID(), Instant.now());
        members.add(storeA.id(), personId, Role.MANAGER, Instant.now());
        var storeB = stores.create("Employee store", "owner-b-" + UUID.randomUUID(), Instant.now());
        members.add(storeB.id(), personId, Role.EMPLOYEE, Instant.now());
        String categoryB = categories.create(storeB.id(), "Shoes", Instant.now()).id();
        String productB = products.create(storeB.id(), categoryB, "Sneaker", "", 600, 6, Instant.now()).id();

        given().contentType("application/json").body("{\"name\":\"Accessories\"}")
                .post("/stores/{storeId}/categories", storeA.id()).then().statusCode(201);
        given().contentType("application/json").body("{\"name\":\"Not allowed\"}")
                .post("/stores/{storeId}/categories", storeB.id()).then().statusCode(403);
        given().get("/stores/{storeId}/products/{productId}", storeB.id(), productB).then().statusCode(200);
        given().contentType("application/json").body("{\"personId\":\"someone\",\"role\":\"EMPLOYEE\"}")
                .post("/stores/{storeId}/members", storeA.id()).then().statusCode(403);
        given().contentType("application/json").body("{\"name\":\"Changed across stores\"}")
                .patch("/stores/{storeId}/products/{productId}", storeA.id(), productB).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "employee-principal", attributes = @SecurityAttribute(key = "tenant-id", value = "google"))
    @OidcSecurity(claims = {
            @Claim(key = "sub", value = "employee-oidc-subject"),
            @Claim(key = "email", value = "employee@example.test"),
            @Claim(key = "name", value = "Store Employee")
    })
    void employeeCanReadProductsAndMoveStockButCannotManageCatalogOrMembers() {
        String personId = given().get("/auth/me").then().statusCode(200).extract().path("id");
        var store = stores.create("Employee fixture", "owner-" + UUID.randomUUID(), Instant.now());
        members.add(store.id(), personId, Role.EMPLOYEE, Instant.now());
        String categoryId = categories.create(store.id(), "Tools", Instant.now()).id();
        String productId = products.create(store.id(), categoryId, "Hammer", "", 1000, 4, Instant.now()).id();

        given().get("/stores/{storeId}/products/{productId}", store.id(), productId).then().statusCode(200);
        given().contentType("application/json").body("{\"type\":\"OUT\",\"quantity\":1}")
                .post(movementsPath(store.id(), productId)).then().statusCode(201).body("newStock", equalTo(3));
        given().contentType("application/json").body("{\"name\":\"Forbidden category\"}")
                .post("/stores/{storeId}/categories", store.id()).then().statusCode(403);
        given().contentType("application/json").body("{\"personId\":\"someone\",\"role\":\"EMPLOYEE\"}")
                .post("/stores/{storeId}/members", store.id()).then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "validation-principal", attributes = @SecurityAttribute(key = "tenant-id", value = "google"))
    @OidcSecurity(claims = {
            @Claim(key = "sub", value = "validation-oidc-subject"),
            @Claim(key = "email", value = "validation@example.test"),
            @Claim(key = "name", value = "Validation User")
    })
    void beanValidationRejectsInvalidInput() {
        given().contentType("application/json").body("{\"name\":\" \"}")
                .post("/stores").then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        given().contentType("application/json").body("{\"name\":\"Bad price\",\"categoryId\":\"missing\",\"priceInCents\":0,\"initialStock\":0}")
                .post("/stores/{storeId}/products", "not-a-real-store").then().statusCode(400);
        given().contentType("application/json").body("{\"type\":\"OUT\",\"quantity\":0}")
                .post("/stores/{storeId}/products/{productId}/inventory-movements", "no-store", "no-product").then().statusCode(400);
    }

    private static String createStore(String name) {
        return given().contentType("application/json").body("{\"name\":\"" + name + "\"}")
                .post("/stores").then().statusCode(201).body("role", equalTo("OWNER")).extract().path("id");
    }

    private static String createCategory(String storeId, String name) {
        return given().contentType("application/json").body("{\"name\":\"" + name + "\"}")
                .post("/stores/{id}/categories", storeId).then().statusCode(201).extract().path("id");
    }

    private static String createProduct(String storeId, String categoryId, String name, int stock) {
        String body = "{\"categoryId\":\"" + categoryId + "\",\"name\":\"" + name
                + "\",\"description\":\"Study item\",\"priceInCents\":1299,\"initialStock\":" + stock + "}";
        return given().contentType("application/json").body(body).post("/stores/{storeId}/products", storeId)
                .then().statusCode(201).extract().path("id");
    }

    private static String movementsPath(String storeId, String productId) {
        return "/stores/" + storeId + "/products/" + productId + "/inventory-movements";
    }
}
