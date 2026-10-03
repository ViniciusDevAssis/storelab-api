package com.viniciusdevassis.storelab.security;

import com.viniciusdevassis.storelab.auth.CurrentPersonService;
import com.viniciusdevassis.storelab.common.ApiException;
import com.viniciusdevassis.storelab.domain.Models.*;
import com.viniciusdevassis.storelab.store.StoreMemberRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class StoreAuthorizationService {
    @Inject CurrentPersonService currentPerson;
    @Inject StoreMemberRepository members;

    public Person requireMember(String storeId) {
        Person person = currentPerson.current();
        if (members.find(storeId, person.id()) == null) throw ApiException.forbidden();
        return person;
    }

    public Person requireOwner(String storeId) {
        Person person = currentPerson.current();
        StoreMember member = members.find(storeId, person.id());
        if (member == null || member.role() != Role.OWNER) throw ApiException.forbidden();
        return person;
    }

    public Person requireManagerOrOwner(String storeId) {
        Person person = currentPerson.current();
        StoreMember member = members.find(storeId, person.id());
        if (member == null || (member.role() != Role.OWNER && member.role() != Role.MANAGER)) throw ApiException.forbidden();
        return person;
    }

    public Person requireStockAccess(String storeId) { return requireMember(storeId); }
}
