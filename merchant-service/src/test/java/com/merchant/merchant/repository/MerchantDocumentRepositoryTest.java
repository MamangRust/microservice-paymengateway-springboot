package com.merchant.merchant.repository;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.merchant.merchant.entity.MerchantDocument;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class MerchantDocumentRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private MerchantDocumentRepository documentRepository;

    private MerchantDocument createDocument(Long merchantId, String type) {
        MerchantDocument document = new MerchantDocument();
        document.setMerchantId(merchantId);
        document.setDocumentType(type);
        document.setDocumentUrl("https://docs.example.com/" + type.toLowerCase() + ".pdf");
        return document;
    }

    @Test
    void save_persistsDocumentWithGeneratedIdAndTimestamps() {
        MerchantDocument saved = documentRepository.save(createDocument(1L, "NIB"));

        assertThat(saved.getDocumentId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void findById_returnsSavedDocument() {
        MerchantDocument saved = documentRepository.save(createDocument(1L, "KTP"));

        Optional<MerchantDocument> found = documentRepository.findById(saved.getDocumentId());

        assertThat(found).isPresent();
        assertThat(found.get().getDocumentType()).isEqualTo("KTP");
        assertThat(found.get().getMerchantId()).isEqualTo(1L);
    }

    @Test
    void findById_returnsEmptyWhenMissing() {
        assertThat(documentRepository.findById(999999L)).isEmpty();
    }

    @Test
    void findByMerchantId_returnsOnlyThatMerchant() {
        documentRepository.save(createDocument(1L, "NIB"));
        documentRepository.save(createDocument(1L, "KTP"));
        documentRepository.save(createDocument(2L, "SIUP"));

        List<MerchantDocument> result = documentRepository.findByMerchantId(1L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(MerchantDocument::getDocumentType)
                .containsExactlyInAnyOrder("NIB", "KTP");
    }

    @Test
    void findByMerchantId_returnsEmptyWhenNoMatch() {
        documentRepository.save(createDocument(1L, "NIB"));

        assertThat(documentRepository.findByMerchantId(42L)).isEmpty();
    }

    @Test
    void deleteById_removesRow() {
        MerchantDocument saved = documentRepository.save(createDocument(1L, "DeleteMe"));

        documentRepository.deleteById(saved.getDocumentId());
        documentRepository.flush();

        assertThat(documentRepository.findById(saved.getDocumentId())).isEmpty();
    }
}
