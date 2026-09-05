package com.merchant.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.merchant.merchant.dto.MerchantMapper;
import com.merchant.merchant.dto.MerchantMapperImpl;
import com.merchant.merchant.dto.MerchantRequest;
import com.merchant.merchant.entity.Merchant;
import com.merchant.merchant.entity.MerchantDocument;
import com.merchant.merchant.repository.MerchantDocumentRepository;
import com.merchant.merchant.repository.MerchantRepository;

import io.opentelemetry.api.OpenTelemetry;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private MerchantDocumentRepository documentRepository;

    private MerchantService merchantService;

    private final MerchantMapper merchantMapper = new MerchantMapperImpl();

    @BeforeEach
    void setUp() {
        merchantService = new MerchantService(merchantRepository, documentRepository, merchantMapper,
                OpenTelemetry.noop());
    }

    private Merchant createMerchant(Long id, String name) {
        Merchant merchant = new Merchant();
        merchant.setMerchantId(id);
        merchant.setUserId(1L);
        merchant.setMerchantNo("MNO-" + id);
        merchant.setApiKey("KEY-" + id);
        merchant.setName(name);
        merchant.setDescription("desc " + name);
        merchant.setAddress("addr " + name);
        merchant.setContactEmail(name.toLowerCase() + "@mail.com");
        merchant.setContactPhone("0812" + id);
        return merchant;
    }

    private MerchantRequest createRequest(String name) {
        return new MerchantRequest(name, "new desc", "new addr", "new@mail.com", "0812000");
    }

    @Test
    void getAll_returnsAllFromRepository() {
        when(merchantRepository.findAll()).thenReturn(List.of(createMerchant(1L, "Merchant1"), createMerchant(2L, "Merchant2")));

        List<Merchant> result = merchantService.getAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Merchant::getName).containsExactly("Merchant1", "Merchant2");
        verify(merchantRepository).findAll();
    }

    @Test
    void getAll_returnsEmptyWhenNone() {
        when(merchantRepository.findAll()).thenReturn(List.of());

        assertThat(merchantService.getAll()).isEmpty();
    }

    @Test
    void getById_returnsMerchantWhenFound() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(createMerchant(1L, "Merchant1")));

        Merchant result = merchantService.getById(1L);

        assertThat(result.getMerchantId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Merchant1");
    }

    @Test
    void getById_throwsWhenNotFound() {
        when(merchantRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantService.getById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Merchant not found");
    }

    @Test
    void create_mapsRequestToEntityAndSaves() {
        MerchantRequest request = createRequest("NewMerchant");
        Merchant saved = createMerchant(5L, "NewMerchant");

        when(merchantRepository.save(any(Merchant.class))).thenReturn(saved);

        Merchant result = merchantService.create(request);

        assertThat(result.getMerchantId()).isEqualTo(5L);

        ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
        verify(merchantRepository).save(captor.capture());
        Merchant mapped = captor.getValue();
        assertThat(mapped.getName()).isEqualTo("NewMerchant");
        assertThat(mapped.getDescription()).isEqualTo("new desc");
        assertThat(mapped.getAddress()).isEqualTo("new addr");
        assertThat(mapped.getContactEmail()).isEqualTo("new@mail.com");
        assertThat(mapped.getContactPhone()).isEqualTo("0812000");
        assertThat(mapped.getStatus()).isEqualTo("PENDING");
        assertThat(mapped.getMerchantId()).isNull();
        assertThat(mapped.getMerchantNo()).isNull();
        assertThat(mapped.getApiKey()).isNull();
    }

    @Test
    void update_updatesFieldsOnExisting() {
        Merchant existing = createMerchant(1L, "OldName");
        MerchantRequest request = createRequest("UpdatedName");

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(inv -> inv.getArgument(0));

        Merchant result = merchantService.update(1L, request);

        assertThat(result.getName()).isEqualTo("UpdatedName");
        assertThat(result.getDescription()).isEqualTo("new desc");
        assertThat(result.getAddress()).isEqualTo("new addr");
        assertThat(result.getContactEmail()).isEqualTo("new@mail.com");
        assertThat(result.getContactPhone()).isEqualTo("0812000");
        verify(merchantRepository).save(existing);
    }

    @Test
    void update_throwsWhenNotFound() {
        when(merchantRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantService.update(999L, createRequest("X")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Merchant not found");

        verify(merchantRepository, never()).save(any(Merchant.class));
    }

    @Test
    void delete_delegatesToRepositoryHardDelete() {
        merchantService.delete(1L);

        verify(merchantRepository).deleteById(1L);
        verify(merchantRepository, never()).save(any(Merchant.class));
    }

    @Test
    void addDocument_setsFieldsAndSaves() {
        when(documentRepository.save(any(MerchantDocument.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MerchantDocument result = merchantService.addDocument(1L, "NIB", "https://docs.example.com/nib.pdf");

        assertThat(result.getMerchantId()).isEqualTo(1L);
        assertThat(result.getDocumentType()).isEqualTo("NIB");
        assertThat(result.getDocumentUrl()).isEqualTo("https://docs.example.com/nib.pdf");
        assertThat(result.getStatus()).isEqualTo("PENDING");

        ArgumentCaptor<MerchantDocument> captor = ArgumentCaptor.forClass(MerchantDocument.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getMerchantId()).isEqualTo(1L);
    }

    @Test
    void getDocuments_returnsFromRepository() {
        MerchantDocument doc = new MerchantDocument();
        doc.setDocumentId(7L);
        doc.setMerchantId(1L);
        doc.setDocumentType("NIB");
        doc.setDocumentUrl("https://docs.example.com/nib.pdf");
        when(documentRepository.findByMerchantId(1L)).thenReturn(List.of(doc));

        List<MerchantDocument> result = merchantService.getDocuments(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDocumentType()).isEqualTo("NIB");
        verify(documentRepository).findByMerchantId(1L);
    }

    @Test
    void getDocuments_returnsEmptyWhenNoMatch() {
        when(documentRepository.findByMerchantId(42L)).thenReturn(List.of());

        assertThat(merchantService.getDocuments(42L)).isEmpty();
    }
}
