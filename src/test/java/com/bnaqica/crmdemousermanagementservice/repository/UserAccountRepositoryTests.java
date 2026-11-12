package com.bnaqica.crmdemousermanagementservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bnaqica.crmdemousermanagementservice.TestcontainersConfiguration;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class UserAccountRepositoryTests {

    @Autowired
    private UserAccountRepository repository;

    private static UserAccount account(String email) {
        return UserAccount.builder()
                .sub(UUID.randomUUID())
                .userType(UserType.INTERNAL)
                .email(email)
                .firstName("Ana")
                .lastName("Test")
                .status(UserStatus.INVITED)
                .createdTicket("OPS-1")
                .createdAt(Instant.parse("2026-11-11T10:00:00Z"))
                .updatedAt(Instant.parse("2026-11-11T10:00:00Z"))
                .build();
    }

    @Test
    void savesAndFindsByEmailIgnoringCase() {
        repository.saveAndFlush(account("ana@corp.example"));

        assertThat(repository.findByEmailIgnoreCase("ANA@corp.example")).isPresent();
    }

    @Test
    void rejectsSameEmailInDifferentCase() {
        repository.saveAndFlush(account("pat@acme.example"));

        assertThatThrownBy(() -> repository.saveAndFlush(account("PAT@acme.example")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
