package com.merchant.merchant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.merchant.merchant.entity.Merchant;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class MerchantRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private MerchantRepository merchantRepository;

    private Merchant createMerchant(String name) {
        Merchant merchant = new Merchant();
        merchant.setUserId(1L);
        merchant.setName(name);
        return merchant;
    }

    @Test
    void save_persistsMerchantWithGeneratedIdAndTimestamps() {
        Merchant saved = merchantRepository.save(createMerchant("Merchant1"));

        assertThat(saved.getMerchantId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void save_prePersistGeneratesUniqueMerchantNoAndApiKey() {
        Merchant saved = merchantRepository.save(createMerchant("MerchantKeys"));

        assertThat(saved.getMerchantNo()).isNotBlank();
        assertThat(saved.getApiKey()).isNotBlank();

        Merchant reloaded = merchantRepository.findById(saved.getMerchantId()).orElseThrow();
        assertThat(reloaded.getMerchantNo()).isEqualTo(saved.getMerchantNo());
        assertThat(reloaded.getApiKey()).isEqualTo(saved.getApiKey());

        Merchant second = merchantRepository.save(createMerchant("MerchantKeys2"));
        assertThat(second.getMerchantNo()).isNotEqualTo(saved.getMerchantNo());
        assertThat(second.getApiKey()).isNotEqualTo(saved.getApiKey());
    }

    @Test
    void findById_returnsSavedMerchant() {
        Merchant saved = merchantRepository.save(createMerchant("Merchant2"));

        Optional<Merchant> found = merchantRepository.findById(saved.getMerchantId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Merchant2");
        assertThat(found.get().getUserId()).isEqualTo(1L);
    }

    @Test
    void findById_returnsEmptyWhenMissing() {
        assertThat(merchantRepository.findById(999999L)).isEmpty();
    }

    @Test
    void findAll_returnsAllPersisted() {
        merchantRepository.save(createMerchant("Merchant1"));
        merchantRepository.save(createMerchant("Merchant2"));

        List<Merchant> all = merchantRepository.findAll();

        assertThat(all).extracting(Merchant::getName)
                .contains("Merchant1", "Merchant2");
    }

    @Test
    void findByMerchantNo_returnsMatchingMerchant() {
        Merchant saved = merchantRepository.save(createMerchant("MerchantNo"));

        Optional<Merchant> found = merchantRepository.findByMerchantNo(saved.getMerchantNo());

        assertThat(found).isPresent();
        assertThat(found.get().getMerchantId()).isEqualTo(saved.getMerchantId());
    }

    @Test
    void findByMerchantNo_returnsEmptyWhenMissing() {
        assertThat(merchantRepository.findByMerchantNo("no-such-merchant-no")).isEmpty();
    }

    @Test
    void findByApiKey_returnsMatchingMerchant() {
        Merchant saved = merchantRepository.save(createMerchant("MerchantApi"));

        Optional<Merchant> found = merchantRepository.findByApiKey(saved.getApiKey());

        assertThat(found).isPresent();
        assertThat(found.get().getMerchantId()).isEqualTo(saved.getMerchantId());
    }

    @Test
    void findByApiKey_returnsEmptyWhenMissing() {
        assertThat(merchantRepository.findByApiKey("no-such-api-key")).isEmpty();
    }

    @Test
    void findByUserId_returnsOnlyThatUser() {
        Merchant mine = merchantRepository.save(createMerchant("Mine"));
        Merchant other = createMerchant("Other");
        other.setUserId(2L);
        merchantRepository.save(other);

        List<Merchant> result = merchantRepository.findByUserId(1L);

        assertThat(result).extracting(Merchant::getMerchantId).containsExactly(mine.getMerchantId());
    }

    @Test
    void findByUserId_returnsEmptyWhenNoMatch() {
        merchantRepository.save(createMerchant("Merchant1"));

        assertThat(merchantRepository.findByUserId(42L)).isEmpty();
    }

    @Test
    void update_touchesUpdatedAtViaPreUpdate() {
        Merchant saved = merchantRepository.save(createMerchant("Before"));
        LocalDateTime createdAtBefore = saved.getCreatedAt();

        saved.setName("After");
        Merchant updated = merchantRepository.saveAndFlush(saved);

        assertThat(updated.getName()).isEqualTo("After");
        assertThat(updated.getCreatedAt()).isEqualTo(createdAtBefore);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void deleteById_removesRow() {
        Merchant saved = merchantRepository.save(createMerchant("DeleteMe"));

        merchantRepository.deleteById(saved.getMerchantId());
        merchantRepository.flush();

        assertThat(merchantRepository.findById(saved.getMerchantId())).isEmpty();
    }
}
