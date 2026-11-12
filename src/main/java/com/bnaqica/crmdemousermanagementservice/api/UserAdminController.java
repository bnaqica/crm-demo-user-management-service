package com.bnaqica.crmdemousermanagementservice.api;

import com.bnaqica.crmdemousermanagementservice.dto.UserSearchCriteria;
import com.bnaqica.crmdemousermanagementservice.dto.request.CreateInternalUserRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.InviteExternalUserRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.TicketRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.UpdateUserRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.UserPageResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.UserResponse;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.service.UserAccountService;
import com.bnaqica.crmdemousermanagementservice.service.UserOnboardingService;
import com.bnaqica.crmdemousermanagementservice.service.UserSupportService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin API for user accounts in crm-internal and portal. Needs a crm-admin token with the admin role. */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserOnboardingService onboarding;
    private final UserSupportService support;
    private final UserAccountService accounts;

    @GetMapping
    UserPageResponse search(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) UserType userType,
            @RequestParam(required = false) UserStatus status,
            @PageableDefault(size = 20, sort = "email") Pageable pageable) {
        return accounts.search(new UserSearchCriteria(email, name, userType, status), pageable);
    }

    @GetMapping("/{sub}")
    UserResponse get(@PathVariable UUID sub) {
        return accounts.getResponse(sub);
    }

    @PostMapping("/internal")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse createInternal(@Valid @RequestBody CreateInternalUserRequest request) {
        return onboarding.createInternal(request);
    }

    @PostMapping("/external")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse inviteExternal(@Valid @RequestBody InviteExternalUserRequest request) {
        return onboarding.inviteExternal(request);
    }

    @PutMapping("/{sub}")
    UserResponse update(@PathVariable UUID sub, @Valid @RequestBody UpdateUserRequest request) {
        return support.update(sub, request);
    }

    @PostMapping("/{sub}/disable")
    UserResponse disable(@PathVariable UUID sub, @Valid @RequestBody TicketRequest request) {
        return support.disable(sub, request.ticketRef());
    }

    @PostMapping("/{sub}/enable")
    UserResponse enable(@PathVariable UUID sub, @Valid @RequestBody TicketRequest request) {
        return support.enable(sub, request.ticketRef());
    }

    @PostMapping("/{sub}/unlock")
    void unlock(@PathVariable UUID sub, @Valid @RequestBody TicketRequest request) {
        support.unlock(sub, request.ticketRef());
    }

    @PostMapping("/{sub}/logout")
    void logout(@PathVariable UUID sub, @Valid @RequestBody TicketRequest request) {
        support.logout(sub, request.ticketRef());
    }

    @PostMapping("/{sub}/reset-mfa")
    void resetMfa(@PathVariable UUID sub, @Valid @RequestBody TicketRequest request) {
        support.resetMfa(sub, request.ticketRef());
    }
}
