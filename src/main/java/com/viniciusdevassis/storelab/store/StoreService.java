package com.viniciusdevassis.storelab.store;

import com.viniciusdevassis.storelab.api.ApiDtos.*;
import com.viniciusdevassis.storelab.auth.CurrentPersonService;
import com.viniciusdevassis.storelab.common.ApiException;
import com.viniciusdevassis.storelab.domain.Models.*;
import com.viniciusdevassis.storelab.security.StoreAuthorizationService;
import com.viniciusdevassis.storelab.person.PersonRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class StoreService {
    @Inject StoreRepository stores;
    @Inject StoreMemberRepository members;
    @Inject StoreAuthorizationService authorization;
    @Inject CurrentPersonService currentPerson;
    @Inject PersonRepository people;

    public StoreResponse create(CreateStoreRequest request) {
        Person person = currentPerson.current();
        Store store = stores.create(request.name(), person.id(), Instant.now());
        return response(store, Role.OWNER);
    }
    public List<StoreResponse> list() {
        Person person = currentPerson.current();
        return members.listForPerson(person.id()).stream().map(m -> response(stores.find(m.storeId()), m.role())).toList();
    }
    public StoreResponse get(String id) {
        authorization.requireMember(id);
        return response(stores.find(id), members.find(id, currentPerson.current().id()).role());
    }
    public StoreResponse update(String id, UpdateStoreRequest request) {
        authorization.requireOwner(id);
        if (request.name() == null && request.active() == null) throw ApiException.badRequest("Provide at least one store field to update");
        if (request.name() != null && request.name().isBlank()) throw ApiException.badRequest("name must not be blank");
        return response(stores.update(id, request.name(), request.active(), Instant.now()), Role.OWNER);
    }
    public List<MemberResponse> listMembers(String storeId) {
        authorization.requireOwner(storeId);
        return members.list(storeId).stream().map(StoreService::member).toList();
    }
    public MemberResponse addMember(String storeId, AddMemberRequest request) {
        authorization.requireOwner(storeId);
        if (!people.exists(request.personId())) throw ApiException.notFound("Person");
        return member(members.add(storeId, request.personId(), request.role(), Instant.now()));
    }
    public MemberResponse updateMember(String storeId, String personId, UpdateMemberRequest request) {
        authorization.requireOwner(storeId);
        return member(members.updateRole(storeId, personId, request.role()));
    }
    public void removeMember(String storeId, String personId) {
        authorization.requireOwner(storeId);
        members.remove(storeId, personId);
    }
    private static StoreResponse response(Store s, Role role) {
        return new StoreResponse(s.id(), s.name(), s.active(), s.createdBy(), s.createdAt(), s.updatedAt(), role);
    }
    private static MemberResponse member(StoreMember m) { return new MemberResponse(m.personId(), m.storeId(), m.role(), m.createdAt()); }
}
